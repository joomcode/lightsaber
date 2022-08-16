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

package com.joom.lightsaber.processor.model

import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.getObjectTypeByInternalName

data class Factory(
  val type: Type.Object,
  val implementationType: Type.Object,
  val dependency: Dependency,
  val provisionPoints: List<FactoryProvisionPoint>
) {

  companion object {
    fun computeImplementationType(type: Type.Object): Type.Object {
      return getObjectTypeByInternalName("${type.internalName}\$Lightsaber\$Factory")
    }
  }
}
