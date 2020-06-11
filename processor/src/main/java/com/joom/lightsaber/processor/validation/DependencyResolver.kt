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
import com.joom.lightsaber.processor.commons.getDependencies
import com.joom.lightsaber.processor.model.Binding
import com.joom.lightsaber.processor.model.Component
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.FactoryInjectee
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint
import io.michaelrocks.grip.mirrors.signature.GenericType

class DependencyResolver(
  private val context: InjectionContext
) {

  private val providedDependencies = HashSet<Dependency>()
  private val requiredDependencies = HashSet<Dependency>()

  init {
    providedDependencies += Dependency(GenericType.Raw(Types.INJECTOR_TYPE))
  }

  fun add(module: Module): DependencyResolver = apply {
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
      add(contract)
    }

    add(module.modules)
  }

  fun add(modules: Iterable<Module>): DependencyResolver = apply {
    modules.forEach { add(it) }
  }

  fun add(component: Component, includeAncestors: Boolean): DependencyResolver = apply {
    if (includeAncestors && component.parent != null) {
      val parentComponent = checkNotNull(context.findComponentByType(component.parent)) { "Component ${component.parent.className} not found" }
      add(parentComponent, true)
    }

    add(component.defaultModule)
  }

  fun isResolved(dependency: Dependency): Boolean {
    return dependency.boxed() in providedDependencies
  }

  fun getResolvedDependencies(): Set<Dependency> {
    return providedDependencies.toSet()
  }

  fun getUnresolvedDependencies(): Set<Dependency> {
    return requiredDependencies.toHashSet().apply {
      removeAll(providedDependencies)
    }
  }

  fun resolveAllDependencies() {
    requiredDependencies.clear()
  }

  fun getUnresolvedDependenciesAndResolveAllDependencies(): Set<Dependency> {
    return getUnresolvedDependencies().also {
      resolveAllDependencies()
    }
  }

  private fun add(provisionPoint: ProvisionPoint) {
    providedDependencies += provisionPoint.dependency.boxed()
    requiredDependencies += provisionPoint.getDependencies(context)
  }

  private fun add(binding: Binding) {
    providedDependencies += binding.ancestor
    requiredDependencies += binding.dependency
  }

  private fun add(factory: Factory) {
    providedDependencies += factory.dependency
    for (provisionPoint in factory.provisionPoints) {
      for (injectee in provisionPoint.injectionPoint.injectees) {
        if (injectee is FactoryInjectee.FromInjector) {
          requiredDependencies += injectee.dependency.boxed()
        }
      }
    }
  }

  private fun add(contract: Contract) {
    providedDependencies += contract.dependency
    for (provisionPoint in contract.provisionPoints) {
      requiredDependencies += provisionPoint.injectee.dependency.boxed()
    }
  }
}
