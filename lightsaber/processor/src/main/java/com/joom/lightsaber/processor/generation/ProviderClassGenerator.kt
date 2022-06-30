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

package com.joom.lightsaber.processor.generation

import com.joom.grip.ClassRegistry
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.getObjectType
import com.joom.grip.mirrors.isPrimitive
import com.joom.lightsaber.LightsaberTypes
import com.joom.lightsaber.processor.commons.GeneratorAdapter
import com.joom.lightsaber.processor.commons.StandaloneClassWriter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.exhaustive
import com.joom.lightsaber.processor.commons.newMethod
import com.joom.lightsaber.processor.commons.rawType
import com.joom.lightsaber.processor.commons.toFieldDescriptor
import com.joom.lightsaber.processor.commons.toMethodDescriptor
import com.joom.lightsaber.processor.descriptors.FieldDescriptor
import com.joom.lightsaber.processor.descriptors.MethodDescriptor
import com.joom.lightsaber.processor.generation.model.KeyRegistry
import com.joom.lightsaber.processor.generation.model.Provider
import com.joom.lightsaber.processor.generation.model.ProviderMedium
import com.joom.lightsaber.processor.generation.model.moduleType
import com.joom.lightsaber.processor.generation.model.lazyModule
import com.joom.lightsaber.processor.model.Binding
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ContractProvisionPoint
import com.joom.lightsaber.processor.model.Converter
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.Injectee
import com.joom.lightsaber.processor.model.ProvisionPoint
import com.joom.lightsaber.processor.watermark.WatermarkClassVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_SUPER
import org.objectweb.asm.Opcodes.V1_6

