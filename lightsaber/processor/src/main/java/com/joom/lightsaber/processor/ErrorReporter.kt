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

package com.joom.lightsaber.processor

import com.joom.lightsaber.processor.commons.immutable
import com.joom.lightsaber.processor.logging.getLogger

interface ErrorReporter {
  val hasErrors: Boolean
  val errors: List<String>
  fun reportError(errorMessage: String, exception: Throwable? = null)
}

class ErrorReporterImpl : ErrorReporter {
  private val logger = getLogger()
  private val loggedErrors = ArrayList<String>()

  override val hasErrors: Boolean
    get() = loggedErrors.isNotEmpty()

  override val errors: List<String>
    get() = loggedErrors.immutable()

  override fun reportError(errorMessage: String, exception: Throwable?) {
    loggedErrors += errorMessage
    logger.error(errorMessage, exception)
  }
}

inline fun ErrorReporter.reportError(builder: StringBuilder.() -> Unit) {
  reportError(buildString(builder))
}
