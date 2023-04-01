package com.joom.lightsaber.processor.validation

import com.joom.lightsaber.processor.integration.IntegrationTestRule
import com.joom.lightsaber.processor.integration.TestErrorReporter
import org.junit.Rule
import org.junit.Test

class UnusedImportsTest {
  @get:Rule
  val integrationTestRule = IntegrationTestRule("test_case_projects/unused_imports")

  @Test
  fun detects_unused_contract_import() {
    val reporter = TestErrorReporter()

    val projectName = "unused_contract"
    val project = integrationTestRule.compileProject(projectName)

    integrationTestRule.processProject(
      compiled = project,
      projectName = projectName,
      errorReporter = reporter,
      dumpDebugReport = true,
      validateUnusedImports = true,
      ignoreErrors = true
    )

    reporter.assertErrorReported(
      """
        Found unused imports in a contract configuration test_case_projects.unused_imports.unused_contract.MainContractConfiguration:
          - Contract test_case_projects.unused_imports.unused_contract.UnusedContract
      """.trimIndent()
    )
  }

  @Test
  fun detects_unused_module_import() {
    val reporter = TestErrorReporter()

    val projectName = "unused_module"
    val project = integrationTestRule.compileProject(projectName)

    integrationTestRule.processProject(
      compiled = project,
      projectName = projectName,
      errorReporter = reporter,
      dumpDebugReport = true,
      validateUnusedImports = true,
      ignoreErrors = true
    )

    reporter.assertErrorReported(
      """
        Found unused imports in a contract configuration test_case_projects.unused_imports.unused_module.MainContractConfiguration:
          - Module test_case_projects.unused_imports.unused_module.UnusedModule
      """.trimIndent()
    )
  }


  @Test
  fun ignores_unused_imports_when_injector_is_used() {
    val reporter = TestErrorReporter()

    val projectName = "injector_usages"
    val project = integrationTestRule.compileProject(projectName)

    integrationTestRule.processProject(
      compiled = project,
      projectName = projectName,
      errorReporter = reporter,
      dumpDebugReport = true,
      validateUnusedImports = true,
      ignoreErrors = false
    )

    reporter.assertNoErrorsReported()
  }

  @Test
  fun detects_transitive_module_usages() {
    val reporter = TestErrorReporter()

    val projectName = "transitive_module_usages"
    val project = integrationTestRule.compileProject(projectName)

    integrationTestRule.processProject(
      compiled = project,
      projectName = projectName,
      errorReporter = reporter,
      dumpDebugReport = true,
      validateUnusedImports = true,
      ignoreErrors = false
    )

    reporter.assertNoErrorsReported()
  }

  @Test
  fun detects_provider_usages() {
    val reporter = TestErrorReporter()

    val projectName = "provider_usages"
    val project = integrationTestRule.compileProject(projectName)

    integrationTestRule.processProject(
      compiled = project,
      projectName = projectName,
      errorReporter = reporter,
      dumpDebugReport = true,
      validateUnusedImports = true,
      ignoreErrors = false
    )

    reporter.assertNoErrorsReported()
  }

  @Test
  fun detects_factory_usages() {
    val reporter = TestErrorReporter()

    val projectName = "factory_usages"
    val project = integrationTestRule.compileProject(projectName)

    integrationTestRule.processProject(
      compiled = project,
      projectName = projectName,
      errorReporter = reporter,
      dumpDebugReport = true,
      validateUnusedImports = true,
      ignoreErrors = false
    )

    reporter.assertNoErrorsReported()
  }
}
