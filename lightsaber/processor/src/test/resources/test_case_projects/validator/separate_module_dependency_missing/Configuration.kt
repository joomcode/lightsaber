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

package test_case_projects.validator.separate_module_dependency_missing

import com.joom.lightsaber.Module
import com.joom.lightsaber.Provide

class FirstDependency

class SecondDependency(private val firstDependency: FirstDependency)

@Module
class MyModule {

  @Provide
  fun provideDependency(firstDependency: FirstDependency): SecondDependency {
    return SecondDependency(firstDependency)
  }
}
