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

import com.joom.grip.mirrors.FieldMirror
import com.joom.grip.mirrors.MethodMirror
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.isStatic
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
import com.joom.lightsaber.processor.generation.getInstance
import com.joom.lightsaber.processor.generation.model.GenerationContext
import com.joom.lightsaber.processor.generation.model.Provider
import com.joom.lightsaber.processor.generation.model.moduleType
import com.joom.lightsaber.processor.generation.registerProvider
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.ImportPoint
import com.joom.lightsaber.processor.model.InjectionContext
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_SYNTHETIC

class ModulePatcher(
  classVisitor: ClassVisitor,
  private val injectionContext: InjectionContext,
  private val generationContext: GenerationContext,
  private val module: Module
) : BaseInjectionClassVisitor(classVisitor) {

  private val keyRegistry = generationContext.keyRegistry

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
      implementInjectorConfigurator()
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

  private fun implementInjectorConfigurator() {
    newMethod(ACC_PUBLIC, CONFIGURE_INJECTOR_METHOD) {
      registerProviders()
      configureInjector()

      if (injectionContext.findComponentByType(module.type) != null || injectionContext.findContractConfigurationByType(module.type) != null) {
        instantiateEagerDependencies()
      }
    }
  }

  private fun GeneratorAdapter.registerProviders() {
    generationContext.findProvidersByModuleType(module.type).forEach { provider ->
      loadArg(0)
      registerProvider(keyRegistry, provider) {
        val moduleType = provider.moduleType
        if (moduleType != null) {
          newModuleProvider(provider, moduleType)
        } else {
          newConstructorProvider(provider)
        }
      }
    }
  }

  private fun GeneratorAdapter.newModuleProvider(provider: Provider, moduleType: Type.Object) {
    newInstance(provider.type)
    dup()
    loadThis()
    loadArg(0)
    val constructor = MethodDescriptor.forConstructor(moduleType, Types.INJECTOR_TYPE)
    invokeConstructor(provider.type, constructor)
  }

  private fun GeneratorAdapter.newConstructorProvider(provider: Provider) {
    newInstance(provider.type)
    dup()
    loadArg(0)
    val constructor = MethodDescriptor.forConstructor(Types.INJECTOR_TYPE)
    invokeConstructor(provider.type, constructor)
  }

  private fun GeneratorAdapter.newContractImportProvider(provider: Provider, import: Import.Contract) {
    newInstance(provider.type)
    dup()
    loadModule(import.importPoint)
    loadArg(0)

    val constructor = when (val converter = import.importPoint.converter) {
      is ImportPoint.Converter.Adapter -> MethodDescriptor.forConstructor(converter.adapterType, Types.INJECTOR_TYPE)
      is ImportPoint.Converter.Instance -> MethodDescriptor.forConstructor(import.contract.type, Types.INJECTOR_TYPE)
    }

    invokeConstructor(provider.type, constructor)
  }

  private fun GeneratorAdapter.configureInjector() {
    module.imports.forEach { configureInjectorWithImport(it) }
  }

  private fun GeneratorAdapter.configureInjectorWithImport(import: Import) {
    return when (import) {
      is Import.Module -> configureInjectorWithModule(import)
      is Import.Contract -> configureInjectorWithContract(import)
    }
  }

  private fun GeneratorAdapter.configureInjectorWithModule(import: Import.Module) {
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
      is ImportPoint.Annotation -> loadModule(importPoint)
    }
  }

  private fun GeneratorAdapter.loadModule(importPoint: ImportPoint.Method) {
    if (!importPoint.method.isStatic) {
      loadThis()
      invokeMethod(module.type, importPoint.method)
    } else {
      invokeStatic(module.type, importPoint.method.toMethodDescriptor())
    }
  }

  private fun GeneratorAdapter.loadModule(importPoint: ImportPoint.Field) {
    if (!importPoint.field.isStatic) {
      loadThis()
      getField(module.type, importPoint.field.toFieldDescriptor())
    } else {
      getStatic(module.type, importPoint.field.toFieldDescriptor())
    }
  }

  private fun GeneratorAdapter.loadModule(importPoint: ImportPoint.Annotation) {
    newInstance(importPoint.importeeType)
    dup()
    invokeConstructor(importPoint.importeeType, MethodDescriptor.forDefaultConstructor())
  }

  private fun GeneratorAdapter.configureInjectorWithContract(import: Import.Contract) {
    generationContext.findProvidersByContractType(import.contract.type).forEach { provider ->
      loadArg(0)

      registerProvider(keyRegistry, provider) {
        newContractImportProvider(provider, import)
      }
    }
  }

  private fun GeneratorAdapter.instantiateEagerDependencies() {
    for (module in module.getModulesWithDescendants()) {
      for (provisionPoint in module.provisionPoints) {
        if (provisionPoint.scope.isEager) {
          loadArg(0)
          getInstance(keyRegistry, provisionPoint.dependency)
        }
      }
    }
  }

  companion object {
    private val CONFIGURE_INJECTOR_METHOD =
      MethodDescriptor.forMethod("configureInjector", Type.Primitive.Void, LightsaberTypes.LIGHTSABER_INJECTOR_TYPE)
  }
}
