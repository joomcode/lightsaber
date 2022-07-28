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

import com.joom.lightsaber.Contract
import com.joom.lightsaber.ContractConfiguration
import com.joom.lightsaber.Import
import com.joom.lightsaber.Provide

interface ModularContract {
  val moduleDependency: ModuleDependency

  @get:ModuleQualifier
  val qualifiedModuleDependency: ModuleDependency

  @get:ContractQualifier
  val qualifiedContractDependency: ModuleDependency
  val typedDependency: TypedModuleDependency<String>
  val factoryDependency: FactoryDependency
}

class ModularContractConfiguration(
  @Import @Contract private val libraryContract: LibraryContract,
) : ContractConfiguration<ModularContract>() {
  @Import
  fun importModule(): LibraryModule {
    return LibraryModule()
  }

  @Import
  fun importFactoryModule(): LibraryFactoryModule {
    return LibraryFactoryModule()
  }

  @Provide
  fun provideFactoryDependency(factory: LibraryFactory): FactoryDependency {
    return factory.create()
  }
}
