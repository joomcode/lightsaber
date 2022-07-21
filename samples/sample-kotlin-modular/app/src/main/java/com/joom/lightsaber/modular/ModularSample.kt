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

import com.joom.lightsaber.Lightsaber
import javax.inject.Inject

class ModularSample {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      ModularSample().run()
    }
  }

  internal fun run() {
    val lightsaber = Lightsaber.Builder().build()
    val libraryContract = lightsaber.createContract(LibraryContractConfiguration())
    val contract = lightsaber.createContract(ModularContractConfiguration(libraryContract))
    val injector = lightsaber.createInjector(LibraryComponent())
    val injectee = ComponentInjectee()
    injector.injectMembers(injectee)

    contract.moduleDependency.printInfo()
    contract.qualifiedModuleDependency.printInfo()
    contract.qualifiedContractDependency.printInfo()
    contract.typedDependency.printInfo()
    contract.factoryDependency.printInfo()
    injectee.componentDependency.printInfo()
  }


  private class ComponentInjectee {
    @Inject
    lateinit var componentDependency: ComponentDependency
  }
}
