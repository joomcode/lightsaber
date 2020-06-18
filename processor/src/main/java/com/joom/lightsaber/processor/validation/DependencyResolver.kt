/*
 * Copyright 2020 SIA Joom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.joom.lightsaber.processor.validation

import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.boxed
import com.joom.lightsaber.processor.commons.getInjectees
import com.joom.lightsaber.processor.commons.rawType
import com.joom.lightsaber.processor.graph.DirectedGraph
import com.joom.lightsaber.processor.graph.HashDirectedGraph
import com.joom.lightsaber.processor.graph.putAll
import com.joom.lightsaber.processor.model.Binding
import com.joom.lightsaber.processor.model.Component
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ContractProvisionPoint
import com.joom.lightsaber.processor.model.Converter
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.FactoryInjectee
import com.joom.lightsaber.processor.model.FactoryInjectionPoint
import com.joom.lightsaber.processor.model.FactoryProvisionPoint
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.InjectionTarget
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.signature.GenericType

interface DependencyResolver {
  fun getImportsWithPaths(): Map<Type.Object, Collection<DependencyResolverPath>>

  fun getProvidedDependencies(): Map<Dependency, Collection<DependencyResolverPath>>
  fun getRequiredDependencies(): Map<Dependency, Collection<DependencyResolverPath>>

  fun getDependencyGraph(): DirectedGraph<Dependency>

  fun getResolvedDependencies(): Map<Dependency, Collection<DependencyResolverPath>> {
    return getProvidedDependencies()
  }

  fun getUnresolvedDependencies(): Map<Dependency, Collection<DependencyResolverPath>> {
    return getRequiredDependencies() - getProvidedDependencies().keys
  }

  fun isResolved(dependency: Dependency): Boolean {
    return dependency.boxed() in getResolvedDependencies()
  }
}

interface MutableDependencyResolver : DependencyResolver {
  fun add(dependencyResolver: DependencyResolver)
  fun add(component: Component)
}

class DependencyResolverImpl(
  private val context: InjectionContext
) : MutableDependencyResolver {

  private val imports = mutableMapOf<Type.Object, MutableCollection<DependencyResolverPath>>()
  private val providedDependencies = mutableMapOf<Dependency, MutableCollection<DependencyResolverPath>>()
  private val requiredDependencies = mutableMapOf<Dependency, MutableCollection<DependencyResolverPath>>()

  private val dependencyGraph = HashDirectedGraph<Dependency>()

  override fun add(dependencyResolver: DependencyResolver) {
    imports.addAllWithPaths(dependencyResolver.getImportsWithPaths())
    providedDependencies.addAllWithPaths(dependencyResolver.getProvidedDependencies())
    requiredDependencies.addAllWithPaths(dependencyResolver.getRequiredDependencies())
    dependencyGraph.putAll(dependencyResolver.getDependencyGraph())

    dependencyResolver.getProvidedDependencies()[INJECTOR_DEPENDENCY]?.let {
      providedDependencies[INJECTOR_DEPENDENCY] = it.toMutableList()
    }
  }

  override fun add(component: Component) {
    val path = DependencyResolverPath.from(component)

    providedDependencies[INJECTOR_DEPENDENCY] = mutableListOf(path)
    dependencyGraph.put(INJECTOR_DEPENDENCY)

    add(component.defaultModule, path)
  }

  override fun getImportsWithPaths(): Map<Type.Object, Collection<DependencyResolverPath>> {
    return imports
  }

  override fun getProvidedDependencies(): Map<Dependency, Collection<DependencyResolverPath>> {
    return providedDependencies
  }

  override fun getRequiredDependencies(): Map<Dependency, Collection<DependencyResolverPath>> {
    return requiredDependencies
  }

  override fun getDependencyGraph(): DirectedGraph<Dependency> {
    return dependencyGraph
  }

  private fun add(module: Module, path: DependencyResolverPath) {
    for (provisionPoint in module.provisionPoints) {
      add(provisionPoint, path.with(provisionPoint))
    }

    for (binding in module.bindings) {
      add(binding, path.with(binding))
    }

    for (factory in module.factories) {
      add(factory, path.with(factory))
    }

    for (contract in module.contracts) {
      add(contract, path.with(contract), isImported = false)
    }

    for (import in module.imports) {
      add(import, path.with(import.importPoint))
    }
  }

  private fun add(provisionPoint: ProvisionPoint, path: DependencyResolverPath) {
    addProvidedDependency(provisionPoint.dependency, path, includeInjectableTargetDependencies = true)
    for (injectee in provisionPoint.getInjectees()) {
      addRequiredDependency(injectee.dependency, path)
      if (injectee.converter == Converter.Instance) {
        dependencyGraph.put(provisionPoint.dependency, injectee.dependency)
      }
    }
  }

  private fun add(injectionTarget: InjectionTarget, path: DependencyResolverPath) {
    for (injectionPoint in injectionTarget.injectionPoints) {
      add(injectionPoint, path.with(injectionPoint))
    }
  }

  private fun add(injectionPoint: InjectionPoint, path: DependencyResolverPath) {
    for (injectee in injectionPoint.getInjectees()) {
      addRequiredDependency(injectee.dependency, path)
    }
  }

  private fun add(binding: Binding, path: DependencyResolverPath) {
    addProvidedDependency(binding.ancestor, path)
    addRequiredDependency(binding.dependency, path)
    dependencyGraph.put(binding.ancestor, binding.dependency)
  }

  private fun add(factory: Factory, path: DependencyResolverPath) {
    addProvidedDependency(factory.dependency, path)
    for (factoryProvisionPoint in factory.provisionPoints) {
      add(factoryProvisionPoint, path.with(factoryProvisionPoint))
    }
  }

  private fun add(factoryProvisionPoint: FactoryProvisionPoint, path: DependencyResolverPath) {
    val injectionPath = factoryProvisionPoint.injectionPoint
    add(injectionPath, path.with(injectionPath))
    for (injectee in factoryProvisionPoint.injectionPoint.injectees) {
      if (injectee is FactoryInjectee.FromInjector) {
        addRequiredDependency(injectee.dependency, path)
      }
    }
  }

  private fun add(factoryInjectionPoint: FactoryInjectionPoint, path: DependencyResolverPath) {
    for (injectee in factoryInjectionPoint.injectees) {
      if (injectee is FactoryInjectee.FromInjector) {
        addRequiredDependency(injectee.dependency, path)
      }
    }
  }

  private fun add(contract: Contract, path: DependencyResolverPath, isImported: Boolean) {
    if (!isImported) {
      addProvidedDependency(contract.dependency, path)
    }

    for (contractProvisionPoint in contract.provisionPoints) {
      add(contractProvisionPoint, path.with(contractProvisionPoint), isImported)
    }
  }

  private fun add(contractProvisionPoint: ContractProvisionPoint, path: DependencyResolverPath, isImported: Boolean) {
    val dependency = contractProvisionPoint.injectee.dependency
    if (isImported) {
      addProvidedDependency(dependency, path)
    } else {
      addRequiredDependency(dependency, path)
    }
  }

  private fun add(import: Import, path: DependencyResolverPath) {
    addImport(import, path)

    return when (import) {
      is Import.Module -> add(import.module, path.with(import.module))
      is Import.Contract -> add(import.contract, path.with(import.contract), isImported = true)
    }
  }

  private fun addImport(import: Import, path: DependencyResolverPath) {
    val importType = when (import) {
      is Import.Module -> import.module.type
      is Import.Contract -> import.contract.type
    }

    addImport(importType, path)
  }

  private fun addImport(type: Type.Object, path: DependencyResolverPath) {
    imports.getOrPut(type, ::ArrayList).add(path)
  }

  private fun addProvidedDependency(dependency: Dependency, path: DependencyResolverPath, includeInjectableTargetDependencies: Boolean = false) {
    providedDependencies.addDependency(dependency, path)
    if (includeInjectableTargetDependencies) {
      val dependencyType = dependency.type.rawType
      if (dependencyType is Type.Object) {
        context.findInjectableTargetByType(dependencyType)?.also { injectableTarget ->
          add(injectableTarget, path.with(injectableTarget))
        }
      }
    }
  }

  private fun addRequiredDependency(dependency: Dependency, path: DependencyResolverPath) {
    requiredDependencies.addDependency(dependency, path)
  }

  private fun MutableMap<Dependency, MutableCollection<DependencyResolverPath>>.addDependency(
    dependency: Dependency,
    path: DependencyResolverPath
  ) {
    getOrPut(dependency.boxed(), ::ArrayList).add(path)
  }

  private fun <T> MutableMap<T, MutableCollection<DependencyResolverPath>>.addAllWithPaths(
    map: Map<T, Collection<DependencyResolverPath>>
  ) {
    map.forEach { (key, paths) ->
      getOrPut(key, ::ArrayList).addAll(paths)
    }
  }

  companion object {
    private val INJECTOR_DEPENDENCY = Dependency(GenericType.Raw(Types.INJECTOR_TYPE))
  }
}
