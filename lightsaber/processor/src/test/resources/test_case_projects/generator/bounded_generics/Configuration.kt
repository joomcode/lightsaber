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

package test_case_projects.generator.bounded_generics

import com.joom.lightsaber.ContractConfiguration
import com.joom.lightsaber.Provide

interface Key

interface Value<T : Any> {
  val value: T
}

interface GenericContainerValue<K : Any, V : Any>

interface GenericContract {
  val value: GenericContainerValue<Key, Value<*>>
}

class GenericContractConfiguration : ContractConfiguration<GenericContract>() {

  @Provide
  fun provideValue(): GenericContainerValue<Key, Value<*>> {
    return object : GenericContainerValue<Key, Value<*>> {}
  }
}
