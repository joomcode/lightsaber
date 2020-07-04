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

import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.ProvisionPoint
import com.joom.lightsaber.processor.model.Scope
import io.michaelrocks.grip.mirrors.Type

data class Provider(
  val type: Type.Object,
  val medium: ProviderMedium
) {

  val dependency: Dependency get() = medium.dependency
}

val Provider.moduleType: Type.Object?
  get() = when (medium) {
    is ProviderMedium.ProvisionPoint -> when (medium.provisionPoint) {
      is ProvisionPoint.Constructor -> null
      is ProvisionPoint.Method,
      is ProvisionPoint.Field -> medium.provisionPoint.containerType
    }
    is ProviderMedium.Binding,
    is ProviderMedium.Factory,
    is ProviderMedium.Contract -> null
    is ProviderMedium.ContractProvisionPoint -> medium.contractType
  }

val Provider.scope: Scope
  get() = when (medium) {
    is ProviderMedium.ProvisionPoint -> medium.provisionPoint.scope
    is ProviderMedium.Binding,
    is ProviderMedium.Factory,
    is ProviderMedium.Contract,
    is ProviderMedium.ContractProvisionPoint -> Scope.None
  }
