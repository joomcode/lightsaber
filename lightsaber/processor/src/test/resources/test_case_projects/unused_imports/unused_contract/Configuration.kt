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

package test_case_projects.unused_imports.unused_contract

import com.joom.lightsaber.Contract
import com.joom.lightsaber.ContractConfiguration
import com.joom.lightsaber.Import
import com.joom.lightsaber.ProvidedAs
import com.joom.lightsaber.ProvidedBy
import javax.inject.Inject

interface MainContract {
  val mainDependency: MainDependency
}

interface SecondaryContract {
  val secondaryDependency: SecondaryDependency
}

interface UnusedContract {
  val unusedDependency: UnusedDependency
}

interface MainDependency
interface SecondaryDependency
interface UnusedDependency

@ProvidedAs(UnusedDependency::class)
@ProvidedBy(UnusedContractConfiguration::class)
class UnusedDependencyImpl @Inject constructor() : UnusedDependency

@ProvidedAs(SecondaryDependency::class)
@ProvidedBy(SecondaryContractConfiguration::class)
class SecondaryDependencyImpl @Inject constructor() : SecondaryDependency

@ProvidedAs(MainDependency::class)
@ProvidedBy(MainContractConfiguration::class)
class MainDependencyImpl @Inject constructor(
  val secondaryDependency: SecondaryDependency
) : MainDependency

class SecondaryContractConfiguration : ContractConfiguration<SecondaryContract>()
class UnusedContractConfiguration : ContractConfiguration<UnusedContract>()

class MainContractConfiguration(
  @Import @Contract val secondaryContract: SecondaryContract,
  @Import @Contract val unusedContract: UnusedContract,
) : ContractConfiguration<MainContract>()
