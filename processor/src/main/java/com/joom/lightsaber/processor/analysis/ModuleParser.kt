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

package com.joom.lightsaber.processor.analysis

import com.joom.lightsaber.LightsaberTypes
import com.joom.lightsaber.processor.ProcessingException
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.contains
import com.joom.lightsaber.processor.commons.rawType
import com.joom.lightsaber.processor.commons.toFieldDescriptor
import com.joom.lightsaber.processor.commons.toMethodDescriptor
import com.joom.lightsaber.processor.descriptors.MethodDescriptor
import com.joom.lightsaber.processor.logging.getLogger
import com.joom.lightsaber.processor.model.Binding
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.Converter
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.Injectee
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.InjectionTarget
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.Provider
import com.joom.lightsaber.processor.model.ProvisionPoint
import com.joom.lightsaber.processor.model.Scope
import io.michaelrocks.grip.Grip
import io.michaelrocks.grip.and
import io.michaelrocks.grip.annotatedWith
import io.michaelrocks.grip.fields
import io.michaelrocks.grip.from
import io.michaelrocks.grip.isStatic
import io.michaelrocks.grip.methodType
import io.michaelrocks.grip.methods
import io.michaelrocks.grip.mirrors.ClassMirror
import io.michaelrocks.grip.mirrors.FieldMirror
import io.michaelrocks.grip.mirrors.MethodMirror
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.getMethodType
import io.michaelrocks.grip.mirrors.getObjectTypeByInternalName
import io.michaelrocks.grip.mirrors.signature.GenericType
import io.michaelrocks.grip.not
import io.michaelrocks.grip.returns
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC

interface ModuleParser {
  fun parseModule(
    type: Type.Object,
    importeeModuleTypes: Collection<Type.Object>,
    providableTargets: Collection<InjectionTarget>,
    factories: Collection<Factory>,
    contracts: Collection<Contract>,
    moduleRegistry: ModuleRegistry
  ): Module
}

