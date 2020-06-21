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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

class EagerInjectionTest {
  @Test
  fun testNonEagerComponent() {
    val lightsaber = Lightsaber.Builder().build()
    val component = NonEagerComponent()

    assertEquals(0, component.counter.get())
    val injector = lightsaber.createInjector(component)
    assertEquals(0, component.counter.get())
    assertEquals("0", injector.getInstance<String>())
    assertEquals(1, component.counter.get())
  }

  @Test
  fun testEagerComponent() {
    val lightsaber = Lightsaber.Builder().build()
    val component = EagerComponent()

    assertEquals(0, component.counter.get())
    val injector = lightsaber.createInjector(component)
    assertEquals(1, component.counter.get())
    assertEquals("0", injector.getInstance<String>())
    assertEquals(1, component.counter.get())
  }

  @Test
  fun testEagerContractConfiguration() {
    val lightsaber = Lightsaber.Builder().build()
    val configuration = EagerContractConfiguration()

    assertEquals(0, configuration.counter.get())
    val contract = lightsaber.createContract(configuration)
    assertEquals(1, configuration.counter.get())
    assertEquals("0", contract.string)
    assertEquals(1, configuration.counter.get())
  }

  @Test
  fun testEagerWithInjectConstructor() {
    val lightsaber = Lightsaber.Builder().build()
    val component = EagerInjectConstructorComponent()

    assertFalse(EagerInjectConstructorDependency.isCreated.get())
    lightsaber.createInjector(component)
    assertTrue(EagerInjectConstructorDependency.isCreated.get())
  }

  @Test
  fun testEagerWithNestedModules() {
    val lightsaber = Lightsaber.Builder().build()
    val component = NestedComponent()

    assertEquals(0, component.nestedComponentCounter.get())
    assertEquals(0, component.nestedParentModuleCounter.get())
    assertEquals(0, component.nestedChildModuleCounter.get())
    val injector = lightsaber.createInjector(component)
    assertEquals(1, component.nestedComponentCounter.get())
    assertEquals(1, component.nestedParentModuleCounter.get())
    assertEquals(1, component.nestedChildModuleCounter.get())
    assertEquals("0", injector.getInstance<String>(named("NestedComponent")))
    assertEquals("0", injector.getInstance<String>(named("NestedParentModule")))
    assertEquals("0", injector.getInstance<String>(named("NestedChildModule")))
    assertEquals(1, component.nestedComponentCounter.get())
    assertEquals(1, component.nestedParentModuleCounter.get())
    assertEquals(1, component.nestedChildModuleCounter.get())
  }

  @Test
  fun testCyclicDependencies() {
    val lightsaber = Lightsaber.Builder().build()
    val component = CyclicComponent()

    assertFalse(CyclicDependency1.isCreated.get())
    assertFalse(CyclicDependency2.isCreated.get())
    val injector = lightsaber.createInjector(component)
    assertTrue(CyclicDependency1.isCreated.get())
    assertTrue(CyclicDependency2.isCreated.get())
    assertNotNull(injector.getInstance<CyclicDependency1>())
    assertNotNull(injector.getInstance<CyclicDependency2>())
  }

  @Test
  fun testEagerWithParentDependency() {
    val lightsaber = Lightsaber.Builder().build()
    val component = EagerWithParentDependencyComponent()
    val injector = lightsaber.createInjector(component)

    assertEquals("Hello, Parent", injector.getInstance<String>())
  }

  @Test
  fun testEagerWithChildDependency() {
    val lightsaber = Lightsaber.Builder().build()
    val component = EagerWithChildDependencyComponent()
    val injector = lightsaber.createInjector(component)

    assertEquals("Hello, Child", injector.getInstance<String>())
  }

  @Test
  fun testEagerWithSiblingDependencies() {
    val lightsaber = Lightsaber.Builder().build()
    val component = EagerWithSiblingDependenciesComponent()
    val injector = lightsaber.createInjector(component)

    assertEquals("Eager1 to Sibling2", injector.getInstance<String>(named("Eager1")))
    assertEquals("Eager2 to Sibling1", injector.getInstance<String>(named("Eager2")))
  }

  @Test
  fun testEagerInTheMiddle() {
    val lightsaber = Lightsaber.Builder().build()
    val component = EagerInTheMiddleComponent()
    val injector = lightsaber.createInjector(component)

    assertEquals("Between Parent and Child", injector.getInstance<String>())
  }

  @Component
  class NonEagerComponent {

    val counter = AtomicInteger()

    @Provide
    @Singleton
    fun provideString(): String = counter.getAndIncrement().toString()
  }

  @Component
  class EagerComponent {

    val counter = AtomicInteger()

    @Provide
    @Eager
    @Singleton
    fun provideString(): String = counter.getAndIncrement().toString()
  }

  class EagerContractConfiguration : ContractConfiguration<EagerContract>() {
    val counter = AtomicInteger()

    @Provide
    @Eager
    @Singleton
    fun provideString(): String = counter.getAndIncrement().toString()
  }

