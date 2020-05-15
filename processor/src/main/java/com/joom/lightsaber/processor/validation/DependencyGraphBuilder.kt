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
import com.joom.lightsaber.processor.model.Component
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.Module
import io.michaelrocks.grip.mirrors.signature.GenericType

class DependencyGraphBuilder(
  private val context: InjectionContext,
  private val includeDependenciesOnlyWithInstanceConverter: Boolean = false
) {

  private val graph = HashDirectedGraph<Dependency>()

  init {
    val rootType = Dependency(GenericType.Raw(Types.INJECTOR_TYPE))
    graph.put(rootType, emptyList())
  }

  fun add(module: Module): DependencyGraphBuilder = apply {
    for (provider in module.providers) {
      val returnType = provider.dependency.boxed()
      graph.put(returnType, provider.getDependencies(context, includeDependenciesOnlyWithInstanceConverter))
    }

    add(module.modules)
  }

  fun add(modules: Iterable<Module>): DependencyGraphBuilder = apply {
    modules.forEach { add(it) }
  }

  fun add(component: Component): DependencyGraphBuilder = apply {
    add(component.defaultModule)
  }

  fun build(): DirectedGraph<Dependency> {
    return HashDirectedGraph(graph)
  }
}
