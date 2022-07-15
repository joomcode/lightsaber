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

    path.shouldContain(computeConstructorProviderPath("first_project", className = "FirstDependencyImpl"))
  }

  @Test
  fun `generates binding provider`() {
    val path = integrationTestRule.processProject("first_project", reporter)

    path.shouldContain(computeBindingProviderPath("first_project", className = "FirstDependencyImpl"))
  }

  @Test
  fun `does not generate providers for already processed modules`() {
    val firstProjectPath = integrationTestRule.processProject("first_project", reporter)
    val secondProjectPath = integrationTestRule.processProject("second_project", reporter, modules = listOf(firstProjectPath))

    secondProjectPath.shouldNotContain(computeConstructorProviderPath("second_project", className = "FirstDependencyImpl"))
    secondProjectPath.shouldNotContain(computeBindingProviderPath("second_project", className = "FirstDependencyImpl"))
  }

  private fun Path.shouldContain(path: Path) {
    Assert.assertTrue(resolve(path).exists())
  }

  private fun Path.shouldNotContain(path: Path) {
    Assert.assertFalse(resolve(path).exists())
  }

  private fun computeConstructorProviderPath(sourceCodeDir: String, className: String): Path {
    return Paths.get(ROOT, sourceCodeDir, "${className}\$ConstructorProvider0\$${sourceCodeDir}.class")
  }

  private fun computeBindingProviderPath(sourceCodeDir: String, className: String): Path {
    return Paths.get(ROOT, sourceCodeDir, "${className}\$BindingProvider0\$${sourceCodeDir}.class")
  }

  private companion object {
    private const val ROOT = "test_case_projects/generator"
  }
}