  @Component
  class EagerInjectConstructorComponent

  @Eager
  @Singleton
  @ProvidedBy(EagerInjectConstructorComponent::class)
  class EagerInjectConstructorDependency @Inject constructor() {

    init {
      isCreated.set(true)
    }

    companion object {
      val isCreated = AtomicBoolean(false)
    }
  }

  interface EagerContract {
    val string: String
  }

  @Component
  class NestedComponent {

    val nestedComponentCounter = AtomicInteger(0)
    val nestedParentModuleCounter = AtomicInteger(0)
    val nestedChildModuleCounter = AtomicInteger(0)

    @Import
    fun importNestedParentModule() = NestedParentModule(nestedParentModuleCounter, nestedChildModuleCounter)

    @Provide
    @Eager
    @Singleton
    @Named("NestedComponent")
    fun provideString(): String = nestedComponentCounter.getAndIncrement().toString()
  }

  @Module
  class NestedParentModule(
    private val nestedParentModuleCounter: AtomicInteger,
    private val nestedChildModuleCounter: AtomicInteger
  ) {

    @Import
    fun importNestedChildModule() = NestedChildModule(nestedChildModuleCounter)

    @Provide
    @Eager
    @Singleton
    @Named("NestedParentModule")
    fun provideString(): String = nestedParentModuleCounter.getAndIncrement().toString()
  }

  @Module
  class NestedChildModule(
    private val nestedChildModuleCounter: AtomicInteger
  ) {

    @Provide
    @Eager
    @Singleton
    @Named("NestedChildModule")
    fun provideString(): String = nestedChildModuleCounter.getAndIncrement().toString()
  }

  @Component
  class CyclicComponent

  @Eager
  @Singleton
  @ProvidedBy(CyclicComponent::class)
  class CyclicDependency1 @Inject constructor(
    val dependency2: CyclicDependency2
  ) {

    init {
      isCreated.set(true)
    }

    companion object {
      val isCreated = AtomicBoolean(false)
    }
  }

  @Eager
  @Singleton
  @ProvidedBy(CyclicComponent::class)
  class CyclicDependency2 @Inject constructor(
    val dependency1: Lazy<CyclicDependency2>
  ) {

    init {
      isCreated.set(true)
    }

    companion object {
      val isCreated = AtomicBoolean(false)
    }
  }

  @Component
  class EagerWithParentDependencyComponent {

    @Provide
    @Named("Parent")
    fun provideParentString(): String {
      return "Parent"
    }
  }

  @Module
  @ImportedBy(EagerWithParentDependencyComponent::class)
  class EagerWithParentDependencyModule {

    @Provide
    @Eager
    @Singleton
    fun provideString(@Named("Parent") parentString: String): String {
      return "Hello, $parentString"
    }
  }

  @Component
  class EagerWithChildDependencyComponent {

    @Provide
    @Eager
    @Singleton
    fun provideString(@Named("Child") childString: String): String {
      return "Hello, $childString"
    }
  }

  @Module
  @ImportedBy(EagerWithChildDependencyComponent::class)
  class EagerWithChildDependencyModule {

    @Provide
    @Named("Child")
    fun provideChildString(): String {
      return "Child"
    }
  }

  @Component
  class EagerWithSiblingDependenciesComponent

  @Module
  @ImportedBy(EagerWithSiblingDependenciesComponent::class)
  class EagerWithSiblingDependenciesModule1 {

    @Provide
    @Eager
    @Singleton
    @Named("Eager1")
    fun provideEager1String(@Named("Sibling2") sibling2: String): String {
      return "Eager1 to $sibling2"
    }

    @Provide
    @Named("Sibling1")
    fun provideSibling1String(): String {
      return "Sibling1"
    }
  }

  @Module
  @ImportedBy(EagerWithSiblingDependenciesComponent::class)
  class EagerWithSiblingDependenciesModule2 {

    @Provide
    @Eager
    @Singleton
    @Named("Eager2")
    fun provideEager1String(@Named("Sibling1") sibling1: String): String {
      return "Eager2 to $sibling1"
    }

    @Provide
    @Named("Sibling2")
    fun provideSibling2String(): String {
      return "Sibling2"
    }
  }

  @Component
  class EagerInTheMiddleComponent {

    @Provide
    @Eager
    @Singleton
    @Named("Parent")
    fun provideParentString(): String {
      return "Parent"
    }
  }

  @Module
  @ImportedBy(EagerInTheMiddleComponent::class)
  class EagerInTheMiddleParentModule {

    @Provide
    @Eager
    @Singleton
    fun provideString(@Named("Parent") parentString: String, @Named("Child") childString: String): String {
      return "Between $parentString and $childString"
    }
  }

  @Module
  @ImportedBy(EagerInTheMiddleParentModule::class)
  class EagerInTheMiddleComponentChildModule {

    @Provide
    @Eager
    @Singleton
    @Named("Child")
    fun provideChildString(): String {
      return "Child"
    }
  }
}
