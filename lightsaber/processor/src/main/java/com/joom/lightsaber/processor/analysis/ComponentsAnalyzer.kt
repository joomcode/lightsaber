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

package com.joom.lightsaber.processor.analysis

import com.joom.grip.Grip
import com.joom.grip.annotatedWith
import com.joom.grip.classes
import com.joom.grip.mirrors.Type
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.graph.DirectedGraph
import com.joom.lightsaber.processor.graph.HashDirectedGraph
import com.joom.lightsaber.processor.graph.reversed
import com.joom.lightsaber.processor.model.Component
import java.nio.file.Path

interface ComponentsAnalyzer {
  fun analyze(paths: Collection<Path>): Collection<Component>
}

class ComponentsAnalyzerImpl(
  private val grip: Grip,
  private val moduleParser: ModuleParser,
  private val errorReporter: ErrorReporter
) : ComponentsAnalyzer {

  override fun analyze(paths: Collection<Path>): Collection<Component> {
    val componentsQuery = grip select classes from paths where annotatedWith(Types.COMPONENT_TYPE)
    val graph = buildComponentGraph(componentsQuery.execute().types)
    val reversedGraph = graph.reversed()
    return graph.vertices
      .filterNot { it == Types.COMPONENT_NONE_TYPE }
      .map { type ->
        val parent = reversedGraph.getAdjacentVertices(type)?.first()?.takeIf { it != Types.COMPONENT_NONE_TYPE }
        val defaultModule = moduleParser.parseModule(type, isImported = false)
        val subcomponents = graph.getAdjacentVertices(type).orEmpty().toList()
        Component(type, parent, defaultModule, subcomponents)
      }
  }

  private fun buildComponentGraph(types: Collection<Type.Object>): DirectedGraph<Type.Object> {
    val graph = HashDirectedGraph<Type.Object>()

    for (type in types) {
      val mirror = grip.classRegistry.getClassMirror(type)
      if (mirror.signature.typeVariables.isNotEmpty()) {
        errorReporter.reportError("Component cannot have a type parameters: ${type.className}")
        continue
      }

      val annotation = mirror.annotations[Types.COMPONENT_TYPE]
      if (annotation == null) {
        errorReporter.reportError("Class ${type.className} is not a component")
        continue
      }

      val parent = annotation.values[com.joom.lightsaber.Component::parent.name] as Type?
      if (parent != null && parent != Types.COMPONENT_NONE_TYPE) {
        if (parent is Type.Object) {
          graph.put(parent, type)
        } else {
          errorReporter.reportError("Parent component of ${type.className} is not a class")
        }
      } else {
        graph.put(Types.COMPONENT_NONE_TYPE, type)
      }
    }

    return graph
  }
}
