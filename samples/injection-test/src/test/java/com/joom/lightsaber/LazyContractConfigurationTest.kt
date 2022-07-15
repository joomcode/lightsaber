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

package com.joom.lightsaber

import org.junit.Assert
import org.junit.Test
import javax.inject.Inject
import javax.inject.Named

class LazyContractConfigurationTest {

  @Test
  fun testComplexContractConfiguration() {
    val lightsaber = Lightsaber.Builder().build()
    val baseContract1Configuration = BaseContract1Configuration()
    val baseContract1 = lazy { lightsaber.createContract(baseContract1Configuration) }
    val baseContract2Component = BaseContract2Component()
    val injector = lightsaber.createInjector(baseContract2Component)
    val baseContract2Holder: BaseContract2Holder = injector.getInstance()
    val complexContractConfiguration = ComplexContractConfiguration(baseContract1, baseContract2Holder.baseContract2)

    val complexContract = lightsaber.createContract(complexContractConfiguration)

    Assert.assertFalse(baseContract1.isInitialized())

    Assert.assertEquals("BaseContract1, BaseContract2", complexContract.string)

    Assert.assertTrue(baseContract1.isInitialized())
  }

  @Test
  fun testStaticDependentContractConfiguration() {
    val lightsaber = Lightsaber.Builder().build()

    StaticContract.init(lightsaber)
    val dependencies = lightsaber.createContract(StaticContractWithDependenciesDependencies(lazy { StaticContract.instance }))
    StaticContractWithDependencies.init(lightsaber, dependencies)

    Assert.assertEquals("String", StaticContractWithDependencies.instance.string)
  }

  class ComplexContractConfiguration(
    @Import @Contract private val baseContract1: kotlin.Lazy<BaseContract1>,
    @Import @Contract private val baseContract2: Lazy<BaseContract2>
  ) : ContractConfiguration<ComplexContract>() {

    @Provide
    fun provideStrings(
      @Named("BaseContract1") strings1: List<String>,
      @Named("BaseContract2") strings2: List<String>
    ): List<String> {
      return strings1 + strings2
    }
  }

  class BaseContract1Configuration : ContractConfiguration<BaseContract1>() {
    @Import
    fun importBaseModule() = BaseModule("BaseContract1")

    @Provide
    @Named("BaseContract1")
    fun provideStrings(string: String): List<String> = listOf(string)


  }

  @Component
  class BaseContract2Component {

    @Import
    fun importBaseModule() = BaseModule("BaseContract2")

    @Provide
    @Named("BaseContract2")
    fun provideStrings(string: String): List<String> = listOf(string)
  }

  interface ComplexContract {
    val string: String
  }

  interface BaseContract1 {
    @get:Named("BaseContract1")
    val strings: List<String>
  }

  @ProvidedBy(BaseContract2Component::class)
  class BaseContract2Holder @Inject constructor(
    val baseContract2: Lazy<BaseContract2>
  )

  @Contract
  @ProvidedBy(BaseContract2Component::class)
  interface BaseContract2 {

    @get:Named("BaseContract2")
    val strings: List<String>
  }

  @Module
  @ImportedBy(ComplexContractConfiguration::class)
  class ComplexModule {

    @Provide
    fun provideString(strings: List<String>): String {
      return strings.joinToString()
    }
  }

  @Module
  class BaseModule(@Provide private val string: String)

  interface StaticContract {
    val string: String

    class Configuration : ContractConfiguration<StaticContract>() {
      @Provide
      fun provideString(): String = "String"
    }

    companion object {
      lateinit var instance: StaticContract

      fun init(lightsaber: Lightsaber) {
        instance = lightsaber.createContract(Configuration())
      }
    }
  }

  interface StaticContractWithDependencies {
    val string: String

    interface Dependencies {
      val string: String
    }

    class Configuration(
      @Import @Contract private val dependencies: Dependencies
    ) : ContractConfiguration<StaticContractWithDependencies>()

    companion object {
      lateinit var instance: StaticContractWithDependencies

      fun init(lightsaber: Lightsaber, dependencies: Dependencies) {
        instance = lightsaber.createContract(Configuration(dependencies))
      }
    }
  }

  class StaticContractWithDependenciesDependencies(
    @Import @Contract private val contract: kotlin.Lazy<StaticContract>
  ) : ContractConfiguration<StaticContractWithDependencies.Dependencies>()
}
