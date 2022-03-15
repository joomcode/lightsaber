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

import com.joom.lightsaber.processor.integration.IntegrationTestRule
import org.junit.Rule
import org.junit.Test

class BindingsAnalyzerImplTest {

  @get:Rule val integrationTestRule = IntegrationTestRule("test_case_projects/bindings_analyzer")

  @Test
  fun test_check_failed_when_provide_as_has_non_class_argument() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "provide_as_with_non_class_argument",
      message = "Class test_case_projects.bindings_analyzer.provide_as_with_non_class_argument.Dependency has a non-class type in its @ProvidedAs annotation: I"
    )
  }
}
