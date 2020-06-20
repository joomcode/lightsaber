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

import com.joom.lightsaber.processor.commons.rawType
import com.joom.lightsaber.processor.model.Binding
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ContractProvisionPoint
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.Import
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint
import io.michaelrocks.grip.FileRegistry
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.getObjectTypeByInternalName

interface ProviderFactory {
  fun createProvidersForModule(module: Module): Collection<Provider>
}

class ProviderFactoryImpl(
  private val fileRegistry: FileRegistry,
  private val projectName: String
) : ProviderFactory {

  private val typeRegistry = mutableSetOf<Type.Object>()

  override fun createProvidersForModule(module: Module): Collection<Provider> {
    val providers = ArrayList<Provider>()
    module.provisionPoints.mapTo(providers) { provisionPoint -> newProviderForProvisionPoint(module, provisionPoint) }
    module.bindings.mapTo(providers) { newBindingProvider(module, it) }
    module.factories.mapTo(providers) { newFactoryProvider(module, it) }
    module.contracts.mapTo(providers) { newContractProvider(module, it) }
    module.imports.flatMapTo(providers) { createProvidersForImport(it) }
    return providers
  }

  private fun newProviderForProvisionPoint(module: Module, provisionPoint: ProvisionPoint): Provider {
    return when (provisionPoint) {
      is ProvisionPoint.Constructor -> newConstructorProvider(module, provisionPoint)
      is ProvisionPoint.Method -> newMethodProvider(module, provisionPoint)
      is ProvisionPoint.Field -> newFieldProvider(module, provisionPoint)
    }
  }

  private fun newConstructorProvider(module: Module, provisionPoint: ProvisionPoint.Constructor): Provider {
    val moduleType = provisionPoint.containerType
    val providerType = getObjectTypeByUniqueInternalName("${moduleType.internalName}\$ConstructorProvider%d\$$projectName")
    return Provider(providerType, module.type, ProviderMedium.ProvisionPoint(provisionPoint))
  }

  private fun newMethodProvider(module: Module, provisionPoint: ProvisionPoint.Method): Provider {
    val providerType = getObjectTypeByUniqueInternalName("${module.type.internalName}\$MethodProvider%d\$$projectName")
    return Provider(providerType, module.type, ProviderMedium.ProvisionPoint(provisionPoint))
  }

  private fun newFieldProvider(module: Module, provisionPoint: ProvisionPoint.Field): Provider {
    val providerType = getObjectTypeByUniqueInternalName("${module.type.internalName}\$FieldProvider%d\$$projectName")
    return Provider(providerType, module.type, ProviderMedium.ProvisionPoint(provisionPoint))
  }

  private fun newBindingProvider(module: Module, binding: Binding): Provider {
    val dependencyType = binding.dependency.type.rawType as Type.Object
    val providerType = getObjectTypeByUniqueInternalName("${dependencyType.internalName}\$BindingProvider%d\$$projectName")
    return Provider(providerType, module.type, ProviderMedium.Binding(binding))
  }

  private fun newFactoryProvider(module: Module, factory: Factory): Provider {
    val providerType = getObjectTypeByUniqueInternalName("${factory.type.internalName}\$FactoryProvider%d\$$projectName")
    return Provider(providerType, module.type, ProviderMedium.Factory(factory))
  }

  private fun newContractProvider(module: Module, contract: Contract): Provider {
    val providerType = getObjectTypeByUniqueInternalName("${contract.type.internalName}\$ContractProvider%d\$$projectName")
    return Provider(providerType, module.type, ProviderMedium.Contract(contract))
  }

  private fun createProvidersForImport(import: Import): Collection<Provider> {
    return when (import) {
      is Import.Module -> emptyList()
      is Import.Contract -> createProvidersForContractImport(import)
    }
  }

  private fun createProvidersForContractImport(import: Import.Contract): Collection<Provider> {
    return import.contract.provisionPoints.map { provisionPoint ->
      newContractProvisionPointProvider(import.contract, provisionPoint)
    }
  }

  private fun newContractProvisionPointProvider(contract: Contract, contractProvisionPoint: ContractProvisionPoint): Provider {
    val providerType = getObjectTypeByUniqueInternalName("${contract.type.internalName}\$MethodProvider%d\$$projectName")
    return Provider(providerType, contract.type, ProviderMedium.ContractProvisionPoint(contractProvisionPoint))
  }

  private fun getObjectTypeByUniqueInternalName(pattern: String): Type.Object {
    for (index in 0..Int.MAX_VALUE) {
      val internalName = pattern.format(index)
      if (index == 0 && internalName == pattern) {
        error("Pattern doesn't contain a format placeholder")
      }

      val type = getObjectTypeByInternalName(internalName)
      if (isUniqueType(type)) {
        typeRegistry += type
        return type
      }
    }

    error("Cannot generate a unique name with pattern: $pattern")
  }

  private fun isUniqueType(type: Type.Object): Boolean {
    if (type in typeRegistry) {
      return false
    }

    if (type in fileRegistry) {
      return false
    }

    return true
  }
}
