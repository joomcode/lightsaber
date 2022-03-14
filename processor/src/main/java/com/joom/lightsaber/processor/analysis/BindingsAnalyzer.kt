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

package com.joom.lightsaber.processor.analysis

import com.joom.grip.Grip
import com.joom.grip.annotatedWith
import com.joom.grip.classes
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.signature.GenericType
import com.joom.lightsaber.ProvidedAs
import com.joom.lightsaber.processor.ErrorReporter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.model.Binding
import com.joom.lightsaber.processor.model.Dependency
import java.io.File
import java.nio.file.Path

interface BindingsAnalyzer {
  fun analyze(files: Collection<File>): BindingRegistry {
    return analyzePaths(files.map { it.toPath() })
  }

  fun analyzePaths(paths: Collection<Path>): BindingRegistry
}

class BindingsAnalyzerImpl(
  private val grip: Grip,
  private val analyzerHelper: AnalyzerHelper,
  private val errorReporter: ErrorReporter
) : BindingsAnalyzer {

  override fun analyzePaths(paths: Collection<Path>): BindingRegistry {
    val bindingRegistry = BindingRegistryImpl()
    val bindingsQuery = grip select classes from paths where annotatedWith(Types.PROVIDED_AS_TYPE)
    bindingsQuery.execute().classes.forEach { mirror ->
      createBindingsForClass(mirror).forEach { binding ->
        bindingRegistry.registerBinding(binding)
      }
    }

    return bindingRegistry
  }

  private fun createBindingsForClass(mirror: ClassMirror): Collection<Binding> {
    return extractAncestorTypesFromClass(mirror).map { ancestorType ->
      val dependency = Dependency(GenericType.Raw(mirror.type))
      val qualifier = analyzerHelper.findQualifier(mirror)
      val ancestor = Dependency(GenericType.Raw(ancestorType), qualifier)
      Binding(dependency, ancestor)
    }
  }

  private fun extractAncestorTypesFromClass(mirror: ClassMirror): Collection<Type.Object> {
    val providedAs = mirror.annotations[Types.PROVIDED_AS_TYPE] ?: return emptyList()

    @Suppress("UNCHECKED_CAST")
    val ancestorTypes = providedAs.values[ProvidedAs::value.name] as? List<*>
    if (ancestorTypes == null) {
      error("Class ${mirror.type.className} has invalid type in its @ProvidedAs annotation: $ancestorTypes")
      return emptyList()
    }

    return ancestorTypes.mapNotNull {
      val ancestorType = it as? Type.Object
      if (ancestorType == null) {
        error("Class ${mirror.type.className} has a non-class type in its @ProvidedAs annotation: $it")
        return@mapNotNull null
      }

      ancestorType
    }
  }

  private fun error(message: String) {
    errorReporter.reportError(message)
  }
}
