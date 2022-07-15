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

package test_case_projects.validator.valid_configuration_with_lazy_imports

import com.joom.lightsaber.Lightsaber

class AppMain {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      AppMain().run()
    }
  }

  fun run() {
    val injector = Lightsaber.Builder().build()
    val contract = injector.createContract(
      AppContractConfiguration(
        lazyContract = { injector.createContract(LazyContractConfiguration()) },
        kotlinLazyContract = lazy { injector.createContract(KotlinLazyContractConfiguration()) }
      )
    )

    contract.dependency.foo()
  }
}
