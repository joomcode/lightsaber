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

class ContractParserTest {

  @get:Rule val integrationTestRule = IntegrationTestRule("test_case_projects/contract_parser")

  @Test
  fun test_parsing_failed_when_contract_is_not_interface() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "non_interface_contract",
      message = "Contract must be an interface: test_case_projects.contract_parser.non_interface_contract.ClassContract"
    )
  }

  @Test
  fun test_parsing_failed_when_contract_have_generic_parameters() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "contract_with_generic_parameters",
      message = "ContractConfiguration test_case_projects.contract_parser.contract_with_generic_parameters.AppContractConfiguration contains " +
          "a generic type: test_case_projects.contract_parser.contract_with_generic_parameters.ClassContract<? extends java.lang.Object>"
    )
  }

  @Test
  fun test_parsing_failed_when_contract_have_generic_parameters2() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "contract_with_methods_with_parameters",
      message = "Contract's method cannot have parameters: test_case_projects.contract_parser.contract_with_methods_with_parameters.AppContract.method"
    )
  }
}
