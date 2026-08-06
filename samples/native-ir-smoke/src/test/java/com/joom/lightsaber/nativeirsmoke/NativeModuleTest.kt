package com.joom.lightsaber.nativeirsmoke

import com.joom.lightsaber.Lightsaber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NativeModuleTest {
  @Test
  fun createsConstructorAndMethodProvidedDependencies() {
    LazyDependency.creations = 0
    EagerDependency.creations = 0
    EagerInstantiationOrder.values.clear()
    val injector = Lightsaber.Builder().build().createInjector(NativeModule())

    assertEquals(1, EagerDependency.creations)
    assertEquals(listOf("source-second", "source-first"), EagerInstantiationOrder.values)
    injector.getInstance(NestedDependency::class.java)
    assertEquals("field", injector.getInstance(FieldProvidedValue::class.java).value)
    assertEquals("getter", injector.getInstance(GetterProvidedValue::class.java).value)
    val lazyConsumer = injector.getInstance(LazyConsumer::class.java)
    assertEquals(0, LazyDependency.creations)
    lazyConsumer.dependency.get()
    assertEquals(1, LazyDependency.creations)

    val dependency = injector.getInstance(Dependency::class.java)
    assertSame(dependency, injector.getInstance(Dependency::class.java))
    assertSame(dependency, injector.getInstance(DependencyApi::class.java))
    assertSame(dependency, injector.getInstance(ProviderConsumer::class.java).dependencyProvider.get())
    assertEquals("dependency:message", injector.getInstance(Message::class.java).value)
    assertSame(dependency, injector.getInstance(GenericConsumer::class.java).box.value)
    val qualifiedConsumer = injector.getInstance(QualifiedConsumer::class.java)
    assertEquals("blue", qualifiedConsumer.blue)
    assertEquals("red", qualifiedConsumer.red)
    assertEquals("dependency:widget", injector.getInstance(WidgetFactory::class.java).create("widget").description)
    assertEquals("dependency:alias", injector.getInstance(WidgetFactoryApi::class.java).create("alias").description)
    val qualifiedWidget = injector.getInstance(QualifiedWidgetFactory::class.java).create("red", "blue")
    assertEquals("blue", qualifiedWidget.blue)
    assertEquals("red", qualifiedWidget.red)
    assertEquals(
      "first",
      injector.getInstance(FirstFactoryOwner.BinderFactory::class.java).create().value,
    )
    assertEquals(
      "second",
      injector.getInstance(SecondFactoryOwner.BinderFactory::class.java).create().value,
    )
    val memberTarget = MemberTarget()
    injector.injectMembers(memberTarget)
    assertSame(dependency, memberTarget.dependency)
    assertSame(dependency, memberTarget.injectedByMethod)
  }

  @Test
  fun createsContractWithoutReflection() {
    val lightsaber = Lightsaber.Builder().build()
    val importedValue = ImportedValue("imported")
    val providerImportedValue = ProviderImportedValue("provider-imported")
    val configuration = SmokeContractConfiguration(object : UpstreamContract {
      override val importedValue = importedValue
      override val providerImportedValue = javax.inject.Provider { providerImportedValue }
    })
    val contract = lightsaber.createContract(configuration)

    assertEquals("contract", contract.contractDependency.value)
    assertEquals("contract", contract.inheritedDependency.value)
    assertEquals("contract", contract.contractMessage.value)
    assertSame(importedValue, contract.importedValue)
    assertSame(providerImportedValue, contract.providerImportedValue)
    assertEquals("function-imported", contract.functionImportedValue.value)
  }
}
