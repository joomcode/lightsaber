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

import io.michaelrocks.grip.mirrors.Type
import io.michaelrocks.grip.mirrors.packageName

data class GenerationContext(
  private val providersByModuleType: Map<Type.Object, Collection<Provider>>,
  private val providersByContractType: Map<Type.Object, Collection<Provider>>,
  val packageInvaders: Collection<PackageInvader>,
  val keyRegistry: KeyRegistry
) {

  val providers: Collection<Provider> = providersByModuleType.values.plus(providersByContractType.values).flatten().distinctBy { it.type }

  private val packageInvadersByPackageName = packageInvaders.associateBy { it.packageName }

  fun findProvidersByModuleType(moduleType: Type.Object): Collection<Provider> {
    return providersByModuleType[moduleType] ?: emptyList()
  }

  fun findProvidersByContractType(contractType: Type.Object): Collection<Provider> {
    return providersByContractType[contractType] ?: emptyList()
  }

  fun findPackageInvaderByTargetType(targetType: Type.Object): PackageInvader? {
    return packageInvadersByPackageName[targetType.packageName]
  }
}
