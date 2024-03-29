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

package com.joom.lightsaber.processor.commons

import com.joom.grip.ClassRegistry
import com.joom.grip.mirrors.ClassMirror
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.getObjectTypeByInternalName
import com.joom.lightsaber.processor.logging.getLogger
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter

class StandaloneClassWriter : ClassWriter {
  private val logger = getLogger()
  private val classRegistry: ClassRegistry

  constructor(flags: Int, classRegistry: ClassRegistry) : super(flags) {
    this.classRegistry = classRegistry
  }

  constructor(classReader: ClassReader, flags: Int, classRegistry: ClassRegistry) : super(classReader, flags) {
    this.classRegistry = classRegistry
  }

  override fun getCommonSuperClass(type1: String, type2: String): String {
    val hierarchy = HashSet<Type>()
    for (mirror in classRegistry.findClassHierarchy(getObjectTypeByInternalName(type1))) {
      hierarchy.add(mirror.type)
    }

    for (mirror in classRegistry.findClassHierarchy(getObjectTypeByInternalName(type2))) {
      if (mirror.type in hierarchy) {
        logger.debug("[getCommonSuperClass]: {} & {} = {}", type1, type2, mirror.access)
        return mirror.type.internalName
      }
    }

    logger.warn("[getCommonSuperClass]: {} & {} = NOT FOUND ", type1, type2)
    return Types.OBJECT_TYPE.internalName
  }

  private fun ClassRegistry.findClassHierarchy(type: Type.Object): Sequence<ClassMirror> {
    return generateSequence(getClassMirror(type)) {
      it.superType?.let { getClassMirror(it) }
    }
  }
}
