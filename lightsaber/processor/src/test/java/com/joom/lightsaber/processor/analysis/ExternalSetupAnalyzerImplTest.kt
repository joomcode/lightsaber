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
import com.joom.lightsaber.processor.integration.TestErrorReporter
import org.junit.Rule
import org.junit.Test

class ExternalSetupAnalyzerImplTest {

  @get:Rule
  val integrationTestRule = IntegrationTestRule("test_case_projects/external_setup_analyzer")

  @Test
  fun test_analysis_failed_if_imported_by_used_without_arguments() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "imported_by_without_arguments",
      message = "Module test_case_projects.external_setup_analyzer.imported_by_without_arguments.AppModule should be imported by at least one container"
    )
  }

  @Test
  fun test_analysis_failed_when_imported_by_have_non_class_argument() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "imported_by_with_non_class_argument",
      message = "A non-class type is specified in @ImportedBy annotation for test_case_projects.external_setup_analyzer.imported_by_with_non_class_argument.AppModule"
    )
  }

  @Test
  fun test_analysis_failed_when_module_imported_by_have_non_container_argument() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "imported_by_with_non_container_argument",
      message = "Module test_case_projects.external_setup_analyzer.imported_by_with_non_container_argument.AppModule is imported " +
          "by java.lang.Object, which isn't a container"
    )
  }

  @Test
  fun test_analysis_failed_when_provided_by_have_non_container_argument() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "provided_by_with_non_container_argument",
      message = "test_case_projects.external_setup_analyzer.provided_by_with_non_container_argument.AppDependency is provided " +
          "by java.lang.Object, which isn't a container"
    )
  }

  @Test
  fun test_analysis_failed_when_imported_by_have_empty_list_of_argument() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "imported_by_with_empty_list_of_arguments",
      message = "Module test_case_projects.external_setup_analyzer.imported_by_with_empty_list_of_arguments.AppModule should be imported by at least one container"
    )
  }

  @Test
  fun test_analysis_failed_when_imported_by_dependency_project() {
    val reporter = TestErrorReporter()
    val dependencyProjectProcessed = integrationTestRule.processProject("dependency_project", reporter)
    integrationTestRule.processProject(
      "imported_by_dependency_project",
      reporter,
      modules = listOf(dependencyProjectProcessed),
      ignoreErrors = true,
    )

    reporter.assertErrorReported(
      "Module test_case_projects.external_setup_analyzer.imported_by_dependency_project.ImportedModule is imported by" +
          " test_case_projects.external_setup_analyzer.dependency_project.DependencyProjectModule, which doesn't belong to current inputs"
    )
  }

  @Test
  fun test_analysis_failed_when_provided_by_dependency_project() {
    val reporter = TestErrorReporter()
    val dependencyProjectProcessed = integrationTestRule.processProject("dependency_project", reporter)
    integrationTestRule.processProject(
      "provided_by_dependency_project",
      reporter,
      modules = listOf(dependencyProjectProcessed),
      ignoreErrors = true,
    )

    reporter.assertErrorReported(
      "test_case_projects.external_setup_analyzer.provided_by_dependency_project.ProvidedDependency is provided by" +
          " test_case_projects.external_setup_analyzer.dependency_project.DependencyProjectModule, which doesn't belong to current inputs"
    )
  }
}
