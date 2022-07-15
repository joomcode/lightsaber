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

class ValidatorTest {

  @get:Rule val integrationTestRule = IntegrationTestRule("test_case_projects/validator")

  @Test
  fun test_processed_without_any_problems() {
    integrationTestRule.assertValidProject("valid_configuration")
  }

  @Test
  fun test_processed_without_any_problems_with_lazy_imports() {
    integrationTestRule.assertValidProject("valid_configuration_with_lazy_imports")
  }

  @Test
  fun test_validation_fails_if_provided_as_is_used_without_provided_by() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "provided_as_without_provided_by",
      message = "Invalid configuration for dependency: test_case_projects.validator.provided_as_without_provided_by.DependencyInterface. \n" +
          "\ttest_case_projects.validator.provided_as_without_provided_by.DependencyImplementation: Using @ProvidedAs without @ProvidedBy." +
          " You must either add @ProvidedBy annotation or add @Provide method\n"
    )
  }

  @Test
  fun test_validation_fails_if_provided_by_is_used_without_inject() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "provided_by_without_inject",
      message = "Invalid configuration for dependency: " +
          "test_case_projects.validator.provided_by_without_inject.DependencyInterface. \n" +
          "\ttest_case_projects.validator.provided_by_without_inject.DependencyImplementation: " +
          "Using @ProvidedBy without @Inject. You must either add at least one constructor " +
          "with @Inject annotation or add @Provide method\n"
    )
  }

  @Test
  fun test_validation_fails_if_dependency_is_not_provided() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "dependency_missed",
      message = "Unresolved dependency" +
          " test_case_projects.validator.dependency_missed.MissedDependencyInterface:\n" +
          "  ContractConfiguration: test_case_projects.validator.dependency_missed.AppContractConfiguration\n" +
          "  Contract: test_case_projects.validator.dependency_missed.AppContract\n" +
          "  Method: test_case_projects.validator.dependency_missed.MissedDependencyInterface getDependency()"
    )
  }

  @Test
  fun test_validation_fails_if_dependencies_has_cycles() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "dependency_cycle",
      message = "Dependency cycle in contract test_case_projects.validator.dependency_cycle.AppContractConfiguration:\n" +
          "  test_case_projects.validator.dependency_cycle.Dependency1\n" +
          "  test_case_projects.validator.dependency_cycle.Dependency2\n" +
          "  test_case_projects.validator.dependency_cycle.Dependency1"
    )
  }

  @Test
  fun test_validation_fails_if_dependency_provided_multiple_times() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "dependency_duplicates",
      message = "Dependency test_case_projects.validator.dependency_duplicates.Dependency provided multiple times in a contract:\n" +
          "1.\n" +
          "  ContractConfiguration: test_case_projects.validator.dependency_duplicates.AppContractConfiguration\n" +
          "  Method: test_case_projects.validator.dependency_duplicates.AppModule1 importAppModule1()\n" +
          "  Module: test_case_projects.validator.dependency_duplicates.AppModule1\n" +
          "  Method: test_case_projects.validator.dependency_duplicates.Dependency provideDependency()\n" +
          "2.\n" +
          "  ContractConfiguration: test_case_projects.validator.dependency_duplicates.AppContractConfiguration\n" +
          "  Method: test_case_projects.validator.dependency_duplicates.AppModule2 importAppModule2()\n" +
          "  Module: test_case_projects.validator.dependency_duplicates.AppModule2\n" +
          "  Method: test_case_projects.validator.dependency_duplicates.Dependency provideDependency()"
    )
  }

  @Test
  fun validation_fails_if_module_imported_multiple_times() {
    integrationTestRule.assertInvalidProject(
      "module_duplicates",
      "Class test_case_projects.validator.module_duplicates.AppModule imported multiple times in a contract:\n" +
          "1.\n" +
          "  ContractConfiguration: test_case_projects.validator.module_duplicates.AppContractConfiguration\n" +
          "  Method: test_case_projects.validator.module_duplicates.AppModule importAppModule1()\n" +
          "2.\n" +
          "  ContractConfiguration: test_case_projects.validator.module_duplicates.AppContractConfiguration\n" +
          "  Method: test_case_projects.validator.module_duplicates.AppModule importAppModule2()"
    )
  }

  @Test
  fun validation_fails_if_contract_imported_multiple_times() {
    integrationTestRule.assertInvalidProject(
      "contract_duplicates",
      "Class test_case_projects.validator.contract_duplicates.LazyContract imported multiple times in a contract:\n" +
          "1.\n" +
          "  ContractConfiguration: test_case_projects.validator.contract_duplicates.AppContractConfiguration\n" +
          "  Field: test_case_projects.validator.contract_duplicates.LazyContract lazyContract1\n" +
          "2.\n" +
          "  ContractConfiguration: test_case_projects.validator.contract_duplicates.AppContractConfiguration\n" +
          "  Field: com.joom.lightsaber.Lazy<test_case_projects.validator.contract_duplicates.LazyContract> lazyContract2"
    )
  }
}