class ProviderClassGenerator(
  private val classRegistry: ClassRegistry,
  private val keyRegistry: KeyRegistry,
  private val provider: Provider
) {

  private val moduleType = if (provider.lazyModule) Types.LAZY_TYPE else provider.moduleType

  private val providerConstructor: MethodDescriptor
    get() = if (moduleType == null) CONSTRUCTOR_WITH_INJECTOR else MethodDescriptor.forConstructor(moduleType, Types.INJECTOR_TYPE)

  fun generate(): ByteArray {
    val classWriter = StandaloneClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS, classRegistry)
    val classVisitor = WatermarkClassVisitor(classWriter, true)
    classVisitor.visit(
      V1_6,
      ACC_PUBLIC or ACC_SUPER,
      provider.type.internalName,
      null,
      Types.OBJECT_TYPE.internalName,
      arrayOf(Types.PROVIDER_TYPE.internalName)
    )

    generateFields(classVisitor)
    generateConstructor(classVisitor)
    generateGetMethod(classVisitor)

    classVisitor.visitEnd()
    return classWriter.toByteArray()
  }

  private fun generateFields(classVisitor: ClassVisitor) {
    generateInjectorField(classVisitor)
    if (moduleType != null) {
      generateModuleField(classVisitor, moduleType)
    }
  }

  private fun generateModuleField(classVisitor: ClassVisitor, moduleType: Type.Object) {
    val fieldVisitor = classVisitor.visitField(
      ACC_PRIVATE or ACC_FINAL,
      MODULE_FIELD_NAME,
      moduleType.descriptor,
      null,
      null
    )
    fieldVisitor.visitEnd()
  }

  private fun generateInjectorField(classVisitor: ClassVisitor) {
    val fieldVisitor = classVisitor.visitField(
      ACC_PRIVATE or ACC_FINAL,
      INJECTOR_FIELD.name,
      INJECTOR_FIELD.type.descriptor,
      null,
      null
    )
    fieldVisitor.visitEnd()
  }

  private fun generateConstructor(classVisitor: ClassVisitor) {
    classVisitor.newMethod(ACC_PUBLIC, providerConstructor) {
      visitCode()
      loadThis()
      invokeConstructor(Types.OBJECT_TYPE, MethodDescriptor.forDefaultConstructor())

      if (moduleType == null) {
        loadThis()
        loadArg(0)
        putField(provider.type, INJECTOR_FIELD)
      } else {
        loadThis()
        loadArg(1)
        putField(provider.type, INJECTOR_FIELD)

        loadThis()
        loadArg(0)
        putField(provider.type, MODULE_FIELD_NAME, moduleType)
      }
    }
  }

  private fun generateGetMethod(classVisitor: ClassVisitor) {
    classVisitor.newMethod(ACC_PUBLIC, GET_METHOD) {
      exhaustive(
        when (val medium = provider.medium) {
          is ProviderMedium.ProvisionPoint -> provideFromProvisionPoint(medium.provisionPoint)
          is ProviderMedium.Binding -> provideFromBinding(medium.binding)
          is ProviderMedium.Factory -> provideFactory(medium.factory)
          is ProviderMedium.Contract -> provideContract(medium.contract)
          is ProviderMedium.ContractProvisionPoint -> provideFromContractProvisionPoint(medium.contractType, medium.isLazy, medium.contractProvisionPoint)
        }
      )

      valueOf(provider.dependency.type.rawType)
    }
  }

  private fun GeneratorAdapter.provideFromProvisionPoint(provisionPoint: ProvisionPoint) {
    val bridge = provisionPoint.bridge
    if (bridge != null) {
      provideFromMethod(bridge)
    } else {
      exhaustive(
        when (provisionPoint) {
          is ProvisionPoint.Field -> provideFromField(provisionPoint)
          is ProvisionPoint.Constructor -> provideFromConstructor(provisionPoint)
          is ProvisionPoint.Method -> provideFromMethod(provisionPoint)
        }
      )
    }
  }

  private fun GeneratorAdapter.provideFromField(provisionPoint: ProvisionPoint.Field) {
    loadThis()
    getField(provider.type, MODULE_FIELD_NAME, provisionPoint.containerType)
    val field = provisionPoint.field.toFieldDescriptor()
    getField(provisionPoint.containerType, field)
  }

  private fun GeneratorAdapter.provideFromConstructor(provisionPoint: ProvisionPoint.Constructor) {
    invokeConstructor(provisionPoint)
    injectMembers()
  }

  private fun GeneratorAdapter.invokeConstructor(provisionPoint: ProvisionPoint.Constructor) {
    newInstance(provisionPoint.containerType)
    dup()
    loadArguments(provisionPoint)
    val method = provisionPoint.method.toMethodDescriptor()
    invokeConstructor(provisionPoint.containerType, method)
  }

  private fun GeneratorAdapter.provideFromMethod(provisionPoint: ProvisionPoint.Method) {
    loadThis()
    getField(provider.type, MODULE_FIELD_NAME, provisionPoint.containerType)
    loadArguments(provisionPoint)
    invokeVirtual(provisionPoint.containerType, provisionPoint.method.toMethodDescriptor())

    if (provider.dependency.type.rawType.isPrimitive) {
      return
    }

    val resultIsNullLabel = newLabel()
    dup()
    ifNonNull(resultIsNullLabel)
    throwException(NULL_POINTER_EXCEPTION_TYPE, "Provider method returned null")

    visitLabel(resultIsNullLabel)
  }

  private fun GeneratorAdapter.loadArguments(provisionPoint: ProvisionPoint.AbstractMethod) {
    val injectees = provisionPoint.injectionPoint.injectees
    injectees.forEach { loadArgument(it) }
  }

  private fun GeneratorAdapter.loadArgument(injectee: Injectee) {
    loadThis()
    getField(provider.type, INJECTOR_FIELD)
    getDependency(keyRegistry, injectee)
  }

  private fun GeneratorAdapter.injectMembers() {
    dup()
    loadThis()
    getField(provider.type, INJECTOR_FIELD)
    swap()
    invokeInterface(Types.INJECTOR_TYPE, INJECT_MEMBERS_METHOD)
  }

  private fun GeneratorAdapter.provideFromBinding(binding: Binding) {
    loadThis()
    getField(provider.type, INJECTOR_FIELD)
    getInstance(keyRegistry, binding.dependency)
    checkCast(binding.ancestor.type.rawType)
  }

  private fun GeneratorAdapter.provideFactory(factory: Factory) {
    invokeConstructorWithInjector(factory.implementationType)
  }

  private fun GeneratorAdapter.provideContract(contract: Contract) {
    invokeConstructorWithInjector(contract.implementationType)
  }

  private fun GeneratorAdapter.invokeConstructorWithInjector(type: Type.Object) {
    newInstance(type)
    dup()
    loadThis()
    getField(provider.type, INJECTOR_FIELD)
    invokeConstructor(type, CONSTRUCTOR_WITH_INJECTOR)
  }

  private fun GeneratorAdapter.provideFromContractProvisionPoint(
    contractType: Type.Object,
    isLazy: Boolean,
    contractProvisionPoint: ContractProvisionPoint) {
    loadThis()
    getField(provider.type, MODULE_FIELD_NAME, if (isLazy) Types.LAZY_TYPE else contractType)

    if (isLazy) {
      invokeInterface(Types.LAZY_TYPE, GET_METHOD)
    }

    invokeInterface(contractProvisionPoint.container, contractProvisionPoint.method.toMethodDescriptor())

    if (provider.dependency.type.rawType.isPrimitive) {
      return
    }

    val resultIsNullLabel = newLabel()
    dup()
    ifNonNull(resultIsNullLabel)
    throwException(NULL_POINTER_EXCEPTION_TYPE, "Provider method returned null")

    visitLabel(resultIsNullLabel)

    exhaustive(
      when (val converter = contractProvisionPoint.injectee.converter) {
        is Converter.Identity -> invokeInterface(Types.PROVIDER_TYPE, GET_METHOD)
        is Converter.Instance -> Unit
        is Converter.Adapter ->
          if (converter.adapterType == LightsaberTypes.LAZY_ADAPTER_TYPE) {
            invokeInterface(Types.LAZY_TYPE, GET_METHOD)
          } else {
            error("Cannot import contract's dependency with adapter ${converter.adapterType.className}")
          }
      }
    )
  }

  companion object {
    private const val MODULE_FIELD_NAME = "module"

    private val NULL_POINTER_EXCEPTION_TYPE = getObjectType<NullPointerException>()

    private val INJECTOR_FIELD = FieldDescriptor("injector", Types.INJECTOR_TYPE)

    private val CONSTRUCTOR_WITH_INJECTOR = MethodDescriptor.forConstructor(Types.INJECTOR_TYPE)

    private val GET_METHOD =
      MethodDescriptor.forMethod("get", Types.OBJECT_TYPE)
    private val INJECT_MEMBERS_METHOD =
      MethodDescriptor.forMethod("injectMembers", Type.Primitive.Void, Types.OBJECT_TYPE)
  }
}
