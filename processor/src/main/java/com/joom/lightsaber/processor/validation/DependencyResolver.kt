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
import com.joom.lightsaber.processor.model.Converter
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.FactoryInjectee
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.signature.GenericType

interface DependencyResolver {
  fun getProvidedDependencies(): Set<Dependency>
  fun getRequiredDependencies(): Set<Dependency>

  fun getResolvedDependencies(): Set<Dependency> {
    return getProvidedDependencies()
  }

  fun getUnresolvedDependencies(): Set<Dependency> {
    return getRequiredDependencies() - getProvidedDependencies()
  }

  fun isResolved(dependency: Dependency): Boolean {
    return dependency.boxed() in getResolvedDependencies()
  }

  fun getDependencyGraph(): DirectedGraph<Dependency>
}

interface MutableDependencyResolver : DependencyResolver {
  fun add(dependencyResolver: DependencyResolver)
  fun add(component: Component)
}

class DependencyResolverImpl(
  private val context: InjectionContext
) : MutableDependencyResolver {

  private val providedDependencies = hashSetOf<Dependency>()
  private val requiredDependencies = hashSetOf<Dependency>()

  private val dependencyGraph = HashDirectedGraph<Dependency>()

  init {
    val injectorDependency = Dependency(GenericType.Raw(Types.INJECTOR_TYPE))
    providedDependencies += injectorDependency
    dependencyGraph.put(injectorDependency)
  }

  override fun add(dependencyResolver: DependencyResolver) {
    providedDependencies += dependencyResolver.getProvidedDependencies()
    requiredDependencies += dependencyResolver.getRequiredDependencies()
    dependencyGraph.putAll(dependencyResolver.getDependencyGraph())
  }

  override fun add(component: Component) {
    add(component.defaultModule)
  }

  override fun getProvidedDependencies(): Set<Dependency> {
    return providedDependencies
  }

  override fun getRequiredDependencies(): Set<Dependency> {
    return requiredDependencies
  }

  override fun getDependencyGraph(): DirectedGraph<Dependency> {
    return dependencyGraph
  }

  private fun add(module: Module) {
    for (provisionPoint in module.provisionPoints) {
      add(provisionPoint)
    }

    for (binding in module.bindings) {
      add(binding)
    }

    for (factory in module.factories) {
      add(factory)
    }

    for (contract in module.contracts) {
      add(contract, isProvided = false)
    }

    for (import in module.imports) {
      add(import)
    }
  }

  private fun add(provisionPoint: ProvisionPoint) {
    addProvidedDependency(provisionPoint.dependency, includeInjectableTargetDependencies = true)
    for (injectee in provisionPoint.getInjectees()) {
      addRequiredDependency(injectee.dependency)
      if (injectee.converter == Converter.Instance) {
        dependencyGraph.put(provisionPoint.dependency, injectee.dependency)
      }
    }
  }

  private fun add(binding: Binding) {
    addProvidedDependency(binding.ancestor)
    addRequiredDependency(binding.dependency)
    dependencyGraph.put(binding.ancestor, binding.dependency)
  }

  private fun add(factory: Factory) {
    addProvidedDependency(factory.dependency)
    for (provisionPoint in factory.provisionPoints) {
      for (injectee in provisionPoint.injectionPoint.injectees) {
        if (injectee is FactoryInjectee.FromInjector) {
          addRequiredDependency(injectee.dependency)
        }
      }
    }
  }

  private fun add(contract: Contract, isProvided: Boolean) {
    addProvidedDependency(contract.dependency)
    for (contractProvisionPoint in contract.provisionPoints) {
      val dependency = contractProvisionPoint.injectee.dependency
      if (isProvided) {
        addProvidedDependency(dependency)
      } else {
        addRequiredDependency(dependency)
      }
    }
  }

  private fun add(import: Import) {
    return when (import) {
      is Import.Module -> add(import.module)
      is Import.Contract -> add(import.contract, isProvided = true)
    }
  }

  private fun addProvidedDependency(dependency: Dependency, includeInjectableTargetDependencies: Boolean = false) {
    providedDependencies += dependency.boxed()
    if (includeInjectableTargetDependencies) {
      val dependencyType = dependency.type.rawType
      if (dependencyType is Type.Object) {
        context.findInjectableTargetByType(dependencyType)?.also { injectableTarget ->
          for (injectionPoint in injectableTarget.injectionPoints) {
            for (injectee in injectionPoint.getInjectees()) {
              addRequiredDependency(injectee.dependency)
            }
          }
        }
      }
    }
  }

  private fun addRequiredDependency(dependency: Dependency) {
    requiredDependencies += dependency.boxed()
  }
}
