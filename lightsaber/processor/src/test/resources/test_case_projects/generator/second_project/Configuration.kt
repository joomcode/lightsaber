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

package test_case_projects.generator.second_project

import com.joom.lightsaber.Contract
import com.joom.lightsaber.ContractConfiguration
import com.joom.lightsaber.Import
import com.joom.lightsaber.Provide
import com.joom.lightsaber.ProvidedAs
import com.joom.lightsaber.ProvidedBy
import test_case_projects.generator.first_project.FirstContractDependency
import test_case_projects.generator.first_project.FirstDependencyContract
import test_case_projects.generator.first_project.FirstDependencyModule
import test_case_projects.generator.first_project.FirstDependencyQualifier
import test_case_projects.generator.first_project.FirstFactoryCreatedDependency
import test_case_projects.generator.first_project.FirstFactoryCreatedDependencyFactory
import test_case_projects.generator.first_project.FirstFactoryCreatedDependencyFactoryModule
import test_case_projects.generator.first_project.FirstModuleDependency
import javax.inject.Inject

interface SecondDependency

@ProvidedBy(SecondDependencyContractConfiguration::class)
@ProvidedAs(SecondDependency::class)
internal class SecondDependencyImpl @Inject constructor() : SecondDependency

@Contract
interface SecondDependencyContract {
  val dependency: SecondDependency

  @get:FirstDependencyQualifier
  val firstContractDependency: FirstContractDependency

  val firstModuleDependency: FirstModuleDependency

  val firstFactoryCreatedDependency: FirstFactoryCreatedDependency
}

class SecondDependencyContractConfiguration(
  @Import @Contract private val firstDependencyContract: FirstDependencyContract
) : ContractConfiguration<SecondDependencyContract>() {

  @Import
  fun importFirstDependencyModule(): FirstDependencyModule {
    return FirstDependencyModule()
  }

  @Import
  fun importFirstFactoryCreatedDependencyFactoryModule(): FirstFactoryCreatedDependencyFactoryModule {
    return FirstFactoryCreatedDependencyFactoryModule()
  }

  @Provide
  fun provideFirstFactoryCreatedDependency(factory: FirstFactoryCreatedDependencyFactory): FirstFactoryCreatedDependency {
    return factory.create()
  }
}
