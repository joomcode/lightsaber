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

import org.junit.Assert
import org.junit.Test
import java.io.Closeable

class LightsaberSharedBuildCacheImplTest {

  @Test
  fun `getOrPut - calls factory - no key in cache`() {
    val cache = LightsaberSharedBuildCache.create()

    var called = false
    cache.getOrPut("Key") {
      called = true
      TestCloseable()
    }

    Assert.assertTrue("Factory was not called", called)
  }

  @Test
  fun `getOrPut - returns same instance - key present in cache`() {
    val cache = LightsaberSharedBuildCache.create()
    val expected = TestCloseable()

    cache.getOrPut("Key") {
      expected
    }

    val actual = cache.getOrPut("Key") { TestCloseable() }

    Assert.assertSame("Instances were not the same", expected, actual)
  }

  @Test
  fun `close - closes Closeable`() {
    val cache = LightsaberSharedBuildCache.create()
    val closeable = TestCloseable()
    cache.getOrPut("Key") {
      closeable
    }

    cache.close()

    Assert.assertTrue("Closeable was not closed", closeable.closed)
  }

  @Test
  fun `close - clears cache`() {
    val cache = LightsaberSharedBuildCache.create()
    val firstCloseable = TestCloseable()
    val secondCloseable = TestCloseable()
    cache.getOrPut("Key") { firstCloseable }

    cache.close()

    val actual = cache.getOrPut("Key") { secondCloseable }
    Assert.assertSame("Cache was not closed", secondCloseable, actual)
  }

  private class TestCloseable : Closeable {
    var closed: Boolean = false
      private set

    override fun close() {
      closed = true
    }
  }
}