class ModuleParserImpl(
  private val grip: Grip,
  private val importParser: ImportParser,
  private val bindingRegistry: BindingRegistry,
  private val analyzerHelper: AnalyzerHelper,
  private val projectName: String
) : ModuleParser {

  private val logger = getLogger()

  private val bridgeRegistry = BridgeRegistry()

  override fun parseModule(
    type: Type.Object,
    importeeModuleTypes: Collection<Type.Object>,
    providableTargets: Collection<InjectionTarget>,
    factories: Collection<Factory>,
    contracts: Collection<Contract>,
    moduleRegistry: ModuleRegistry
  ): Module {
    return parseModule(
      grip.classRegistry.getClassMirror(type),
      importeeModuleTypes,
      providableTargets,
      factories,
      contracts,
      moduleRegistry
    )
  }

  private fun parseModule(
    mirror: ClassMirror,
    importeeModuleTypes: Collection<Type.Object>,
    providableTargets: Collection<InjectionTarget>,
    factories: Collection<Factory>,
    contracts: Collection<Contract>,
    moduleRegistry: ModuleRegistry
  ): Module {
    if (mirror.signature.typeVariables.isNotEmpty()) {
      throw ModuleParserException("Module cannot have a type parameters: ${mirror.type.className}")
    }

    val isComponentDefaultModule = when {
      Types.COMPONENT_TYPE in mirror.annotations -> true
      Types.MODULE_TYPE in mirror.annotations -> false
      else -> throw ModuleParserException("Class ${mirror.type.className} is neither a component nor a module")
    }

    val imports = importParser.parseImports(mirror, moduleRegistry, importeeModuleTypes, isComponentDefaultModule)

    bridgeRegistry.clear()
    mirror.methods.forEach { bridgeRegistry.reserveMethod(it.toMethodDescriptor()) }

    val providers = createProviders(mirror, providableTargets, factories, contracts)
    return Module(mirror.type, imports, providers, factories, contracts)
  }

  private fun createProviders(
    module: ClassMirror,
    providableTargets: Collection<InjectionTarget>,
    factories: Collection<Factory>,
    contracts: Collection<Contract>
  ): Collection<Provider> {
    val isProvidable = annotatedWith(Types.PROVIDE_TYPE) and not(isStatic())
    val methodsQuery = grip select methods from module where (isProvidable and methodType(not(returns(Type.Primitive.Void))))
    val fieldsQuery = grip select fields from module where isProvidable

    logger.debug("Module: {}", module.type.className)
    val constructorProviders = providableTargets.map { target ->
      logger.debug("  Constructor: {}", target.injectionPoints.first())
      newConstructorProvider(target.type, target)
    }

    val methodProviders = methodsQuery.execute()[module.type].orEmpty().mapIndexed { index, method ->
      logger.debug("  Method: {}", method)
      newMethodProvider(module.type, method, index)
    }

    val fieldProviders = fieldsQuery.execute()[module.type].orEmpty().mapIndexed { index, field ->
      logger.debug("  Field: {}", field)
      newFieldProvider(module.type, field, index)
    }

    val directProviders = constructorProviders + methodProviders + fieldProviders
    val moduleBindings = directProviders.flatMap { provider ->
      bindingRegistry.findBindingsByDependency(provider.dependency)
    }

    val bindingProviders = moduleBindings.map { binding ->
      logger.debug("  Binding: {} -> {}", binding.dependency, binding.ancestor)
      newBindingProvider(module.type, binding)
    }

    val factoryProviders = factories.map { factory ->
      logger.debug("  Factory: {}", factory)
      newFactoryProvider(module.type, factory)
    }

    val contractProviders = contracts.map { contract ->
      logger.debug("  Contract: {}", contract)
      newContractProvider(module.type, contract)
    }

    val providerCount =
      constructorProviders.size + methodProviders.size + fieldProviders.size + bindingProviders.size + factoryProviders.size + contractProviders.size
    return ArrayList<Provider>(providerCount).apply {
      addAll(constructorProviders)
      addAll(methodProviders)
      addAll(fieldProviders)
      addAll(bindingProviders)
      addAll(factoryProviders)
      addAll(contractProviders)
    }
  }

  private fun newConstructorProvider(container: Type.Object, target: InjectionTarget): Provider {
    val mirror = grip.classRegistry.getClassMirror(target.type)
    val providerType = getObjectTypeByInternalName("${target.type.internalName}\$ConstructorProvider\$$projectName")
    val dependency = Dependency(GenericType.Raw(target.type), analyzerHelper.findQualifier(mirror))
    val injectionPoint = target.injectionPoints.first() as InjectionPoint.Method
    val provisionPoint = ProvisionPoint.Constructor(dependency, injectionPoint)
    val scope = analyzerHelper.findScope(mirror)
    return Provider(providerType, provisionPoint, container, scope)
  }

  private fun newMethodProvider(container: Type.Object, method: MethodMirror, index: Int): Provider {
    val providerType = getObjectTypeByInternalName("${container.internalName}\$MethodProvider\$$index\$$projectName")
    val dependency = Dependency(method.signature.returnType, analyzerHelper.findQualifier(method))
    val injectionPoint = analyzerHelper.convertMethodToInjectionPoint(method, container)
    val provisionPoint = ProvisionPoint.Method(dependency, injectionPoint, null).withBridge()
    val scope = analyzerHelper.findScope(method)
    return Provider(providerType, provisionPoint, container, scope)
  }

  private fun newFieldProvider(container: Type.Object, field: FieldMirror, index: Int): Provider {
    val providerType = getObjectTypeByInternalName("${container.internalName}\$FieldProvider\$$index\$$projectName")
    val dependency = Dependency(field.signature.type, analyzerHelper.findQualifier(field))
    val provisionPoint = ProvisionPoint.Field(container, dependency, null, field).withBridge()
    val scope = analyzerHelper.findScope(field)
    return Provider(providerType, provisionPoint, container, scope)
  }

  private fun newBindingProvider(container: Type.Object, binding: Binding): Provider {
    val dependencyType = binding.dependency.type.rawType as Type.Object
    val ancestorType = binding.ancestor.type.rawType as Type.Object
    val providerType = getObjectTypeByInternalName("${dependencyType.internalName}\$${ancestorType.internalName}\$BindingProvider\$$projectName")
    val provisionPoint = ProvisionPoint.Binding(container, binding.ancestor, binding.dependency)
    return Provider(providerType, provisionPoint, container, Scope.None)
  }

  private fun newFactoryProvider(container: Type.Object, factory: Factory): Provider {
    val mirror = grip.classRegistry.getClassMirror(factory.type)
    val providerType = getObjectTypeByInternalName("${factory.type.internalName}\$FactoryProvider\$$projectName")
    val constructorMirror = MethodMirror.Builder()
      .access(ACC_PUBLIC)
      .name(MethodDescriptor.CONSTRUCTOR_NAME)
      .type(getMethodType(Type.Primitive.Void, Types.INJECTOR_TYPE))
      .build()
    val constructorInjectee = Injectee(Dependency(GenericType.Raw(Types.INJECTOR_TYPE)), Converter.Instance)
    val injectionPoint = InjectionPoint.Method(factory.implementationType, constructorMirror, listOf(constructorInjectee))
    val provisionPoint = ProvisionPoint.Constructor(factory.dependency, injectionPoint)
    val scope = analyzerHelper.findScope(mirror)
    return Provider(providerType, provisionPoint, container, scope)
  }

  private fun newContractProvider(container: Type.Object, contract: Contract): Provider {
    val providerType = getObjectTypeByInternalName("${contract.type.internalName}\$ContractProvider\$$projectName")
    val constructorMirror = MethodMirror.Builder()
      .access(ACC_PUBLIC)
      .name(MethodDescriptor.CONSTRUCTOR_NAME)
      .type(getMethodType(Type.Primitive.Void, Types.INJECTOR_TYPE))
      .build()
    val constructorInjectee = Injectee(Dependency(GenericType.Raw(Types.INJECTOR_TYPE)), Converter.Instance)
    val injectionPoint = InjectionPoint.Method(contract.implementationType, constructorMirror, listOf(constructorInjectee))
    val provisionPoint = ProvisionPoint.Constructor(contract.dependency, injectionPoint)
    val scope = Scope.Class(LightsaberTypes.SINGLETON_PROVIDER_TYPE)
    return Provider(providerType, provisionPoint, container, scope)
  }

  private fun ProvisionPoint.Method.withBridge(): ProvisionPoint.Method {
    val method = injectionPoint.method
    if (ACC_PRIVATE !in method.access) {
      return this
    }

    val bridgeMethod = bridgeRegistry.addBridge(method)
    val bridgeInjectionPoint = injectionPoint.copy(method = bridgeMethod)
    val bridgeProvisionPoint = ProvisionPoint.Method(dependency, bridgeInjectionPoint, null)
    return copy(bridge = bridgeProvisionPoint)
  }

  private fun ProvisionPoint.Field.withBridge(): ProvisionPoint.Field {
    val field = field
    if (ACC_PRIVATE !in field.access) {
      return this
    }

    val bridgeMethod = bridgeRegistry.addBridge(field)
    val bridgeInjectionPoint = InjectionPoint.Method(containerType, bridgeMethod, listOf())
    val bridgeProvisionPoint = ProvisionPoint.Method(dependency, bridgeInjectionPoint, null)
    return copy(bridge = bridgeProvisionPoint)
  }

  private fun BridgeRegistry.addBridge(method: MethodMirror): MethodMirror {
    val bridge = addBridge(method.toMethodDescriptor())
    return createBridgeMirror(bridge)
  }

  private fun BridgeRegistry.addBridge(field: FieldMirror): MethodMirror {
    val bridge = addBridge(field.toFieldDescriptor())
    return createBridgeMirror(bridge)
  }

  private fun createBridgeMirror(bridge: MethodDescriptor): MethodMirror {
    return MethodMirror.Builder()
      .access(ACC_PUBLIC or ACC_SYNTHETIC)
      .name(bridge.name)
      .type(bridge.type)
      .build()
  }
}

class ModuleParserException(message: String) : ProcessingException(message)
