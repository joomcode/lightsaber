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
import com.joom.lightsaber.processor.commons.GeneratorAdapter
import com.joom.lightsaber.processor.commons.StandaloneClassWriter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.newMethod
import com.joom.lightsaber.processor.commons.toMethodDescriptor
import com.joom.lightsaber.processor.descriptors.FieldDescriptor
import com.joom.lightsaber.processor.descriptors.MethodDescriptor
import com.joom.lightsaber.processor.generation.model.KeyRegistry
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ContractProvisionPoint
import com.joom.lightsaber.processor.watermark.WatermarkClassVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_SUPER
import org.objectweb.asm.Opcodes.V1_6

class ContractClassGenerator(
  private val classRegistry: ClassRegistry,
  private val keyRegistry: KeyRegistry,
  private val contract: Contract
) {

  fun generate(): ByteArray {
    val classWriter = StandaloneClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS, classRegistry)
    val classVisitor = WatermarkClassVisitor(classWriter, true)
    classVisitor.visit(
      V1_6,
      ACC_PUBLIC or ACC_SUPER,
      contract.implementationType.internalName,
      null,
      Types.OBJECT_TYPE.internalName,
      arrayOf(contract.type.internalName)
    )

    generateFields(classVisitor)
    generateConstructor(classVisitor)
    generateMethods(classVisitor)

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
      putField(contract.implementationType, INJECTOR_FIELD)
    }
  }

  private fun generateMethods(classVisitor: ClassVisitor) {
    contract.provisionPoints.forEach { provisionPoint ->
      classVisitor.newMethod(ACC_PUBLIC, provisionPoint.method.toMethodDescriptor()) {
        newProvisionPoint(provisionPoint)
      }
    }
  }

  private fun GeneratorAdapter.newProvisionPoint(provisionPoint: ContractProvisionPoint) {
    loadThis()
    getField(contract.implementationType, INJECTOR_FIELD)
    getDependency(keyRegistry, provisionPoint.injectee)
  }

  companion object {
    private val INJECTOR_FIELD = FieldDescriptor("injector", Types.INJECTOR_TYPE)
    private val CONSTRUCTOR = MethodDescriptor.forConstructor(Types.INJECTOR_TYPE)
  }
}
