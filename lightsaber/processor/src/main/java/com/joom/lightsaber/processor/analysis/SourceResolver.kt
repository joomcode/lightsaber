/*
 * Copyright 2022 SIA Joom
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

package com.joom.lightsaber.processor.analysis

import com.joom.grip.FileRegistry
import com.joom.grip.mirrors.Type
import java.nio.file.Path

interface SourceResolver {
  fun belongsToCurrentInput(type: Type.Object): Boolean
}

class SourceResolverImpl(
  private val fileRegistry: FileRegistry,
  private val inputs: Collection<Path>,
) : SourceResolver {
  private val normalizedInputs = inputs.map { it.normalize() }

  override fun belongsToCurrentInput(type: Type.Object): Boolean {
    val path = fileRegistry.findPathForType(type)?.normalize() ?: return false

    return normalizedInputs.any { path.startsWith(it) }
  }
}
