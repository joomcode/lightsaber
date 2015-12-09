/*
 * Copyright 2015 Michael Rozumyanskiy
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

package io.michaelrocks.lightsaber.processor.files

import io.michaelrocks.lightsaber.processor.descriptors.ClassDescriptor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.util.*

interface ClassRegistry {
  fun findClass(type: Type): ClassDescriptor
  fun findClassHierarchy(type: Type): Sequence<ClassDescriptor>
}

class ClassRegistryImpl(private val fileRegistry: FileRegistry) : ClassRegistry {
  private val resolvedClasses = HashMap<Type, ClassDescriptor>()

  override fun findClass(type: Type): ClassDescriptor {
    return resolvedClasses.getOrPut(type) {
      val data = fileRegistry.readClass(type)
      val reader = ClassReader(data)
      val visitor = ClassDescriptorReader()
      reader.accept(visitor, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
      visitor.descriptor
    }
  }

  override fun findClassHierarchy(type: Type): Sequence<ClassDescriptor> {
    return sequence(findClass(type)) {
      it.superType?.let { findClass(it) }
    }
  }

  private class ClassDescriptorReader : ClassVisitor(Opcodes.ASM5) {
    lateinit var descriptor: ClassDescriptor

    override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?,
        interfaces: Array<String>?) {
      descriptor = ClassDescriptor(access, name, superName, interfaces)
    }
  }
}