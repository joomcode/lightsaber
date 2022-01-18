package com.joom.lightsaber.processor.validation

import com.joom.grip.ClassRegistry
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.mirrors.Type
import com.joom.lightsaber.Provide
import com.joom.lightsaber.ProvidedAs
import com.joom.lightsaber.ProvidedBy
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.rawType
import com.joom.lightsaber.processor.model.Dependency
import javax.inject.Inject

class HintsBuilder(private val classRegistry: ClassRegistry) {

  fun buildHint(dependency: Dependency): String? {
    val mirror = dependency.type.rawType.getClassMirror() ?: return null

    return when {
      !mirror.isProvidedByAnnotationExist() -> "\n\t${dependency.type}: Using @${ProvidedAs::class.simpleName} without @${ProvidedBy::class.simpleName}. " +
          "You must either add @${ProvidedBy::class.simpleName} annotation or " +
          "add @${Provide::class.simpleName} method\n"
      !mirror.isAnyInjectableConstructorExist() -> "\n\t${dependency.type}: Using @${ProvidedBy::class.simpleName} without @${Inject::class.simpleName}. " +
          "You must either add at least one constructor with @${Inject::class.simpleName} annotation or " +
          "add @${Provide::class.simpleName} method\n"

      else -> null
    }
  }

  private fun Type.getClassMirror(): ClassMirror? {
    val objectType = this as? Type.Object ?: return null
    return classRegistry.getClassMirror(objectType)
  }

  private fun ClassMirror.isAnyInjectableConstructorExist(): Boolean {
    return constructors.any { constructor ->
      constructor.annotations[Types.INJECT_TYPE] != null
    }
  }

  private fun ClassMirror.isProvidedByAnnotationExist(): Boolean {
    return annotations[Types.PROVIDED_BY_TYPE] != null
  }
}
