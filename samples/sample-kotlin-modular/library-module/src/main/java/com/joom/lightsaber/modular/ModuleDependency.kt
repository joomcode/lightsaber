/*
 * Copyright 2022 SIA Joom
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

package com.joom.lightsaber.modular

import com.joom.lightsaber.ProvidedAs
import com.joom.lightsaber.ProvidedBy
import javax.inject.Inject
import javax.inject.Qualifier

interface ModuleDependency {
  fun printInfo()
}

interface TypedModuleDependency<T> : ModuleDependency

@Qualifier
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ModuleQualifier

@ProvidedBy(LibraryModule::class)
@ProvidedAs(ModuleDependency::class)
internal class ModuleDependencyImpl @Inject constructor() : ModuleDependency {
  override fun printInfo() {
    println("ModuleDependencyImpl")
  }
}
