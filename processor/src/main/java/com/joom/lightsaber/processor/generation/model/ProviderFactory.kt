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
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint
import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.getObjectTypeByInternalName

interface ProviderFactory {
  fun createProvidersForModule(module: Module): Collection<Provider>
}

class ProviderFactoryImpl(
  private val projectName: String
) : ProviderFactory {

  override fun createProvidersForModule(module: Module): Collection<Provider> {
    val providers = ArrayList<Provider>(module.provisionPoints.size + module.factories.size + module.contracts.size)
    module.provisionPoints.mapIndexedTo(providers) { index, provisionPoint -> newProviderForProvisionPoint(module, provisionPoint, index) }
    module.bindings.mapTo(providers) { newBindingProvider(module, it) }
    module.factories.mapTo(providers) { newFactoryProvider(module, it) }
    module.contracts.mapTo(providers) { newContractProvider(module, it) }
    return providers
  }

  private fun newProviderForProvisionPoint(module: Module, provisionPoint: ProvisionPoint, index: Int): Provider {
    return when (provisionPoint) {
      is ProvisionPoint.Constructor -> newConstructorProvider(module, provisionPoint)
      is ProvisionPoint.Method -> newMethodProvider(module, provisionPoint, index)
      is ProvisionPoint.Field -> newFieldProvider(module, provisionPoint, index)
    }
  }

  private fun newConstructorProvider(module: Module, provisionPoint: ProvisionPoint.Constructor): Provider {
    val moduleType = provisionPoint.containerType
    val providerType = getObjectTypeByInternalName("${moduleType.internalName}\$ConstructorProvider\$$projectName")
    return Provider(providerType, module.type, ProviderMedium.ProvisionPoint(provisionPoint))
  }

  private fun newMethodProvider(module: Module, provisionPoint: ProvisionPoint.Method, index: Int): Provider {
    val providerType = getObjectTypeByInternalName("${module.type.internalName}\$MethodProvider\$$index\$$projectName")
    return Provider(providerType, module.type, ProviderMedium.ProvisionPoint(provisionPoint))
  }

  private fun newFieldProvider(module: Module, provisionPoint: ProvisionPoint.Field, index: Int): Provider {
    val providerType = getObjectTypeByInternalName("${module.type.internalName}\$FieldProvider\$$index\$$projectName")
    return Provider(providerType, module.type, ProviderMedium.ProvisionPoint(provisionPoint))
  }

  private fun newBindingProvider(module: Module, binding: Binding): Provider {
    val dependencyType = binding.dependency.type.rawType as Type.Object
    val ancestorType = binding.ancestor.type.rawType as Type.Object
    val providerType = getObjectTypeByInternalName("${dependencyType.internalName}\$${ancestorType.internalName}\$BindingProvider\$$projectName")
    return Provider(providerType, module.type, ProviderMedium.Binding(binding))
  }

  private fun newFactoryProvider(module: Module, factory: Factory): Provider {
    val providerType = getObjectTypeByInternalName("${factory.type.internalName}\$FactoryProvider\$$projectName")
    return Provider(providerType, module.type, ProviderMedium.Factory(factory))
  }

  private fun newContractProvider(module: Module, contract: Contract): Provider {
    val providerType = getObjectTypeByInternalName("${contract.type.internalName}\$ContractProvider\$$projectName")
    return Provider(providerType, module.type, ProviderMedium.Contract(contract))
  }
}
