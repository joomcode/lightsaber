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

sealed class ProviderMedium {
  abstract val dependency: Dependency

  data class ProvisionPoint(
    val provisionPoint: com.joom.lightsaber.processor.model.ProvisionPoint
  ) : ProviderMedium() {

    override val dependency: Dependency get() = provisionPoint.dependency
  }

  data class Binding(
    val binding: com.joom.lightsaber.processor.model.Binding
  ) : ProviderMedium() {

    override val dependency: Dependency get() = binding.ancestor
  }

  data class Factory(
    val factory: com.joom.lightsaber.processor.model.Factory
  ) : ProviderMedium() {

    override val dependency: Dependency get() = factory.dependency
  }

  data class Contract(
    val contract: com.joom.lightsaber.processor.model.Contract
  ) : ProviderMedium() {

    override val dependency: Dependency get() = contract.dependency
  }
}
