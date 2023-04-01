/*
 * Copyright 2023 SIA Joom
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

package test_case_projects.unused_imports.provider_usages

import com.joom.lightsaber.Contract
import com.joom.lightsaber.ContractConfiguration
import com.joom.lightsaber.Import
import com.joom.lightsaber.ProvidedAs
import com.joom.lightsaber.ProvidedBy
import javax.inject.Inject
import javax.inject.Provider

interface MainContract {
  val mainDependency: MainDependency
}

interface SecondaryContract {
  val secondaryDependency: SecondaryDependency
}

interface MainDependency
interface SecondaryDependency

@ProvidedAs(SecondaryDependency::class)
@ProvidedBy(SecondaryContractConfiguration::class)
class SecondaryDependencyImpl @Inject constructor() : SecondaryDependency

@ProvidedAs(MainDependency::class)
@ProvidedBy(MainContractConfiguration::class)
class MainDependencyImpl @Inject constructor(
  val secondaryDependency: Provider<SecondaryDependency>,
) : MainDependency

class SecondaryContractConfiguration : ContractConfiguration<SecondaryContract>()

class MainContractConfiguration(
  @Import @Contract val secondaryContract: SecondaryContract,
) : ContractConfiguration<MainContract>()
