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

package test_case_projects.generator.first_project

import com.joom.lightsaber.Contract
import com.joom.lightsaber.ContractConfiguration
import com.joom.lightsaber.ProvidedAs
import com.joom.lightsaber.ProvidedBy
import javax.inject.Inject

interface FirstDependency

@ProvidedBy(FirstDependencyContractConfiguration::class)
@ProvidedAs(FirstDependency::class)
internal class FirstDependencyImpl @Inject constructor() : FirstDependency

@Contract
interface FirstDependencyContract {
  val dependency: FirstDependency
}

class FirstDependencyContractConfiguration : ContractConfiguration<FirstDependencyContract>()
