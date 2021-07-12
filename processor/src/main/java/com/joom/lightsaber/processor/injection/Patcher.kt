/*
 * Copyright 2021 SIA Joom
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

import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.generation.model.GenerationContext
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.InjectionTarget
import io.michaelrocks.grip.ClassRegistry
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.getObjectTypeByInternalName
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

class Patcher(
  classVisitor: ClassVisitor,
  private val classRegistry: ClassRegistry,
  private val injectionContext: InjectionContext,
  private val generationContext: GenerationContext
) : ClassVisitor(Opcodes.ASM9, classVisitor) {

  private val keyRegistry get() = generationContext.keyRegistry

  override fun visit(
    version: Int,
    access: Int,
    name: String,
    signature: String?,
    superName: String?,
    interfaces: Array<String>?
  ) {
    val type = getObjectTypeByInternalName(name)

    injectionContext.findContractConfigurationByType(type)?.also {
      cv = ContractConfigurationPatcher(cv, keyRegistry, it)
    }

    injectionContext.findModuleByType(type)?.also {
      cv = ModulePatcher(cv, injectionContext, generationContext, it)
    }

    injectionContext.findInjectableTargetByType(type)?.also {
      cv = InjectableTargetPatcher(cv, keyRegistry, it, it.hasSuperMembersInjector())
    }

    injectionContext.findProvidableTargetByType(type)?.also {
      cv = ProvidableTargetPatcher(cv, it)
    }

    injectionContext.findFactoryInjectionPointByType(type)?.also {
      cv = FactoryInjectionPointPatcher(cv, it)
    }

    super.visit(version, access, name, signature, superName, interfaces)
  }

  private fun InjectionTarget.hasSuperMembersInjector(): Boolean {
    return findSuperInjectableTarget(type) != null
  }

  private tailrec fun findSuperInjectableTarget(type: Type.Object): InjectionTarget? {
    val superType = classRegistry.getClassMirror(type).superType ?: return null
    if (superType == Types.OBJECT_TYPE) {
      return null
    }

    return injectionContext.findInjectableTargetByType(superType) ?: findSuperInjectableTarget(superType)
  }
}
