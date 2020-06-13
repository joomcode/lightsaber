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
import com.joom.lightsaber.processor.graph.DirectedGraph
import com.joom.lightsaber.processor.graph.HashDirectedGraph
import com.joom.lightsaber.processor.model.Binding
import com.joom.lightsaber.processor.model.Component
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.FactoryInjectee
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint
import io.michaelrocks.grip.mirrors.signature.GenericType

class DependencyGraphBuilder(
  private val context: InjectionContext,
  private val includeDependenciesOnlyWithInstanceConverter: Boolean = false
) {

  private val graph = HashDirectedGraph<Dependency>()

  init {
    val rootType = Dependency(GenericType.Raw(Types.INJECTOR_TYPE))
    graph.put(rootType)
  }

  fun add(component: Component): DependencyGraphBuilder = apply {
    add(component.defaultModule)
  }

  private fun add(import: Import) {
    return when (import) {
      is Import.Module -> add(import.module)
      is Import.Contract -> add(import.contract, isImported = true)
    }
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
      add(contract, isImported = false)
    }

    for (import in module.imports) {
      add(import)
    }
  }

  private fun add(provisionPoint: ProvisionPoint) {
    val returnType = provisionPoint.dependency.boxed()
    graph.put(returnType, provisionPoint.getDependencies(context, includeDependenciesOnlyWithInstanceConverter))
  }

  private fun add(binding: Binding) {
    graph.put(binding.ancestor, binding.dependency)
  }

  private fun add(factory: Factory) {
    if (includeDependenciesOnlyWithInstanceConverter) {
      return
    }

    for (provisionPoint in factory.provisionPoints) {
      for (injectee in provisionPoint.injectionPoint.injectees) {
        if (injectee is FactoryInjectee.FromInjector) {
          graph.put(factory.dependency, injectee.dependency)
        }
      }
    }
  }

  private fun add(contract: Contract, isImported: Boolean) {
    if (!includeDependenciesOnlyWithInstanceConverter) {
      return
    }

    if (isImported) {
      graph.put(contract.dependency)
      for (provisionPoint in contract.provisionPoints) {
        graph.put(provisionPoint.injectee.dependency)
      }
    } else {
      for (provisionPoint in contract.provisionPoints) {
        graph.put(contract.dependency, provisionPoint.injectee.dependency)
      }
    }
  }

  fun build(): DirectedGraph<Dependency> {
    return HashDirectedGraph(graph)
  }
}
