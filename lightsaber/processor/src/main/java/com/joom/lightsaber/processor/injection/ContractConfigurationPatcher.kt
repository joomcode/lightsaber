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

package com.joom.lightsaber.processor.injection

import com.joom.lightsaber.LightsaberTypes
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.newMethod
import com.joom.lightsaber.processor.descriptors.MethodDescriptor
import com.joom.lightsaber.processor.generation.getInstance
import com.joom.lightsaber.processor.generation.model.KeyRegistry
import com.joom.lightsaber.processor.model.ContractConfiguration
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes.ACC_PUBLIC

class ContractConfigurationPatcher(
  classVisitor: ClassVisitor,
  private val keyRegistry: KeyRegistry,
  private val contractConfiguration: ContractConfiguration
) : BaseInjectionClassVisitor(classVisitor) {

  private var isContractCreator = false

  override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
    val contractCreatorType = LightsaberTypes.CONTRACT_CREATOR_TYPE.internalName
    if (interfaces == null || contractCreatorType !in interfaces) {
      val newInterfaces =
        if (interfaces == null) arrayOf(contractCreatorType) else interfaces + contractCreatorType
      super.visit(version, access, name, signature, superName, newInterfaces)
      isDirty = true
    } else {
      super.visit(version, access, name, signature, superName, interfaces)
      isContractCreator = true
    }
  }

  override fun visitEnd() {
    if (!isContractCreator) {
      implementContractCreator()
    }
    super.visitEnd()
  }

  private fun implementContractCreator() {
    newMethod(ACC_PUBLIC, CREATE_CONTRACT_METHOD) {
      loadArg(0)
      getInstance(keyRegistry, contractConfiguration.contract.dependency)
    }
  }

  companion object {
    private val CREATE_CONTRACT_METHOD = MethodDescriptor.forMethod("createContract", Types.OBJECT_TYPE, Types.INJECTOR_TYPE)
  }
}
