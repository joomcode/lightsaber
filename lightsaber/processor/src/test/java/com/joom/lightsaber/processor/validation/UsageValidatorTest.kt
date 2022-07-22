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
import com.joom.lightsaber.processor.integration.TestErrorReporter
import org.junit.Rule
import org.junit.Test

class UsageValidatorTest {
  @get:Rule
  val integrationTestRule = IntegrationTestRule("test_case_projects/usage_validator")

  private val reporter = TestErrorReporter()

  @Test
  fun test_check_failed_not_processed_classes_on_modules_classpath() {
    val dependencyProjectCompiled = integrationTestRule.compileProject("dependency_project")
    val referencingProjectCompiled = integrationTestRule.compileProject("referencing_dependency_project", classpath = listOf(dependencyProjectCompiled))

    integrationTestRule.processProject(
      referencingProjectCompiled, "referencing_dependency_project", reporter, listOf(dependencyProjectCompiled),
      ignoreErrors = true
    )

    reporter.assertErrorReported(
      "Class test_case_projects.usage_validator.dependency_project.DependencyProjectModule is not processed by lightsaber," +
          " is plugin applied to module?"
    )
    reporter.assertErrorReported(
      "Class test_case_projects.usage_validator.dependency_project.DependencyProjectComponent is not processed by lightsaber," +
          " is plugin applied to module?"
    )
    reporter.assertErrorReported(
      "Class test_case_projects.usage_validator.dependency_project.DependencyProjectContractConfiguration is not processed by" +
          " lightsaber, is plugin applied to module?"
    )
    reporter.assertErrorReported(
      "Class test_case_projects.usage_validator.dependency_project.DependencyProjectFactory is not processed by lightsaber," +
          " is plugin applied to module?"
    )
  }

  @Test
  fun test_check_successful_for_processed_classes_on_modules_classpath() {
    val dependencyProjectCompiled = integrationTestRule.compileProject("dependency_project")
    val dependencyProjectProcessed = integrationTestRule.processProject(dependencyProjectCompiled, "dependency_project", reporter)
    val referencingProjectCompiled = integrationTestRule.compileProject("referencing_dependency_project", classpath = listOf(dependencyProjectProcessed))
    integrationTestRule.processProject(referencingProjectCompiled, "referencing_dependency_project", reporter, modules = listOf(dependencyProjectProcessed))

    reporter.assertNoErrorsReported()
  }
}
