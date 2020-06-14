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

import com.joom.lightsaber.processor.model.Component
import com.joom.lightsaber.processor.model.InjectionContext
import io.michaelrocks.grip.mirrors.Type

class DependencyResolverFactory(
  private val injectionContext: InjectionContext
) {

  private val dependencyResolversByComponentType = mutableMapOf<Type.Object, DependencyResolver>()

  fun createEmpty(): MutableDependencyResolver {
    return DependencyResolverImpl(injectionContext)
  }

  fun getOrCreate(component: Component): DependencyResolver {
    return dependencyResolversByComponentType.getOrPut(component.type) {
      createEmpty().also { resolver ->
        if (component.parent != null) {
          resolver.addComponentByType(component.parent)
        }
        resolver.add(component)
      }
    }
  }

  fun getOrCreateMutable(component: Component): MutableDependencyResolver {
    return createEmpty().also { resolver ->
      resolver.add(getOrCreate(component))
    }
  }

  private fun MutableDependencyResolver.addComponentByType(type: Type.Object) {
    val component = checkNotNull(injectionContext.findComponentByType(type)) { "Component ${type.className} not found" }
    add(getOrCreate(component))
  }
}
