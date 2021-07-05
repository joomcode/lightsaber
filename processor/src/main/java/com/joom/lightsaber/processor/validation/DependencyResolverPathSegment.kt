/*
 * Copyright 2020 SIA Joom
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

import com.joom.lightsaber.processor.commons.appendDescriptionTo
import com.joom.lightsaber.processor.model.Binding
import com.joom.lightsaber.processor.model.Component
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ContractConfiguration
import com.joom.lightsaber.processor.model.ContractProvisionPoint
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.FactoryInjectionPoint
import com.joom.lightsaber.processor.model.FactoryProvisionPoint
import com.joom.lightsaber.processor.model.ImportPoint
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.InjectionTarget
import com.joom.lightsaber.processor.model.Module
import io.michaelrocks.grip.mirrors.AnnotationMirror
import io.michaelrocks.grip.mirrors.FieldMirror
import io.michaelrocks.grip.mirrors.MethodMirror
import io.michaelrocks.grip.mirrors.Type

sealed class DependencyResolverPathSegment {

  abstract val name: String

  fun appendDescriptionTo(appendable: Appendable, indent: String = "") {
    appendable.append(indent)
    appendable.append(name)
    appendable.append(": ")
    appendable.appendValueDescription()
  }

  fun getDescription(): String {
    return buildString { appendDescriptionTo(this) }
  }

  override fun toString(): String {
    return getDescription()
  }

  protected abstract fun Appendable.appendValueDescription()

  data class ForDependency(
    val dependency: Dependency,
    override val name: String
  ) : DependencyResolverPathSegment() {

    override fun Appendable.appendValueDescription() {
      dependency.appendDescriptionTo(this)
    }
  }

  data class ForType(
    val type: Type,
    override val name: String
  ) : DependencyResolverPathSegment() {

    override fun Appendable.appendValueDescription() {
      type.appendDescriptionTo(this)
    }
  }

  data class ForAnnotation(
    val annotation: AnnotationMirror,
    override val name: String = "Annotation"
  ) : DependencyResolverPathSegment() {

    override fun Appendable.appendValueDescription() {
      annotation.appendDescriptionTo(this)
    }
  }

  data class ForMethod(
    val method: MethodMirror,
    override val name: String = "Method"
  ) : DependencyResolverPathSegment() {

    override fun Appendable.appendValueDescription() {
      method.appendDescriptionTo(this)
    }
  }

  data class ForField(
    val field: FieldMirror,
    override val name: String = "Field"
  ) : DependencyResolverPathSegment() {

    override fun Appendable.appendValueDescription() {
      field.appendDescriptionTo(this)
    }
  }

  companion object {
    fun create(component: Component): DependencyResolverPathSegment {
      return create(component.type, "Component")
    }

    fun create(contractConfiguration: ContractConfiguration): DependencyResolverPathSegment {
      return create(contractConfiguration.type, "ContractConfiguration")
    }

    fun create(module: Module): DependencyResolverPathSegment {
      return create(module.type, "Module")
    }

    fun create(injectionTarget: InjectionTarget): DependencyResolverPathSegment {
      return create(injectionTarget.type, "Class")
    }

    fun create(injectionPoint: InjectionPoint): DependencyResolverPathSegment {
      return when (injectionPoint) {
        is InjectionPoint.Method -> create(injectionPoint.method)
        is InjectionPoint.Field -> create(injectionPoint.field)
      }
    }

    fun create(binding: Binding): DependencyResolverPathSegment {
      return create(binding.ancestor, "Binding")
    }

    fun create(factory: Factory): DependencyResolverPathSegment {
      return create(factory.type, "Factory")
    }

    fun create(factoryProvisionPoint: FactoryProvisionPoint): DependencyResolverPathSegment {
      return create(factoryProvisionPoint.method)
    }

    fun create(factoryInjectionPoint: FactoryInjectionPoint): DependencyResolverPathSegment {
      return create(factoryInjectionPoint.method)
    }

    fun create(contract: Contract): DependencyResolverPathSegment {
      return create(contract.type, "Contract")
    }

    fun create(contractProvisionPoint: ContractProvisionPoint): DependencyResolverPathSegment {
      return create(contractProvisionPoint.method)
    }

    fun create(importPoint: ImportPoint): DependencyResolverPathSegment {
      return when (importPoint) {
        is ImportPoint.Method -> create(importPoint.method)
        is ImportPoint.Field -> create(importPoint.field)
        is ImportPoint.Annotation -> create(importPoint.annotation)
      }
    }

    fun create(dependency: Dependency, name: String): DependencyResolverPathSegment {
      return ForDependency(dependency, name)
    }

    fun create(type: Type, name: String): DependencyResolverPathSegment {
      return ForType(type, name)
    }

    fun create(annotation: AnnotationMirror, name: String = "Annotation"): DependencyResolverPathSegment {
      return ForAnnotation(annotation, name)
    }

    fun create(method: MethodMirror, name: String = "Method"): DependencyResolverPathSegment {
      return ForMethod(method, name)
    }

    fun create(field: FieldMirror, name: String = "Field"): DependencyResolverPathSegment {
      return ForField(field, name)
    }
  }
}
