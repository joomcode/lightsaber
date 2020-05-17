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

import com.joom.lightsaber.processor.model.Converter
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Injectee

fun Injectee.boxed(): Injectee {
  val boxedDependency = dependency.boxed()
  return if (boxedDependency === dependency) this else copy(dependency = boxedDependency)
}

fun Iterable<Injectee>.onlyWithInstanceConverter(): List<Injectee> {
  return filter { it.converter === Converter.Instance }
}

fun Iterable<Injectee>.getDependencies(): List<Dependency> {
  return map { it.dependency.boxed() }
}

fun Iterable<Injectee>.getDependenciesWithInstanceConverter(): List<Dependency> {
  return onlyWithInstanceConverter().getDependencies()
}

fun Iterable<Injectee>.getDependencies(onlyWithInstanceConverter: Boolean): List<Dependency> {
  return if (onlyWithInstanceConverter) getDependenciesWithInstanceConverter() else getDependencies()
}