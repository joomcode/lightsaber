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

package com.joom.lightsaber.plugin

import com.joom.lightsaber.processor.LightsaberSharedBuildCache
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent
import java.util.Collections

@Suppress("UnstableApiUsage")
abstract class LightsaberSharedBuildCacheService : BuildService<LightsaberSharedBuildCacheService.Parameters>, AutoCloseable, OperationCompletionListener {
  val cache = LightsaberSharedBuildCache.create()
  private val registeredTaskPaths = Collections.synchronizedSet(HashSet<String>(parameters.taskPaths.get()))

  override fun onFinish(event: FinishEvent) {
    if (event !is TaskFinishEvent) {
      return
    }

    val path = event.descriptor.taskPath
    val pathRemoved = registeredTaskPaths.remove(path)

    if (pathRemoved && registeredTaskPaths.size == 0) {
      cache.close()
    }
  }

  override fun close() {
    registeredTaskPaths.clear()
    cache.close()
  }

  interface Parameters : BuildServiceParameters {
    val taskPaths: Property<Collection<String>>
  }
}

