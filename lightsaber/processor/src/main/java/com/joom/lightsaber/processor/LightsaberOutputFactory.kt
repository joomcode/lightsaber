/*
 * Copyright 2023 SIA Joom
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

package com.joom.lightsaber.processor

import java.nio.file.Path

interface LightsaberOutputFactory {
  fun createOutput(): LightsaberOutput

  companion object {
    fun create(output: Path): LightsaberOutputFactory {
      return SingleSinkOutputProvider(output)
    }

    fun create(inputs: List<Path>, outputs: List<Path>, generationPath: Path): LightsaberOutputFactory {
      require(inputs.size == outputs.size) { "Inputs size should be equal to outputs size" }

      return MultipleSinkOutputProvider(inputs, outputs, generationPath)
    }
  }
}

internal class SingleSinkOutputProvider(
  private val output: Path,
) : LightsaberOutputFactory {
  override fun createOutput(): LightsaberOutput {
    return SingleSinkOutput(output)
  }
}

internal class MultipleSinkOutputProvider(
  private val inputs: List<Path>,
  private val outputs: List<Path>,
  private val generationPath: Path,
) : LightsaberOutputFactory {
  override fun createOutput(): LightsaberOutput {
    return MultipleSinkOutput(inputs, outputs, generationPath)
  }
}


