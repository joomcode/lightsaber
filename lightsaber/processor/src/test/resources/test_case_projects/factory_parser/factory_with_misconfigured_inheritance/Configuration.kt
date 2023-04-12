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

package test_case_projects.factory_parser.factory_with_misconfigured_inheritance

import com.joom.lightsaber.Factory
import com.joom.lightsaber.Module
import com.joom.lightsaber.ProvidedAs
import com.joom.lightsaber.ProvidedBy

interface Foo {
  val foo: String
}

interface Bar {
  val bar: String
}

interface RootFactory {
  fun createFoo(): Foo

  fun createBar(): Bar
}

@Module
class RootModule

@Factory
@ProvidedAs(RootFactory::class)
@ProvidedBy(RootModule::class)
interface RootFactoryImpl : RootFactory {
  @Factory.Return(FooImpl::class)
  override fun createFoo(): Foo
}

class FooImpl @Factory.Inject constructor() : Foo {
  override val foo: String
    get() = "foo"
}

class BarImpl @Factory.Inject constructor() : Bar {
  override val bar: String
    get() = "bar"
}
