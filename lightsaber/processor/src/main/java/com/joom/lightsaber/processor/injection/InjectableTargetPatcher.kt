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

import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.getObjectTypeByInternalName
import com.joom.lightsaber.LightsaberTypes
import com.joom.lightsaber.processor.commons.GeneratorAdapter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.invokeMethod
import com.joom.lightsaber.processor.commons.newMethod
import com.joom.lightsaber.processor.commons.toFieldDescriptor
import com.joom.lightsaber.processor.descriptors.MethodDescriptor
import com.joom.lightsaber.processor.generation.getDependency
import com.joom.lightsaber.processor.generation.model.KeyRegistry
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.InjectionTarget
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes.ACC_PUBLIC

class InjectableTargetPatcher(
  classVisitor: ClassVisitor,
  private val keyRegistry: KeyRegistry,
  private val injectableTarget: InjectionTarget,
  private val hasSuperMembersInjector: Boolean
) : BaseInjectionClassVisitor(classVisitor) {

  private val fields: Collection<InjectionPoint.Field>
  private val methods: Collection<InjectionPoint.Method>

  private var isMembersInjector = false
  private var superType: Type.Object? = null

  init {
    val fields = ArrayList<InjectionPoint.Field>()
    val methods = ArrayList<InjectionPoint.Method>()

    injectableTarget.injectionPoints.forEach {
      when (it) {
        is InjectionPoint.Field -> fields += it
        is InjectionPoint.Method -> methods += it
      }
    }

    this.fields = fields
    this.methods = methods
  }

  override fun visit(
    version: Int, access: Int, name: String, signature: String?, superName: String?,
    interfaces: Array<String>?
  ) {
    val membersInjectorType = LightsaberTypes.MEMBERS_INJECTOR_TYPE.internalName
    if (interfaces == null || membersInjectorType !in interfaces) {
      val newInterfaces = if (interfaces == null) arrayOf(membersInjectorType) else interfaces + membersInjectorType
      super.visit(version, access, name, signature, superName, newInterfaces)
      superType = if (hasSuperMembersInjector && superName != null) getObjectTypeByInternalName(superName) else null
      isDirty = true
    } else {
      super.visit(version, access, name, signature, superName, interfaces)
      isMembersInjector = true
    }
  }

  override fun visitEnd() {
    if (!isMembersInjector) {
      newMethod(ACC_PUBLIC, INJECT_FIELDS_METHOD) { injectFields(fields) }
      newMethod(ACC_PUBLIC, INJECT_METHODS_METHOD) { injectMethods(methods) }
    }
    super.visitEnd()
  }

  private fun GeneratorAdapter.injectFields(fields: Collection<InjectionPoint.Field>) {
    superType?.let {
      loadThis()
      loadArg(0)
      invokeSuper(it, INJECT_FIELDS_METHOD)
    }
    fields.forEach { injectField(it) }
  }

  private fun GeneratorAdapter.injectField(field: InjectionPoint.Field) {
    loadThis()
    loadArg(0)
    getDependency(keyRegistry, field.injectee)
    putField(injectableTarget.type, field.field.toFieldDescriptor())
  }

  private fun GeneratorAdapter.injectMethods(methods: Collection<InjectionPoint.Method>) {
    superType?.let {
      loadThis()
      loadArg(0)
      invokeSuper(it, INJECT_METHODS_METHOD)
    }
    methods.forEach { injectMethod(it) }
  }

  private fun GeneratorAdapter.injectMethod(method: InjectionPoint.Method) {
    loadThis()
    method.injectees.forEach { injectee ->
      loadArg(0)
      getDependency(keyRegistry, injectee)
    }
    invokeMethod(injectableTarget.type, method.method)
  }

  companion object {
    private val INJECT_FIELDS_METHOD =
      MethodDescriptor.forMethod("injectFields", Type.Primitive.Void, Types.INJECTOR_TYPE)
    private val INJECT_METHODS_METHOD =
      MethodDescriptor.forMethod("injectMethods", Type.Primitive.Void, Types.INJECTOR_TYPE)
  }
}
