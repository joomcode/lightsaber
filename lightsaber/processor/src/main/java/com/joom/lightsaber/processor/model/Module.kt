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

package com.joom.lightsaber.processor.model

import com.joom.grip.mirrors.Type

data class Module(
  val type: Type.Object,
  val imports: Collection<Import>,
  val provisionPoints: Collection<ProvisionPoint>,
  val bindings: Collection<Binding>,
  val factories: Collection<Factory>,
  val contracts: Collection<Contract>
) {

  val modules: Collection<Module> = imports.mapNotNull { (it as? Import.Module)?.module }

  fun getModulesWithDescendants(): Sequence<Module> = sequence {
    yieldModulesWithDescendants(listOf(this@Module))
  }

  fun getImportsWithDescendants(): Sequence<Import> {
    return getModulesWithDescendants().flatMap { it.imports.asSequence() }
  }

  private suspend fun SequenceScope<Module>.yieldModulesWithDescendants(modules: Iterable<Module>) {
    modules.forEach { module ->
      yield(module)
      yieldModulesWithDescendants(module.modules)
    }
  }
}
