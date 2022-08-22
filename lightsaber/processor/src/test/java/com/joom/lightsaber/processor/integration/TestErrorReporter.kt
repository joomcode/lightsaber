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

package com.joom.lightsaber.processor.integration

import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.ErrorReporterImpl
import org.junit.Assert

class TestErrorReporter(private val delegate: ErrorReporter = ErrorReporterImpl()) : ErrorReporter by delegate {
  fun assertErrorReported(message: String) {
    Assert.assertTrue("Expected '${message}', got:\n${errorsToString()}", errors.any { it == message })
  }

  fun assertNoErrorsReported() {
    Assert.assertFalse("Expected no errors, got\n${errorsToString()}", hasErrors)
  }

  private fun errorsToString(): String {
    return errors.joinToString("\n") { it }
  }
}
