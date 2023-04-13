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

import org.junit.Test

class FactoryBridgesInjectionTest {

  @Test
  fun test() {
    val lightsaber = Lightsaber.Builder().build()
    val injector = lightsaber.createInjector(RootComponent())

    injector.getInstance<AnyFactory>().create()
    injector.getInstance<FooFactory>().create()
    injector.getInstance<BarFactory>().create()
    injector.getInstance<FactoryImpl>().create()
  }

  interface Foo
  interface Bar : Foo

  interface AnyFactory {
    fun create(): Any
  }

  interface FooFactory : AnyFactory {
    override fun create(): Foo
  }

  interface BarFactory : FooFactory {
    override fun create(): Bar
  }

  @Factory
  @ProvidedBy(RootModule::class)
  @ProvidedAs(AnyFactory::class, FooFactory::class, BarFactory::class)
  interface FactoryImpl : BarFactory {
    override fun create(): Impl
  }

  class Impl @Factory.Inject constructor() : Bar

  @Module
  class RootModule

  @Component
  class RootComponent {
    @Import
    fun importRootModule(): RootModule {
      return RootModule()
    }
  }
}
