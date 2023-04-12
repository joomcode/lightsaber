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

package com.joom.lightsaber

import org.junit.Assert
import org.junit.Test

class FactoryInheritanceInjectionTest {

  @Test
  fun test() {
    val lightsaber = Lightsaber.Builder().build()
    val injector = lightsaber.createInjector(RootComponent())
    val factory = injector.getInstance<ObjectFactory>()

    Assert.assertEquals("foo", factory.createFoo().foo)
    Assert.assertEquals("bar", factory.createBar().bar)
  }

  interface Foo {
    val foo: String
  }

  interface Bar {
    val bar: String
  }

  interface ObjectFactory {
    fun createFoo(): Foo

    fun createBar(): Bar
  }

  @Module
  class RootModule

  @Component
  class RootComponent {
    @Import
    fun importRootModule(): RootModule {
      return RootModule()
    }
  }

  @Factory
  @ProvidedAs(ObjectFactory::class)
  @ProvidedBy(RootModule::class)
  interface ObjectFactoryImpl : ObjectFactory {
    @Factory.Return(FooImpl::class)
    override fun createFoo(): Foo

    @Factory.Return(BarImpl::class)
    override fun createBar(): Bar
  }

  class FooImpl @Factory.Inject constructor() : Foo {
    override val foo: String
      get() = "foo"
  }

  class BarImpl @Factory.Inject constructor() : Bar {
    override val bar: String
      get() = "bar"
  }
}
