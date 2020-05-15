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

package com.joom.lightsaber.processor.commons

import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.Provider
import io.michaelrocks.grip.mirrors.Type

fun Provider.getDependencies(context: InjectionContext, onlyWithInstanceConverter: Boolean = false): Collection<Dependency> {
  return getProvidableTargetDependencies(onlyWithInstanceConverter) + getInjectableTargetDependencies(context, onlyWithInstanceConverter)
}

private fun Provider.getProvidableTargetDependencies(onlyWithInstanceConverter: Boolean): Collection<Dependency> {
  return provisionPoint.getInjectees().getDependencies(onlyWithInstanceConverter)
}

private fun Provider.getInjectableTargetDependencies(context: InjectionContext, onlyWithInstanceConverter: Boolean): Collection<Dependency> {
  val dependencyType = dependency.type.rawType
  if (dependencyType is Type.Object) {
    context.findInjectableTargetByType(dependencyType)?.also { injectableTarget ->
      return injectableTarget.injectionPoints.flatMap { it.getInjectees().getDependencies(onlyWithInstanceConverter) }
    }
  }

  return emptyList()
}
