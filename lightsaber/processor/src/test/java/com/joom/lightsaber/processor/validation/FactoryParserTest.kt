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

class FactoryParserTest {

  @get:Rule val integrationTestRule = IntegrationTestRule("test_case_projects/factory_parser")

  @Test
  fun test_check_fails_when_factory_method_returns_void() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "factory_with_void_methods",
      message = "Method test_case_projects.factory_parser.factory_with_void_methods.FactoryInterface.buildInstance returns void, but must return a class"
    )
  }

  @Test
  fun test_check_fails_when_factory_creating_instance_without_factory_inject() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "factory_creating_instance_without_factory_inject",
      message = "Class test_case_projects.factory_parser.factory_creating_instance_without_factory_inject.Instance" +
          " must have a constructor annotated with @Factory.Inject"
    )
  }

  @Test
  fun test_check_fails_when_factory_creating_instance_with_multiple_factory_inject() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "factory_creating_instance_with_multiple_factory_inject",
      message = "Class test_case_projects.factory_parser.factory_creating_instance_with_multiple_factory_inject.Instance" +
          " must have a single constructor annotated with @Factory.Inject"
    )
  }

  @Test
  fun test_check_fails_when_factory_return_with_non_class_argument() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "factory_return_with_non_class_argument",
      message = "Method test_case_projects.factory_parser.factory_return_with_non_class_argument.FactoryInterface.buildInstance is annotated with " +
          "@Factory.Return with int value, but its value must be a class"
    )
  }

  @Test
  fun test_check_fails_when_factory_creating_instance_without_providing_factory_parameter() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "factory_creating_instance_with_without_factory_parameter",
      message = "Class test_case_projects.factory_parser.factory_creating_instance_with_without_factory_parameter.Instance contains" +
          " a @Factory.Parameter not provided by factory " +
          "test_case_projects.factory_parser.factory_creating_instance_with_without_factory_parameter.FactoryInterface: " +
          "Dependency(type=java.lang.String, qualifier=null)"
    )
  }

  @Test
  fun test_check_fails_when_factory_has_misconfigured_methods() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "factory_with_misconfigured_inheritance",
      message = "Class test_case_projects.factory_parser.factory_with_misconfigured_inheritance.Bar must have a constructor annotated with @Factory.Inject"
    )
  }

  @Test
  fun test_check_works_when_using_factories_with_inheritance() {
    integrationTestRule.assertValidProject(
      sourceCodeDir = "factory_with_inheritance"
    )
  }

  @Test
  fun test_check_works_when_using_factories_with_multiple_inheritance() {
    integrationTestRule.assertValidProject(
      sourceCodeDir = "factory_with_multiple_inheritance"
    )
  }
}
