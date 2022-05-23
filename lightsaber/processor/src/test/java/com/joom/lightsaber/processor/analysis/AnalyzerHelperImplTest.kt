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

class AnalyzerHelperImplTest {

  @get:Rule val integrationTestRule = IntegrationTestRule("test_case_projects/analyzer_helper")

  @Test
  fun test_analysis_failed_when_contract_configuration_has_generic_type() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "contract_configuration_with_generic_type",
      message = "ContractConfiguration test_case_projects.analyzer_helper.contract_configuration_with_generic_type.AppContractConfiguration contains" +
          " a generic type: test_case_projects.analyzer_helper.contract_configuration_with_generic_type.AppContract<? extends java.lang.Object>"
    )
  }

  @Test
  fun test_analysis_failed_when_contract_configuration_has_non_class_type() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "multiple_qualifiers",
      message = "Element FieldMirror{name = parameter, type = Ltest_case_projects/analyzer_helper/multiple_qualifiers/Instance;} has multiple qualifiers"
    )
  }

  @Test
  fun test_analysis_failed_when_eager_is_used_without_scope() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "eager_without_scope",
      message = "Element test_case_projects.analyzer_helper.eager_without_scope.Instance is annotated with @Eager but doesn't have a scope"
    )
  }
}
