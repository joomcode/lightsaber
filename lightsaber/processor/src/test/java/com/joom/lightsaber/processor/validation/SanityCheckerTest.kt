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

class SanityCheckerTest {

  @get:Rule val integrationTestRule = IntegrationTestRule("test_case_projects/sanity")

  @Test
  fun test_check_failed_when_provide_as_argument_has_wrong_type() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "invalid_provided_as",
      message = "@ProvidedAs binding's argument java.lang.String isn't a super type of" +
          " the host class test_case_projects.sanity.invalid_provided_as.Dependency"
    )
  }

  @Test
  fun test_check_failed_when_dependency_is_an_abstract() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "invalid_access_level",
      message = "Providable class cannot be abstract: Ltest_case_projects/sanity/invalid_access_level/AbstractClassDependency;"
    )
  }

  @Test
  fun test_check_failed_when_dependency_injected_in_static_field() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "static_field_injection",
      message = "Static field injection is not supported yet: FieldMirror{name = dependency, " +
          "type = Ltest_case_projects/sanity/static_field_injection/Dependency;}"
    )
  }

  @Test
  fun test_check_failed_when_dependency_injected_in_static_method() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "static_method_injection",
      message = "Static method injection is not supported yet: MethodMirror{name = staticMethodWithInjection, " +
          "type = (Ltest_case_projects/sanity/static_method_injection/Dependency;)V}"
    )
  }

  @Test
  fun test_check_failed_when_module_inherits_types_that_not_allowed() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "module_with_invalid_parent",
      message = "test_case_projects.sanity.module_with_invalid_parent.AppModule has a super type of test_case_projects.sanity.module_with_invalid_parent.AppModule " +
          "instead of java.lang.Object or com.joom.lightsaber.ContractConfiguration"
    )
  }

  @Test
  fun test_check_successful_when_module_inherits_contract_configuration() {
    integrationTestRule.assertValidProject("module_inherits_contract_configuration_class")
  }

  @Test
  fun test_check_failed_when_module_with_imported_by_does_not_have_default_constructor() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "non_constructable_module_with_imported_by",
      message = "Module test_case_projects.sanity.non_constructable_module_with_imported_by.AppModule with @ImportedBy annotation must have a default constructor"
    )
  }

  @Test
  fun test_check_fails_when_contract_configuration_is_an_abstract() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "abstract_contract_configuration",
      message = "Contract configuration test_case_projects.sanity.abstract_contract_configuration.AppContractConfiguration should be a concrete class"
    )
  }

  @Test
  fun test_check_failed_when_provided_as_has_generic_class_argument() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "provide_as_with_generic_argument_class",
      message = "@ProvidedAs bindings cannot have a generic type java.util.List as an argument"
    )
  }

  @Test
  fun test_check_failed_when_provided_as_has_generic_class_host() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "provide_as_with_generic_host_class",
      message = "@ProvidedAs bindings aren't supported for a generic type test_case_projects.sanity.provide_as_with_generic_host_class.Dependency"
    )
  }

  @Test
  fun test_check_failed_when_provide_as_has_non_inherited_argument() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "provide_as_with_not_inherited_argument",
      message = "@ProvidedAs binding's argument test_case_projects.sanity.provide_as_with_not_inherited_argument.InterfaceThatHostClassDoesNotInherit" +
          " isn't a super type of the host class test_case_projects.sanity.provide_as_with_not_inherited_argument.Dependency"
    )
  }

  @Test
  fun test_check_failed_when_provide_as_containing_argument_class_with_injectable_constructor() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "provide_as_with_argument_class_containing_injectable_constructor",
      message = "@ProvidedAs binding's argument test_case_projects.sanity.provide_as_with_argument_class_containing_injectable_constructor.ClassWithInjectableConstructor cannot have an @Inject constructor"
    )
  }

  @Test
  fun test_check_failed_when_provide_as_containing_host_class_as_argument() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "provide_as_with_host_class_as_argument",
      message = "@ProvidedAs bindings cannot have a host class test_case_projects.sanity.provide_as_with_host_class_as_argument.Dependency as an argument"
    )
  }

  @Test
  fun test_check_failed_when_factory_host_is_not_an_interface() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "factory_is_not_an_interface",
      message = "Factory test_case_projects.sanity.factory_is_not_an_interface.FactoryClass must be an interface"
    )
  }

  @Test
  fun test_check_failed_when_factory_contains_generic_parameters() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "factory_contains_generic_parameters",
      message = "Factory test_case_projects.sanity.factory_contains_generic_parameters.FactoryInterface mustn't contain generic parameters"
    )
  }

  @Test
  fun test_check_fails_when_factory_does_not_have_any_methods() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "factory_without_methods",
      message = "Factory test_case_projects.sanity.factory_without_methods.FactoryInterface must contain at least one method"
    )
  }

  @Test
  fun test_check_fails_when_factory_does_not_have_any_methods2() {
    integrationTestRule.assertInvalidProject(
      sourceCodeDir = "factory_creating_instance_type_differ_from_return_annotation",
      message = "Method test_case_projects.sanity.factory_creating_instance_type_differ_from_return_annotation.FactoryInterface.buildInstance returns " +
          "test_case_projects.sanity.factory_creating_instance_type_differ_from_return_annotation.Instance which isn't an ancestor of " +
          "test_case_projects.sanity.factory_creating_instance_type_differ_from_return_annotation.ActualInstance from the @Factory.Return annotation"
    )
  }
}
