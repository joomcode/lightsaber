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
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.mirrors.MethodMirror
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.signature.GenericType
import com.joom.lightsaber.processor.commons.GeneratorAdapter
import com.joom.lightsaber.processor.commons.StandaloneClassWriter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.newMethod
import com.joom.lightsaber.processor.commons.toMethodDescriptor
import com.joom.lightsaber.processor.descriptors.FieldDescriptor
import com.joom.lightsaber.processor.descriptors.MethodDescriptor
import com.joom.lightsaber.processor.generation.model.KeyRegistry
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.FactoryInjectee
import com.joom.lightsaber.processor.model.FactoryProvisionPoint
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.watermark.WatermarkClassVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_SUPER
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC
import org.objectweb.asm.Opcodes.V1_6

class FactoryClassGenerator(
  private val classRegistry: ClassRegistry,
  private val keyRegistry: KeyRegistry,
  private val injectionContext: InjectionContext,
  private val factory: Factory
) {

  fun generate(): ByteArray {
    val classWriter = StandaloneClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS, classRegistry)
    val classVisitor = WatermarkClassVisitor(classWriter, true)
    classVisitor.visit(
      V1_6,
      ACC_PUBLIC or ACC_SUPER,
      factory.implementationType.internalName,
      null,
      Types.OBJECT_TYPE.internalName,
      arrayOf(factory.type.internalName)
    )

    generateFields(classVisitor)
    generateConstructor(classVisitor)
    generateMethods(classVisitor)
    generateBridges(classVisitor)

    classVisitor.visitEnd()
    return classWriter.toByteArray()
  }

  private fun generateFields(classVisitor: ClassVisitor) {
    generateInjectorField(classVisitor)
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
    classVisitor.newMethod(ACC_PUBLIC, CONSTRUCTOR) {
      loadThis()
      invokeConstructor(Types.OBJECT_TYPE, MethodDescriptor.forDefaultConstructor())

      loadThis()
      loadArg(0)
      putField(factory.implementationType, INJECTOR_FIELD)
    }
  }

  private fun generateMethods(classVisitor: ClassVisitor) {
    factory.provisionPoints.forEach { provisionPoint ->
      classVisitor.newMethod(ACC_PUBLIC, provisionPoint.method.toMethodDescriptor()) {
        newProvisionPoint(provisionPoint)
      }
    }
  }

  private fun generateBridges(classVisitor: ClassVisitor) {
    val hashToReturnType = HashMap<BridgeHash, GenericType>()
    val hashToBridgeTypes = HashMap<BridgeHash, HashSet<GenericType>>()
    val hashToBridgeMethods = HashMap<BridgeHash, ArrayList<MethodMirror>>()

    factory.provisionPoints.forEach { provisionPoint ->
      hashToReturnType[provisionPoint.method.toBridgeHash()] = provisionPoint.method.signature.returnType
    }

    fun collectBridges(mirror: ClassMirror) {
      mirror.methods.forEach { method ->
        val hash = method.toBridgeHash()
        val returnType = hashToReturnType[hash] ?: return@forEach

        if (method.signature.returnType != returnType) {
          if (hashToBridgeTypes.getOrPut(hash) { HashSet() }.add(method.signature.returnType)) {
            hashToBridgeMethods.getOrPut(hash) { ArrayList() }.add(method)
          }
        }
      }

      mirror.interfaces.forEach { parent ->
        collectBridges(classRegistry.getClassMirror(parent))
      }
    }

    classRegistry.getClassMirror(factory.type).interfaces.forEach { parent ->
      collectBridges(classRegistry.getClassMirror(parent))
    }

    factory.provisionPoints.forEach { provisionPoint ->
      hashToBridgeMethods[provisionPoint.method.toBridgeHash()].orEmpty().forEach { bridge ->
        generateBridge(classVisitor, bridge, provisionPoint)
      }
    }
  }

  private fun generateBridge(classVisitor: ClassVisitor, from: MethodMirror, provisionPoint: FactoryProvisionPoint) {
    classVisitor.newMethod(ACC_PUBLIC or ACC_SYNTHETIC, from.toMethodDescriptor()) {
      loadThis()
      loadArgs()
      invokeInterface(provisionPoint.containerType, provisionPoint.method.toMethodDescriptor())
    }
  }

  private data class BridgeHash(
    val name: String,
    val parameters: List<GenericType>
  )

  private fun MethodMirror.toBridgeHash(): BridgeHash {
    return BridgeHash(
      name = name,
      // MethodSignatureMirror returns a list without equals/hashCode implementation so we need to make copy
      parameters = signature.parameterTypes.toList()
    )
  }


  private fun GeneratorAdapter.newProvisionPoint(provisionPoint: FactoryProvisionPoint) {
    val dependencyType = provisionPoint.injectionPoint.containerType
    newInstance(dependencyType)
    dup()
    provisionPoint.injectionPoint.injectees.forEach { loadArgument(it) }
    invokeConstructor(dependencyType, provisionPoint.injectionPoint.method.toMethodDescriptor())
    if (dependencyType != provisionPoint.method.type.returnType) {
      checkCast(provisionPoint.method.type.returnType)
    }

    injectionContext.findInjectableTargetByType(dependencyType)?.also {
      injectMembers()
    }
  }

  private fun GeneratorAdapter.loadArgument(injectee: FactoryInjectee) {
    return when (injectee) {
      is FactoryInjectee.FromInjector -> loadArgumentFromInjector(injectee)
      is FactoryInjectee.FromMethod -> loadArgumentFromMethod(injectee)
    }
  }

  private fun GeneratorAdapter.loadArgumentFromInjector(injectee: FactoryInjectee.FromInjector) {
    loadThis()
    getField(factory.implementationType, INJECTOR_FIELD)
    getDependency(keyRegistry, injectee.injectee)
  }

  private fun GeneratorAdapter.loadArgumentFromMethod(injectee: FactoryInjectee.FromMethod) {
    loadArg(injectee.argumentIndex)
  }

  private fun GeneratorAdapter.injectMembers() {
    dup()
    loadThis()
    getField(factory.implementationType, INJECTOR_FIELD)
    swap()
    invokeInterface(Types.INJECTOR_TYPE, INJECT_MEMBERS_METHOD)
  }

  companion object {
    private val INJECTOR_FIELD = FieldDescriptor("injector", Types.INJECTOR_TYPE)
    private val CONSTRUCTOR = MethodDescriptor.forConstructor(Types.INJECTOR_TYPE)

    private val INJECT_MEMBERS_METHOD =
      MethodDescriptor.forMethod("injectMembers", Type.Primitive.Void, Types.OBJECT_TYPE)
  }
}
