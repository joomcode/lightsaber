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

import com.joom.lightsaber.processor.graph.DirectedGraph
import com.joom.lightsaber.processor.graph.HashDirectedGraph
import com.joom.lightsaber.processor.model.Component
import io.michaelrocks.grip.mirrors.Type

fun buildComponentGraph(components: Collection<Component>): DirectedGraph<Type.Object> {
  return HashDirectedGraph<Type.Object>().apply {
    components.forEach { put(it.type, it.subcomponents) }
  }
}
