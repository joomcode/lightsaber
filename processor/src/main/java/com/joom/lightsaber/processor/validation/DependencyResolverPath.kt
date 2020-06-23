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

import com.joom.lightsaber.processor.commons.getDescription
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
import com.joom.lightsaber.processor.model.ProvisionPoint
import io.michaelrocks.grip.mirrors.AnnotationMirror
import io.michaelrocks.grip.mirrors.FieldMirror
import io.michaelrocks.grip.mirrors.MethodMirror
import io.michaelrocks.grip.mirrors.Type

data class DependencyResolverPath(
  val path: DependencyResolverPath?,
  val segment: String
) {

  fun getDescription(indent: String = "", separator: String = "\n"): String {
    fun StringBuilder.appendPath(path: DependencyResolverPath): StringBuilder = apply {
      path.path?.let { appendPath(it).append(separator) }
      append(indent)
      append(path.segment)
    }

    return buildString {
      appendPath(this@DependencyResolverPath)
    }
  }

  fun with(module: Module): DependencyResolverPath {
    return DependencyResolverPath(this, newSegment(module))
  }

  fun with(provisionPoint: ProvisionPoint): DependencyResolverPath {
    return when (provisionPoint) {
      is ProvisionPoint.Constructor ->
        with(newSegment(provisionPoint.containerType, "Class"))
          .with(newSegment(provisionPoint.injectionPoint.method, "Constructor"))
      is ProvisionPoint.Method -> with(newSegment(provisionPoint.method))
      is ProvisionPoint.Field -> with(newSegment(provisionPoint.field))
    }
  }

  fun with(injectionTarget: InjectionTarget): DependencyResolverPath {
    return with(newSegment(injectionTarget))
  }

  fun with(injectionPoint: InjectionPoint): DependencyResolverPath {
    return with(newSegment(injectionPoint))
  }

  fun with(binding: Binding): DependencyResolverPath {
    return with(newSegment(binding))
  }

  fun with(factory: Factory): DependencyResolverPath {
    return with(newSegment(factory))
  }

  fun with(factoryProvisionPoint: FactoryProvisionPoint): DependencyResolverPath {
    return with(newSegment(factoryProvisionPoint))
  }

  fun with(factoryInjectionPoint: FactoryInjectionPoint): DependencyResolverPath {
    return with(newSegment(factoryInjectionPoint))
  }

  fun with(contract: Contract): DependencyResolverPath {
    return with(newSegment(contract))
  }

  fun with(contractProvisionPoint: ContractProvisionPoint): DependencyResolverPath {
    return with(newSegment(contractProvisionPoint))
  }

  fun with(importPoint: ImportPoint): DependencyResolverPath {
    return with(newSegment(importPoint))
  }

  private fun with(segment: String): DependencyResolverPath {
    return DependencyResolverPath(this, segment)
  }

  companion object {
    fun from(component: Component): DependencyResolverPath {
      return DependencyResolverPath(null, newSegment(component))
    }

    fun from(contractConfiguration: ContractConfiguration): DependencyResolverPath {
      return DependencyResolverPath(null, newSegment(contractConfiguration))
    }

    private fun newSegment(component: Component): String {
      return newSegment(component.type, "Component")
    }

    private fun newSegment(contractConfiguration: ContractConfiguration): String {
      return newSegment(contractConfiguration.type, "ContractConfiguration")
    }

    private fun newSegment(module: Module): String {
      return newSegment(module.type, "Module")
    }

    private fun newSegment(injectionTarget: InjectionTarget): String {
      return newSegment(injectionTarget.type, "Class")
    }

    private fun newSegment(injectionPoint: InjectionPoint): String {
      return when (injectionPoint) {
        is InjectionPoint.Method -> newSegment(injectionPoint.method)
        is InjectionPoint.Field -> newSegment(injectionPoint.field)
      }
    }

    private fun newSegment(binding: Binding): String {
      return newSegment(binding.ancestor, "Binding")
    }

    private fun newSegment(factory: Factory): String {
      return newSegment(factory.type, "Factory")
    }

    private fun newSegment(factoryProvisionPoint: FactoryProvisionPoint): String {
      return newSegment(factoryProvisionPoint.method)
    }

    private fun newSegment(factoryInjectionPoint: FactoryInjectionPoint): String {
      return newSegment(factoryInjectionPoint.method)
    }

    private fun newSegment(contract: Contract): String {
      return newSegment(contract.type, "Contract")
    }

    private fun newSegment(contractProvisionPoint: ContractProvisionPoint): String {
      return newSegment(contractProvisionPoint.method)
    }

    private fun newSegment(importPoint: ImportPoint): String {
      return when (importPoint) {
        is ImportPoint.Method -> newSegment(importPoint.method)
        is ImportPoint.Field -> newSegment(importPoint.field)
        is ImportPoint.Annotation -> newSegment(importPoint.annotation)
      }
    }

    private fun newSegment(dependency: Dependency, name: String): String {
      return newSegment(dependency.getDescription(), name)
    }

    private fun newSegment(type: Type, name: String): String {
      return newSegment(type.getDescription(), name)
    }

    private fun newSegment(annotation: AnnotationMirror, name: String = "Annotation"): String {
      return newSegment(annotation.getDescription(), name)
    }

    private fun newSegment(method: MethodMirror, name: String = "Method"): String {
      return newSegment(method.getDescription(), name)
    }

    private fun newSegment(field: FieldMirror, name: String = "Field"): String {
      return newSegment(field.getDescription(), name)
    }

    private fun newSegment(value: String, name: String): String {
      return "$name: $value"
    }
  }
}
