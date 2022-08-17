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

package com.joom.lightsaber.processor

import com.joom.lightsaber.processor.commons.closeQuietly
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

interface LightsaberSharedBuildCache : Closeable {
  fun <K : Any, V : Closeable> getOrPut(key: K, factory: (key: K) -> V): V

  companion object {
    fun create(): LightsaberSharedBuildCache {
      return LightsaberSharedBuildCacheImpl()
    }
  }
}

internal class LightsaberSharedBuildCacheImpl : LightsaberSharedBuildCache {
  private val cache = ConcurrentHashMap<Any, Closeable>()

  override fun <K : Any, V : Closeable> getOrPut(key: K, factory: (key: K) -> V): V {
    @Suppress("UNCHECKED_CAST")
    return (cache.getOrPut(key) { factory(key) } as V)
  }

  override fun close() {
    cache.forEach {
      it.value.closeQuietly()
    }
    cache.clear()
  }
}
