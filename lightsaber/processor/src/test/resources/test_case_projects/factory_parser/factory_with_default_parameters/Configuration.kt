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

package test_case_projects.factory_parser.factory_with_default_parameters

import com.joom.lightsaber.Factory
import com.joom.lightsaber.Module
import com.joom.lightsaber.ProvidedBy

interface Target {
  val name: String
}

@Factory
@ProvidedBy(RootModule::class)
interface FactoryWithDefaultParameters {
  @Factory.Return(TargetImpl::class)
  fun createTarget(name: String = "default"): Target
}

class TargetImpl @Factory.Inject constructor(
  @Factory.Parameter override val name: String,
) : Target

@Module
class RootModule
