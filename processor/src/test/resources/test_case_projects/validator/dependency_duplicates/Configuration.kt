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

package test_case_projects.validator.dependency_duplicates

import com.joom.lightsaber.Contract
import com.joom.lightsaber.ContractConfiguration
import com.joom.lightsaber.Import
import com.joom.lightsaber.Module
import com.joom.lightsaber.Provide

@Contract
interface AppContract {
  val dependency: Dependency
}

class AppContractConfiguration : ContractConfiguration<AppContract>() {

  @Import
  private fun importAppModule1(): AppModule1 {
    return AppModule1()
  }

  @Import
  private fun importAppModule2(): AppModule2 {
    return AppModule2()
  }
}

class Dependency

@Module
class AppModule1 {
  @Provide
  fun provideDependency(): Dependency {
    return Dependency()
  }
}

@Module
class AppModule2 {
  @Provide
  fun provideDependency(): Dependency {
    return Dependency()
  }
}
