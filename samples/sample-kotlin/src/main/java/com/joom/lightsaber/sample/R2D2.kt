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

package com.joom.lightsaber.sample

import com.joom.lightsaber.Factory
import com.joom.lightsaber.ProvidedBy
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

internal class R2D2 @Factory.Inject private constructor(
  private val body: Body,
  @Factory.Parameter private val color: String
) : Droid {

  override fun repair() {
    println("BEEP BEEP BEEEEEP")
  }

  override fun toString(): String {
    return "R2-D2, S/N: " + body.serialNumber + ", color: " + color
  }

  @ProvidedBy(LightsaberModule::class)
  private class Body @Inject private constructor() {

    val serialNumber = serialNumberCounter.incrementAndGet()

    companion object {
      private val serialNumberCounter = AtomicInteger()
    }
  }
}
