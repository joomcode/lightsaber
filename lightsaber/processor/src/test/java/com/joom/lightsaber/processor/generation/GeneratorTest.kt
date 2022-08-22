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

package com.joom.lightsaber.processor.generation

import com.joom.lightsaber.processor.integration.IntegrationTestRule
import com.joom.lightsaber.processor.integration.TestErrorReporter
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

class GeneratorTest {

  @get:Rule
  val integrationTestRule = IntegrationTestRule(ROOT)

  private val reporter = TestErrorReporter()

  @Test
  fun `generates constructor provider`() {
    val path = integrationTestRule.processProject("first_project", reporter)

    path.shouldContain(computeConstructorProviderPath("first_project", projectName = "first_project", className = "FirstModuleDependencyImpl"))
  }

  @Test
  fun `generates binding provider`() {
    val path = integrationTestRule.processProject("first_project", reporter)

    path.shouldContain(computeBindingProviderPath("first_project", projectName = "first_project", className = "UnqualifiedFirstContractDependencyImpl"))
  }

  @Test
  fun `generates module method provider`() {
    val path = integrationTestRule.processProject("first_project", reporter)

    path.shouldContain(computeMethodProviderPath("first_project", projectName = "first_project", className = "FirstDependencyModule"))
  }

  @Test
  fun `generates contract configuration method provider`() {
    val path = integrationTestRule.processProject("first_project", reporter)

    path.shouldContain(computeMethodProviderPath("first_project", projectName = "first_project", className = "FirstDependencyContractConfiguration"))
  }

  @Test
  fun `generates factory provider`() {
    val path = integrationTestRule.processProject("first_project", reporter)

    path.shouldContain(computeFactoryProviderPath("first_project", projectName = "first_project", className = "FirstFactoryCreatedDependencyFactory"))
  }

  @Test
  fun `generates factory`() {
    val path = integrationTestRule.processProject("first_project", reporter)

    path.shouldContain(computeFactoryPath("first_project", className = "FirstFactoryCreatedDependencyFactory"))
  }

  @Test
  fun `generates imported contract provider`() {
    val firstProjectPath = integrationTestRule.processProject("first_project", reporter)

    val path = integrationTestRule.processProject("second_project", reporter, modules = listOf(firstProjectPath))

    path.shouldContain(computeMethodProviderPath("first_project", projectName = "second_project", "FirstDependencyContract"))
  }

  @Test
  fun `does not generate providers for already processed modules`() {
    val firstProjectPath = integrationTestRule.processProject("first_project", reporter)

    val path = integrationTestRule.processProject("second_project", reporter, modules = listOf(firstProjectPath))

    path.shouldNotContain(computeConstructorProviderPath("first_project", projectName = "first_project", className = "FirstModuleDependencyImpl"))
    path.shouldNotContain(computeBindingProviderPath("first_project", projectName = "first_project", className = "UnqualifiedFirstContractDependencyImpl"))
    path.shouldNotContain(computeMethodProviderPath("first_project", projectName = "first_project", className = "FirstDependencyModule"))
    path.shouldNotContain(computeMethodProviderPath("first_project", projectName = "first_project", className = "FirstDependencyContractConfiguration"))
    path.shouldNotContain(computeFactoryProviderPath("first_project", projectName = "first_project", className = "FirstFactoryCreatedDependencyFactory"))
    path.shouldNotContain(computeFactoryPath("first_project", className = "FirstFactoryCreatedDependencyFactory"))
  }

  @Test
  fun `generates provider for constructor with parameterized type`() {
    integrationTestRule.assertValidProject("parameterized_type")
  }

  @Test
  fun `generates provider for bounded generics`() {
    integrationTestRule.assertValidProject("bounded_generics")
  }

  private fun Path.shouldContain(path: Path) {
    Assert.assertTrue(resolve(path).exists())
  }

  private fun Path.shouldNotContain(path: Path) {
    Assert.assertFalse(resolve(path).exists())
  }

  private fun computeConstructorProviderPath(sourceCodeDir: String, projectName: String, className: String): Path {
    return computeProviderPath(sourceCodeDir, projectName, "ConstructorProvider0", className)
  }

  private fun computeBindingProviderPath(sourceCodeDir: String, projectName: String, className: String): Path {
    return computeProviderPath(sourceCodeDir, projectName, "BindingProvider0", className)
  }

  private fun computeMethodProviderPath(sourceCodeDir: String, projectName: String, className: String): Path {
    return computeProviderPath(sourceCodeDir, projectName, "MethodProvider0", className)
  }

  private fun computeFactoryProviderPath(sourceCodeDir: String, projectName: String, className: String): Path {
    return computeProviderPath(sourceCodeDir, projectName, "FactoryProvider0", className)
  }

  private fun computeFactoryPath(sourceCodeDir: String, className: String): Path {
    return Paths.get(ROOT, sourceCodeDir, "${className}\$Lightsaber\$Factory.class")
  }

  private fun computeProviderPath(sourceCodeDir: String, projectName: String, providerName: String, className: String): Path {
    return Paths.get(ROOT, sourceCodeDir, "${className}\$${providerName}\$${projectName}.class")
  }

  private companion object {
    private const val ROOT = "test_case_projects/generator"
  }
}
