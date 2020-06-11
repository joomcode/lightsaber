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

package com.joom.lightsaber.processor.generation.model

import com.joom.lightsaber.LightsaberTypes
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.rawType
import com.joom.lightsaber.processor.descriptors.MethodDescriptor
import com.joom.lightsaber.processor.model.Binding
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.Converter
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.Injectee
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint
import com.joom.lightsaber.processor.model.Scope
import io.michaelrocks.grip.mirrors.MethodMirror
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.getMethodType
import io.michaelrocks.grip.mirrors.getObjectTypeByInternalName
import io.michaelrocks.grip.mirrors.signature.GenericType
import org.objectweb.asm.Opcodes

interface ProviderFactory {
  fun createProvidersForModule(module: Module): Collection<Provider>
}

class ProviderFactoryImpl(
  private val projectName: String
) : ProviderFactory {

  override fun createProvidersForModule(module: Module): Collection<Provider> {
    val providers = ArrayList<Provider>(module.provisionPoints.size + module.factories.size + module.contracts.size)
    module.provisionPoints.mapIndexedTo(providers) { index, provisionPoint -> newProviderForProvisionPoint(module, provisionPoint, index) }
    module.bindings.mapTo(providers) { newBindingProvider(module, it) }
    module.factories.mapTo(providers) { newFactoryProvider(module, it) }
    module.contracts.mapTo(providers) { newContractProvider(module, it) }
    return providers
  }

  private fun newProviderForProvisionPoint(module: Module, provisionPoint: ProvisionPoint, index: Int): Provider {
    return when (provisionPoint) {
      is ProvisionPoint.Constructor -> newConstructorProvider(module, provisionPoint)
      is ProvisionPoint.Method -> newMethodProvider(module, provisionPoint, index)
      is ProvisionPoint.Field -> newFieldProvider(module, provisionPoint, index)
      // FIXME: Maybe there should be no ProvisionPoint.Binding.
      is ProvisionPoint.Binding -> error("Modules mustn't contain binding provision points")
    }
  }

  private fun newConstructorProvider(module: Module, provisionPoint: ProvisionPoint.Constructor): Provider {
    val moduleType = provisionPoint.containerType
    val providerType = getObjectTypeByInternalName("${moduleType.internalName}\$ConstructorProvider\$$projectName")
    return Provider(providerType, module.type, provisionPoint)
  }

  private fun newMethodProvider(module: Module, provisionPoint: ProvisionPoint.Method, index: Int): Provider {
    val providerType = getObjectTypeByInternalName("${module.type.internalName}\$MethodProvider\$$index\$$projectName")
    return Provider(providerType, module.type, provisionPoint)
  }

  private fun newFieldProvider(module: Module, provisionPoint: ProvisionPoint.Field, index: Int): Provider {
    val providerType = getObjectTypeByInternalName("${module.type.internalName}\$FieldProvider\$$index\$$projectName")
    return Provider(providerType, module.type, provisionPoint)
  }

  private fun newBindingProvider(module: Module, binding: Binding): Provider {
    val dependencyType = binding.dependency.type.rawType as Type.Object
    val ancestorType = binding.ancestor.type.rawType as Type.Object
    val providerType = getObjectTypeByInternalName("${dependencyType.internalName}\$${ancestorType.internalName}\$BindingProvider\$$projectName")
    val provisionPoint = ProvisionPoint.Binding(module.type, binding.ancestor, binding.dependency)
    return Provider(providerType, module.type, provisionPoint)
  }

  private fun newFactoryProvider(module: Module, factory: Factory): Provider {
    val providerType = getObjectTypeByInternalName("${factory.type.internalName}\$FactoryProvider\$$projectName")
    val constructorMirror = MethodMirror.Builder()
      .access(Opcodes.ACC_PUBLIC)
      .name(MethodDescriptor.CONSTRUCTOR_NAME)
      .type(getMethodType(Type.Primitive.Void, Types.INJECTOR_TYPE))
      .build()
    val constructorInjectee = Injectee(Dependency(GenericType.Raw(Types.INJECTOR_TYPE)), Converter.Instance)
    val injectionPoint = InjectionPoint.Method(factory.implementationType, constructorMirror, listOf(constructorInjectee))
    val provisionPoint = ProvisionPoint.Constructor(factory.dependency, Scope.None, injectionPoint)
    return Provider(providerType, module.type, provisionPoint)
  }

  private fun newContractProvider(module: Module, contract: Contract): Provider {
    val providerType = getObjectTypeByInternalName("${contract.type.internalName}\$ContractProvider\$$projectName")
    val scope = Scope.Class(LightsaberTypes.SINGLETON_PROVIDER_TYPE)
    val constructorMirror = MethodMirror.Builder()
      .access(Opcodes.ACC_PUBLIC)
      .name(MethodDescriptor.CONSTRUCTOR_NAME)
      .type(getMethodType(Type.Primitive.Void, Types.INJECTOR_TYPE))
      .build()
    val constructorInjectee = Injectee(Dependency(GenericType.Raw(Types.INJECTOR_TYPE)), Converter.Instance)
    val injectionPoint = InjectionPoint.Method(contract.implementationType, constructorMirror, listOf(constructorInjectee))
    val provisionPoint = ProvisionPoint.Constructor(contract.dependency, scope, injectionPoint)
    return Provider(providerType, module.type, provisionPoint)
  }
}
