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

import com.joom.grip.FileRegistry
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.getObjectTypeByInternalName
import com.joom.lightsaber.processor.commons.rawType
import com.joom.lightsaber.processor.model.Binding
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ContractProvisionPoint
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint

interface ProviderFactory {
  fun createProvidersForModule(module: Module): Collection<Provider>
  fun createProvidersForContract(contract: Contract, isLazy: Boolean): Collection<Provider>
}

class ProviderFactoryImpl(
  private val fileRegistry: FileRegistry,
  private val projectName: String
) : ProviderFactory {

  private val typeRegistry = mutableSetOf<Type.Object>()

  private val providersByModuleType = mutableMapOf<Type.Object, Collection<Provider>>()
  private val providerByProvidableTargetType = mutableMapOf<Type.Object, Provider>()
  private val providerByBinding = mutableMapOf<Binding, Provider>()
  private val providerByFactoryType = mutableMapOf<Type.Object, Provider>()
  private val providerByContractType = mutableMapOf<Type.Object, Provider>()
  private val providersByImportedContractType = mutableMapOf<Type.Object, Collection<Provider>>()

  override fun createProvidersForModule(module: Module): Collection<Provider> {
    return providersByModuleType.getOrPut(module.type) {
      val providers = ArrayList<Provider>()
      module.provisionPoints.mapTo(providers) { newProviderForProvisionPoint(it) }
      module.bindings.mapTo(providers) { newBindingProvider(it) }
      module.factories.mapTo(providers) { newFactoryProvider(it) }
      module.contracts.mapTo(providers) { newContractProvider(it) }
      providers
    }
  }

  override fun createProvidersForContract(contract: Contract, isLazy: Boolean): Collection<Provider> {
    return providersByImportedContractType.getOrPut(contract.type) {
      contract.provisionPoints.map { provisionPoint ->
        newContractProvisionPointProvider(contract, provisionPoint, isLazy)
      }
    }
  }

  private fun newProviderForProvisionPoint(provisionPoint: ProvisionPoint): Provider {
    return when (provisionPoint) {
      is ProvisionPoint.Constructor -> newConstructorProvider(provisionPoint)
      is ProvisionPoint.Method -> newMethodProvider(provisionPoint)
      is ProvisionPoint.Field -> newFieldProvider(provisionPoint)
    }
  }

  private fun newConstructorProvider(provisionPoint: ProvisionPoint.Constructor): Provider {
    val dependencyType = provisionPoint.containerType
    return providerByProvidableTargetType.getOrPut(dependencyType) {
      val providerType = getObjectTypeByUniqueInternalName("${dependencyType.internalName}\$ConstructorProvider%d\$$projectName")
      Provider(providerType, ProviderMedium.ProvisionPoint(provisionPoint))
    }
  }

  private fun newMethodProvider(provisionPoint: ProvisionPoint.Method): Provider {
    val moduleType = provisionPoint.containerType
    val providerType = getObjectTypeByUniqueInternalName("${moduleType.internalName}\$MethodProvider%d\$$projectName")
    return Provider(providerType, ProviderMedium.ProvisionPoint(provisionPoint))
  }

  private fun newFieldProvider(provisionPoint: ProvisionPoint.Field): Provider {
    val moduleType = provisionPoint.containerType
    val providerType = getObjectTypeByUniqueInternalName("${moduleType.internalName}\$FieldProvider%d\$$projectName")
    return Provider(providerType, ProviderMedium.ProvisionPoint(provisionPoint))
  }

  private fun newBindingProvider(binding: Binding): Provider {
    return providerByBinding.getOrPut(binding) {
      val dependencyType = binding.dependency.type.rawType as Type.Object
      val providerType = getObjectTypeByUniqueInternalName("${dependencyType.internalName}\$BindingProvider%d\$$projectName")
      Provider(providerType, ProviderMedium.Binding(binding))
    }
  }

  private fun newFactoryProvider(factory: Factory): Provider {
    return providerByFactoryType.getOrPut(factory.type) {
      val providerType = getObjectTypeByUniqueInternalName("${factory.type.internalName}\$FactoryProvider%d\$$projectName")
      Provider(providerType, ProviderMedium.Factory(factory))
    }
  }

  private fun newContractProvider(contract: Contract): Provider {
    return providerByContractType.getOrPut(contract.type) {
      val providerType = getObjectTypeByUniqueInternalName("${contract.type.internalName}\$ContractProvider%d\$$projectName")
      Provider(providerType, ProviderMedium.Contract(contract))
    }
  }

  private fun newContractProvisionPointProvider(
    contract: Contract,
    contractProvisionPoint: ContractProvisionPoint,
    isLazy: Boolean
  ): Provider {
    val providerType = getObjectTypeByUniqueInternalName("${contract.type.internalName}\$MethodProvider%d\$$projectName")
    return Provider(providerType, ProviderMedium.ContractProvisionPoint(contract.type, isLazy, contractProvisionPoint))
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
