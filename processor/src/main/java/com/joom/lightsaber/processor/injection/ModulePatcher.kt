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
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.exhaustive
import com.joom.lightsaber.processor.commons.invokeMethod
import com.joom.lightsaber.processor.commons.newMethod
import com.joom.lightsaber.processor.commons.toFieldDescriptor
import com.joom.lightsaber.processor.commons.toMethodDescriptor
import com.joom.lightsaber.processor.descriptors.FieldDescriptor
import com.joom.lightsaber.processor.descriptors.MethodDescriptor
import com.joom.lightsaber.processor.generation.model.KeyRegistry
import com.joom.lightsaber.processor.generation.model.Provider
import com.joom.lightsaber.processor.generation.model.requiresModule
import com.joom.lightsaber.processor.generation.registerProvider
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint
import io.michaelrocks.grip.mirrors.FieldMirror
import io.michaelrocks.grip.mirrors.MethodMirror
import io.michaelrocks.grip.mirrors.isStatic
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC

class ModulePatcher(
  classVisitor: ClassVisitor,
  private val keyRegistry: KeyRegistry,
  private val module: Module,
  private val providers: Collection<Provider>
) : BaseInjectionClassVisitor(classVisitor) {

  private val providableFields = mutableSetOf<FieldDescriptor>()
  private val providableMethods = mutableSetOf<MethodDescriptor>()

  private var isInjectorConfigurator = false

  init {
    for (provisionPoint in module.provisionPoints) {
      exhaustive(
        when (provisionPoint) {
          is ProvisionPoint.Field -> providableFields.add(provisionPoint.field.toFieldDescriptor())
          is ProvisionPoint.Constructor -> providableMethods.add(provisionPoint.method.toMethodDescriptor())
          is ProvisionPoint.Method -> providableMethods.add(provisionPoint.method.toMethodDescriptor())
          is ProvisionPoint.Binding -> Unit
        }
      )
    }
  }

  override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<String>?) {
    val injectorConfiguratorType = LightsaberTypes.INJECTOR_CONFIGURATOR_TYPE.internalName
    if (interfaces == null || injectorConfiguratorType !in interfaces) {
      val newInterfaces =
        if (interfaces == null) arrayOf(injectorConfiguratorType) else interfaces + injectorConfiguratorType
      super.visit(version, access, name, signature, superName, newInterfaces)
      isDirty = true
    } else {
      super.visit(version, access, name, signature, superName, interfaces)
      isInjectorConfigurator = true
    }
  }

  override fun visitEnd() {
    if (!isInjectorConfigurator) {
      generateBridges()
      InjectorConfiguratorImplementor(this, module.type).implementInjectorConfigurator(module.imports) {
        registerProviders()
      }
    }
    super.visitEnd()
  }

  private fun generateBridges() {
    module.provisionPoints.forEach { provisionPoint ->
      val bridge = provisionPoint.bridge
      if (bridge != null) {
        newMethod(ACC_PUBLIC or ACC_SYNTHETIC, bridge.method.toMethodDescriptor()) {
          generateBridge(provisionPoint)
        }
      }
    }
  }

  private fun GeneratorAdapter.generateBridge(provisionPoint: ProvisionPoint) {
    return when (provisionPoint) {
      is ProvisionPoint.Field -> getBridgedField(provisionPoint.field)
      is ProvisionPoint.Method -> invokeBridgedMethod(provisionPoint.method)
      else -> error("Unexpected provision point $provisionPoint for bridge method")
    }
  }

  private fun GeneratorAdapter.getBridgedField(field: FieldMirror) {
    loadThis()
    getField(module.type, field.toFieldDescriptor())
  }

  private fun GeneratorAdapter.invokeBridgedMethod(method: MethodMirror) {
    if (method.isStatic) {
      loadArgs()
      invokeStatic(module.type, method.toMethodDescriptor())
      return
    }

    loadThis()
    loadArgs()
    invokeMethod(module.type, method)
  }

  private fun GeneratorAdapter.registerProviders() {
    providers.forEach { provider ->
      loadArg(0)
      registerProvider(keyRegistry, provider) {
        if (!provider.requiresModule) {
          newConstructorProvider(provider)
        } else {
          newModuleProvider(provider)
        }
      }
    }
  }

  private fun GeneratorAdapter.newModuleProvider(provider: Provider) {
    newInstance(provider.type)
    dup()
    loadThis()
    loadArg(0)
    val constructor = MethodDescriptor.forConstructor(provider.moduleType, Types.INJECTOR_TYPE)
    invokeConstructor(provider.type, constructor)
  }

  private fun GeneratorAdapter.newConstructorProvider(provider: Provider) {
    newInstance(provider.type)
    dup()
    loadArg(0)
    val constructor = MethodDescriptor.forConstructor(Types.INJECTOR_TYPE)
    invokeConstructor(provider.type, constructor)
  }
}
