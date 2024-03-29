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

class ImportParserTest {

  @get:Rule val integrationTestRule = IntegrationTestRule("test_case_projects/import_parser")

  @Test
  fun test_parsing_failed_when_module_is_not_annotated() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "import_method_with_parameters",
      message = "Import method cannot have parameters: test_case_projects.import_parser.import_method_with_parameters.AppContractConfiguration.importAppModule"
    )
  }

  @Test
  fun test_parsing_import_with_lazy_contract() {
    integrationTestRule.assertValidProject(
      sourceCodeDir = "import_lazy_contract"
    )
  }

  @Test
  fun test_parsing_import_with_kotlin_lazy_contract() {
    integrationTestRule.assertValidProject(
      sourceCodeDir = "import_kotlin_lazy_contract"
    )
  }

  @Test
  fun test_parsing_failed_when_import_with_lazy_module() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "import_lazy_module",
      message = "Imported module with type: test_case_projects.import_parser.import_lazy_module.AppModule cannot be wrapped in: com.joom.lightsaber.Lazy"
    )
  }
}
