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

import com.joom.lightsaber.processor.model.Binding
import com.joom.lightsaber.processor.model.Component
import com.joom.lightsaber.processor.model.Contract
import com.joom.lightsaber.processor.model.ContractConfiguration
import com.joom.lightsaber.processor.model.ContractProvisionPoint
import com.joom.lightsaber.processor.model.Factory
import com.joom.lightsaber.processor.model.FactoryInjectionPoint
import com.joom.lightsaber.processor.model.FactoryProvisionPoint
import com.joom.lightsaber.processor.model.ImportPoint
import com.joom.lightsaber.processor.model.InjectionPoint
import com.joom.lightsaber.processor.model.InjectionTarget
import com.joom.lightsaber.processor.model.Module
import com.joom.lightsaber.processor.model.ProvisionPoint

data class DependencyResolverPath(
  val path: DependencyResolverPath?,
  val segment: DependencyResolverPathSegment
) {

  fun getDescription(indent: String = "", separator: String = "\n"): String {
    fun StringBuilder.appendPath(path: DependencyResolverPath): StringBuilder = apply {
      path.path?.let { appendPath(it).append(separator) }
      append(indent)
      path.segment.appendDescriptionTo(this)
    }

    return buildString {
      appendPath(this@DependencyResolverPath)
    }
  }

  fun with(module: Module): DependencyResolverPath {
    return DependencyResolverPath(this, DependencyResolverPathSegment.create(module))
  }

  fun with(provisionPoint: ProvisionPoint): DependencyResolverPath {
    return when (provisionPoint) {
      is ProvisionPoint.Constructor ->
        with(DependencyResolverPathSegment.create(provisionPoint.containerType, "Class"))
          .with(DependencyResolverPathSegment.create(provisionPoint.injectionPoint.method, "Constructor"))
      is ProvisionPoint.Method -> with(DependencyResolverPathSegment.create(provisionPoint.method))
      is ProvisionPoint.Field -> with(DependencyResolverPathSegment.create(provisionPoint.field))
    }
  }

  fun with(injectionTarget: InjectionTarget): DependencyResolverPath {
    return with(DependencyResolverPathSegment.create(injectionTarget))
  }

  fun with(injectionPoint: InjectionPoint): DependencyResolverPath {
    return with(DependencyResolverPathSegment.create(injectionPoint))
  }

  fun with(binding: Binding): DependencyResolverPath {
    return with(DependencyResolverPathSegment.create(binding))
  }

  fun with(factory: Factory): DependencyResolverPath {
    return with(DependencyResolverPathSegment.create(factory))
  }

  fun with(factoryProvisionPoint: FactoryProvisionPoint): DependencyResolverPath {
    return with(DependencyResolverPathSegment.create(factoryProvisionPoint))
  }

  fun with(factoryInjectionPoint: FactoryInjectionPoint): DependencyResolverPath {
    return with(DependencyResolverPathSegment.create(factoryInjectionPoint))
  }

  fun with(contract: Contract): DependencyResolverPath {
    return with(DependencyResolverPathSegment.create(contract))
  }

  fun with(contractProvisionPoint: ContractProvisionPoint): DependencyResolverPath {
    return with(DependencyResolverPathSegment.create(contractProvisionPoint))
  }

  fun with(importPoint: ImportPoint): DependencyResolverPath {
    return with(DependencyResolverPathSegment.create(importPoint))
  }

  private fun with(segment: DependencyResolverPathSegment): DependencyResolverPath {
    return DependencyResolverPath(this, segment)
  }

  companion object {
    fun from(component: Component): DependencyResolverPath {
      return DependencyResolverPath(null, DependencyResolverPathSegment.create(component))
    }

    fun from(contractConfiguration: ContractConfiguration): DependencyResolverPath {
      return DependencyResolverPath(null, DependencyResolverPathSegment.create(contractConfiguration))
    }

    fun from(module: Module): DependencyResolverPath {
      return DependencyResolverPath(null, DependencyResolverPathSegment.create(module))
    }
  }
}
