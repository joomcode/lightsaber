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

package com.joom.lightsaber.processor.validation

import com.joom.lightsaber.processor.integration.IntegrationTestRule
import org.junit.Rule
import org.junit.Test

class ModuleParserTest {

  @get:Rule val integrationTestRule = IntegrationTestRule("test_case_projects/module_parser")

  @Test
  fun test_parsing_failed_when_module_is_not_annotated() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "module_without_annotation",
      message = "Imported module test_case_projects.module_parser.module_without_annotation.NonAnnotatedModule isn't annotated with @Module"
    )
  }

  @Test
  fun test_parsing_failed_if_module_have_type_parameters() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "module_with_type_parameters",
      message = "Module cannot have a type parameters: test_case_projects.module_parser.module_with_type_parameters.AppContractConfiguration"
    )
  }
}
