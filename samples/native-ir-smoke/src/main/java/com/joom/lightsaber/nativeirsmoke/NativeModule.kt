package com.joom.lightsaber.nativeirsmoke

import com.joom.lightsaber.Module
import com.joom.lightsaber.ContractConfiguration
import com.joom.lightsaber.Contract
import com.joom.lightsaber.Import
import com.joom.lightsaber.Factory
import com.joom.lightsaber.Eager
import com.joom.lightsaber.Lazy
import com.joom.lightsaber.Provide
import com.joom.lightsaber.ProvidedBy
import com.joom.lightsaber.ProvidedAs
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class Blue

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class Red

@Module
internal class NativeModule {
  @Provide
  private val fieldValue = FieldProvidedValue("field")

  @get:Provide
  private val getterValue: GetterProvidedValue
    get() = GetterProvidedValue("getter")

  @Import
  fun importNestedModule(): NestedModule = NestedModule()

  @Provide
  fun provideMessage(dependency: Dependency): Message = Message("${dependency.value}:message")

  @Provide
  fun provideBox(dependency: Dependency): Box<Dependency> = Box(dependency)

  @Blue
  @Provide
  fun provideBlue(): String = "blue"

  @Red
  @Provide
  fun provideRed(): String = "red"
}

internal data class FieldProvidedValue(val value: String)

internal data class GetterProvidedValue(val value: String)

@Module
internal class NestedModule

@ProvidedBy(NestedModule::class)
internal class NestedDependency @Inject private constructor()

internal data class Message(val value: String)

@Singleton
@ProvidedAs(DependencyApi::class)
@ProvidedBy(NativeModule::class)
internal class Dependency @Inject private constructor() : DependencyApi {
  val value = "dependency"

  override fun value(): String = value
}

internal interface DependencyApi {
  fun value(): String
}

@ProvidedBy(NativeModule::class)
internal class ProviderConsumer @Inject private constructor(
  val dependencyProvider: Provider<Dependency>,
)

internal data class Box<T>(val value: T)

@ProvidedBy(NativeModule::class)
internal class GenericConsumer @Inject private constructor(
  val box: Box<Dependency>,
)

@ProvidedBy(NativeModule::class)
internal class QualifiedConsumer @Inject private constructor(
  @Blue val blue: String,
  @Red val red: String,
)

@ProvidedBy(NativeModule::class)
internal class LazyConsumer @Inject private constructor(
  val dependency: Lazy<LazyDependency>,
)

@Singleton
@ProvidedBy(NativeModule::class)
internal class LazyDependency @Inject private constructor() {
  init {
    creations += 1
  }

  companion object {
    var creations = 0
  }
}

@Eager
@Singleton
@ProvidedBy(NativeModule::class)
internal class EagerDependency @Inject private constructor() {
  init {
    creations += 1
  }

  companion object {
    var creations = 0
  }
}

internal interface Widget {
  val description: String
}

internal interface WidgetFactoryApi {
  fun create(name: String): Widget
}

@Factory
@ProvidedAs(WidgetFactoryApi::class)
@ProvidedBy(NativeModule::class)
internal interface WidgetFactory : WidgetFactoryApi {
  @Factory.Return(WidgetImpl::class)
  override fun create(name: String): Widget
}

internal class WidgetImpl @Factory.Inject private constructor(
  dependency: Dependency,
  @Factory.Parameter name: String?,
) : Widget {
  override val description = "${dependency.value}:$name"
}

@Factory
@ProvidedBy(NativeModule::class)
internal interface QualifiedWidgetFactory {
  @Factory.Return(QualifiedWidget::class)
  fun create(@Red red: String, @Blue blue: String): QualifiedWidget
}

internal class QualifiedWidget @Factory.Inject private constructor(
  @Blue @Factory.Parameter val blue: String,
  @Red @Factory.Parameter val red: String,
)

internal object FirstFactoryOwner {
  @Factory
  @ProvidedBy(NativeModule::class)
  interface BinderFactory {
    @Factory.Return(FirstWidget::class)
    fun create(): FirstWidget
  }
}

internal object SecondFactoryOwner {
  @Factory
  @ProvidedBy(NativeModule::class)
  interface BinderFactory {
    @Factory.Return(SecondWidget::class)
    fun create(): SecondWidget
  }
}

internal class FirstWidget @Factory.Inject private constructor() {
  val value = "first"
}

internal class SecondWidget @Factory.Inject private constructor() {
  val value = "second"
}

internal class MemberTarget {
  @Inject
  lateinit var dependency: Dependency

  var injectedByMethod: Dependency? = null

  @Inject
  fun injectByMethod(dependency: Dependency) {
    injectedByMethod = dependency
  }
}

internal interface ParentSmokeContract {
  val inheritedDependency: ContractDependency
}

internal interface SmokeContract : ParentSmokeContract {
  val contractDependency: ContractDependency
  val contractMessage: ContractMessage
  val importedValue: ImportedValue
  val providerImportedValue: ProviderImportedValue
  val functionImportedValue: FunctionImportedValue
}

internal interface UpstreamContract {
  val importedValue: ImportedValue
  val providerImportedValue: Provider<ProviderImportedValue>
}

internal class SmokeContractConfiguration(
  @Import @Contract private val upstreamContract: UpstreamContract,
) : ContractConfiguration<SmokeContract>() {
  @Import
  @Contract
  private fun importFunctionContract(): FunctionUpstreamContract {
    return object : FunctionUpstreamContract {
      override val functionImportedValue = FunctionImportedValue("function-imported")
    }
  }

  @Provide
  fun provideContractMessage(dependency: ContractDependency): ContractMessage {
    return ContractMessage(dependency.value)
  }
}

@ProvidedBy(SmokeContractConfiguration::class)
internal class ContractDependency @Inject private constructor() {
  val value = "contract"
}

internal data class ContractMessage(val value: String)

internal data class ImportedValue(val value: String)

internal data class ProviderImportedValue(val value: String)

internal interface FunctionUpstreamContract {
  val functionImportedValue: FunctionImportedValue
}

internal data class FunctionImportedValue(val value: String)
