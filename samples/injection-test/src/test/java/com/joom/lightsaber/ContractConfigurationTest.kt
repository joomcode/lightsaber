/*
 * Copyright 2020 SIA Joom
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
import javax.inject.Named

class ContractConfigurationTest {
  @Test
  fun testSimpleContractConfiguration() {
    val lightsaber = Lightsaber.Builder().build()
    val configuration = SimpleContractConfiguration()
    val contract = lightsaber.createContract(configuration)

    Assert.assertEquals("String", contract.string)
  }

  @Test
  fun testSubInterfacesContractConfiguration() {
    val lightsaber = Lightsaber.Builder().build()
    val configuration = SubInterfacesContractConfiguration()
    val contract = lightsaber.createContract(configuration)

    Assert.assertEquals("String", contract.string)
  }

  @Test
  fun testComponentContractConfiguration() {
    val lightsaber = Lightsaber.Builder().build()
    val configuration = ComponentContractConfiguration()
    val contract = lightsaber.createContract(configuration)
    val injector = lightsaber.createInjector(configuration)

    Assert.assertEquals("String", contract.string)
    Assert.assertEquals("String", injector.getInstance<String>())
  }

  @Test
  fun testModuleContractConfiguration() {
    val lightsaber = Lightsaber.Builder().build()
    val configuration = ModuleContractConfiguration()
    val contract = lightsaber.createContract(configuration)
    val injector = lightsaber.createInjector(configuration)

    Assert.assertEquals("String", contract.string)
    Assert.assertEquals("String", injector.getInstance<String>())
  }

  @Test
  fun testComplexContractConfiguration() {
    val lightsaber = Lightsaber.Builder().build()
    val baseContract1Configuration = BaseContract1Configuration()
    val baseContract1 = lightsaber.createContract(baseContract1Configuration)
    val baseContract2Component = BaseContract2Component()
    val injector = lightsaber.createInjector(baseContract2Component)
    val complexContractConfiguration = ComplexContractConfiguration(baseContract1, injector.getInstance())
    val complexContract = lightsaber.createContract(complexContractConfiguration)

    Assert.assertEquals("BaseContract1, BaseContract2", complexContract.string)
  }

  @Test
  fun testStaticContractConfiguration() {
    val lightsaber = Lightsaber.Builder().build()

    StaticContract.init(lightsaber)

    Assert.assertEquals("String", StaticContract.instance.string)
  }

  class SimpleContractConfiguration : ContractConfiguration<SimpleContract>() {
    @Provide
    fun provideString(): String = "String"
  }

  interface SimpleContract {
    val string: String
  }

  class SubInterfacesContractConfiguration : ContractConfiguration<SubInterfacesContract>() {
    @Provide
    fun provideString(): String = "String"
  }

  interface SubInterfacesContract : SubInterface1, SubInterface2 {
    override val string: String
  }

  interface SubInterface1 {
    val string: CharSequence
  }

  interface SubInterface2 {
    @get:Named("SubInterface")
    val string: String
  }

  @Component
  class ComponentContractConfiguration : ContractConfiguration<ComponentContract>() {

    @Provide
    fun provideString(): String = "String"
  }

  interface ComponentContract {
    val string: String
  }

  @Module
  class ModuleContractConfiguration : ContractConfiguration<ModuleContract>() {

    @Provide
    fun provideString(): String = "String"
  }

  interface ModuleContract {
    val string: String
  }

  class ComplexContractConfiguration(
    @Import @Contract private val baseContract1: BaseContract1,
    @Import @Contract private val baseContract2: BaseContract2
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
}
