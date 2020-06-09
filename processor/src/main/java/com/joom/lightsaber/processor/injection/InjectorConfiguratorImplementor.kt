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
import com.joom.lightsaber.processor.commons.GeneratorAdapter
import com.joom.lightsaber.processor.commons.invokeMethod
import com.joom.lightsaber.processor.commons.newMethod
import com.joom.lightsaber.processor.commons.toFieldDescriptor
import com.joom.lightsaber.processor.commons.toMethodDescriptor
import com.joom.lightsaber.processor.descriptors.MethodDescriptor
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.ImportPoint
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.isStatic
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

class InjectorConfiguratorImplementor(
  private val classVisitor: ClassVisitor,
  private val containerType: Type.Object
) {

  fun implementInjectorConfigurator(imports: Collection<Import>, configurator: GeneratorAdapter.() -> Unit = {}) {
    classVisitor.newMethod(Opcodes.ACC_PUBLIC, CONFIGURE_INJECTOR_METHOD) {
      configurator()
      configureInjector(imports)
    }
  }

  private fun GeneratorAdapter.configureInjector(imports: Collection<Import>) {
    imports.forEach { configureInjectorWithModule(it) }
  }

  private fun GeneratorAdapter.configureInjectorWithModule(import: Import) {
    loadModule(import.importPoint)
    // TODO: It would be better to throw ConfigurationException here.
    checkCast(LightsaberTypes.INJECTOR_CONFIGURATOR_TYPE)
    loadArg(0)
    invokeInterface(LightsaberTypes.INJECTOR_CONFIGURATOR_TYPE, CONFIGURE_INJECTOR_METHOD)
  }

  private fun GeneratorAdapter.loadModule(importPoint: ImportPoint) {
    return when (importPoint) {
      is ImportPoint.Method -> loadModule(importPoint)
      is ImportPoint.Field -> loadModule(importPoint)
      is ImportPoint.Inverse -> loadModule(importPoint)
    }
  }

  private fun GeneratorAdapter.loadModule(importPoint: ImportPoint.Method) {
    if (!importPoint.method.isStatic) {
      loadThis()
      invokeMethod(containerType, importPoint.method)
    } else {
      invokeStatic(containerType, importPoint.method.toMethodDescriptor())
    }
  }

  private fun GeneratorAdapter.loadModule(importPoint: ImportPoint.Field) {
    if (!importPoint.field.isStatic) {
      loadThis()
      getField(containerType, importPoint.field.toFieldDescriptor())
    } else {
      getStatic(containerType, importPoint.field.toFieldDescriptor())
    }
  }

  private fun GeneratorAdapter.loadModule(importPoint: ImportPoint.Inverse) {
    newInstance(importPoint.importeeType)
    dup()
    invokeConstructor(importPoint.importeeType, MethodDescriptor.forDefaultConstructor())
  }

  companion object {
    private val CONFIGURE_INJECTOR_METHOD =
      MethodDescriptor.forMethod("configureInjector", Type.Primitive.Void, LightsaberTypes.LIGHTSABER_INJECTOR_TYPE)
  }
}
