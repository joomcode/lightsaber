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

package com.joom.lightsaber.processor.generation

import com.joom.grip.ClassRegistry
import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.getObjectType
import com.joom.grip.mirrors.signature.GenericType
import com.joom.grip.mirrors.toArrayType
import com.joom.lightsaber.internal.GenericArrayTypeImpl
import com.joom.lightsaber.internal.ParameterizedTypeImpl
import com.joom.lightsaber.internal.WildcardTypeImpl
import com.joom.lightsaber.processor.annotations.proxy.AnnotationCreator
import com.joom.lightsaber.processor.commons.GeneratorAdapter
import com.joom.lightsaber.processor.commons.StandaloneClassWriter
import com.joom.lightsaber.processor.commons.Types
import com.joom.lightsaber.processor.commons.boxed
import com.joom.lightsaber.processor.commons.newDefaultConstructor
import com.joom.lightsaber.processor.descriptors.MethodDescriptor
import com.joom.lightsaber.processor.descriptors.descriptor
import com.joom.lightsaber.processor.generation.model.GenerationContext
import com.joom.lightsaber.processor.generation.model.Key
import com.joom.lightsaber.processor.generation.model.PackageInvader
import com.joom.lightsaber.processor.model.Dependency
import com.joom.lightsaber.processor.watermark.WatermarkClassVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACC_SUPER
import org.objectweb.asm.Opcodes.V1_6

private val KEY_CONSTRUCTOR = MethodDescriptor.forConstructor(Types.TYPE_TYPE, Types.ANNOTATION_TYPE)

private val PARAMETERIZED_TYPE_IMPL_TYPE = getObjectType<ParameterizedTypeImpl>()
private val GENERIC_ARRAY_TYPE_IMPL_TYPE = getObjectType<GenericArrayTypeImpl>()
private val WILDCARD_TYPE_IMPL_TYPE = getObjectType<WildcardTypeImpl>()

private val PARAMETERIZED_TYPE_IMPL_CONSTRUCTOR =
  MethodDescriptor.forConstructor(Types.TYPE_TYPE, Types.CLASS_TYPE, Types.TYPE_TYPE.toArrayType())
private val GENERIC_ARRAY_TYPE_IMPL_CONSTRUCTOR =
  MethodDescriptor.forConstructor(Types.TYPE_TYPE)
private val WILDCARD_TYPE_IMPL_CONSTRUCTOR =
  MethodDescriptor.forConstructor(Types.TYPE_TYPE, Types.TYPE_TYPE)

class KeyRegistryClassGenerator(
  private val classProducer: ClassProducer,
  private val classRegistry: ClassRegistry,
  private val annotationCreator: AnnotationCreator,
  private val generationContext: GenerationContext
) {

  private val keyRegistry = generationContext.keyRegistry

  fun generate() {
    val classWriter = StandaloneClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS, classRegistry)
    val classVisitor = WatermarkClassVisitor(classWriter, true)
    classVisitor.visit(
      V1_6,
      ACC_PUBLIC or ACC_SUPER,
      keyRegistry.type.internalName,
      null,
      Types.OBJECT_TYPE.internalName,
      null
    )

    generateFields(classVisitor)
    generateStaticInitializer(classVisitor)
    classVisitor.newDefaultConstructor()

    classVisitor.visitEnd()
    val classBytes = classWriter.toByteArray()
    classProducer.produceClass(keyRegistry.type.internalName, classBytes)
  }

  private fun generateFields(classVisitor: ClassVisitor) {
    for (key in keyRegistry.keys.values) {
      val field = key.field
      val fieldVisitor = classVisitor.visitField(
        ACC_PUBLIC or ACC_STATIC or ACC_FINAL,
        field.name,
        field.descriptor,
        null,
        null
      )
      fieldVisitor.visitEnd()
    }
  }

  private fun generateStaticInitializer(classVisitor: ClassVisitor) {
    val staticInitializer = MethodDescriptor.forStaticInitializer()
    val generator = GeneratorAdapter(classVisitor, ACC_STATIC, staticInitializer)
    generator.visitCode()

    for ((dependency, key) in keyRegistry.keys.entries) {
      generator.pushInstanceOfKey(dependency, key)
      generator.putStatic(keyRegistry.type, key.field)
    }

    generator.returnValue()
    generator.endMethod()
  }

  private fun GeneratorAdapter.pushInstanceOfKey(dependency: Dependency, key: Key) {
    when (key) {
      is Key.QualifiedType -> newKey(dependency)
      is Key.Type -> push(dependency.type)
    }
  }

  private fun GeneratorAdapter.newKey(dependency: Dependency) {
    newInstance(Types.KEY_TYPE)
    dup()

    push(dependency.type)
    if (dependency.qualifier == null) {
      pushNull()
    } else {
      annotationCreator.newAnnotation(this, dependency.qualifier)
    }

    invokeConstructor(Types.KEY_TYPE, KEY_CONSTRUCTOR)
  }

  private fun GeneratorAdapter.push(type: GenericType) {
    when (type) {
      is GenericType.Raw -> pushType(type.type.boxed())
      is GenericType.Parameterized -> newParameterizedType(type)
      is GenericType.Array -> newGenericArrayType(type)
      is GenericType.LowerBounded -> newWildcardType(lowerBound = type.lowerBound, upperBound = null)
      is GenericType.UpperBounded -> newWildcardType(lowerBound = null, upperBound = type.upperBound)
      else -> error("Unsupported generic type $type")
    }
  }

  private fun GeneratorAdapter.newParameterizedType(type: GenericType.Parameterized) {
    newInstance(PARAMETERIZED_TYPE_IMPL_TYPE)
    dup()
    pushNull()
    pushType(type.type)
    newArray(Types.TYPE_TYPE, type.typeArguments.size)
    type.typeArguments.forEachIndexed { index, typeArgument ->
      dup()
      push(index)
      push(typeArgument)
      arrayStore(Types.TYPE_TYPE)
    }
    invokeConstructor(PARAMETERIZED_TYPE_IMPL_TYPE, PARAMETERIZED_TYPE_IMPL_CONSTRUCTOR)
  }

  private fun GeneratorAdapter.newGenericArrayType(type: GenericType.Array) {
    newInstance(GENERIC_ARRAY_TYPE_IMPL_TYPE)
    dup()
    push(type.elementType)
    invokeConstructor(GENERIC_ARRAY_TYPE_IMPL_TYPE, GENERIC_ARRAY_TYPE_IMPL_CONSTRUCTOR)
  }

  private fun GeneratorAdapter.newWildcardType(lowerBound: GenericType?, upperBound: GenericType?) {
    newInstance(WILDCARD_TYPE_IMPL_TYPE)
    dup()
    if (lowerBound != null) push(lowerBound) else pushNull()
    if (upperBound != null) push(upperBound) else pushNull()
    invokeConstructor(WILDCARD_TYPE_IMPL_TYPE, WILDCARD_TYPE_IMPL_CONSTRUCTOR)
  }

  private fun GeneratorAdapter.pushType(rawType: Type) {
    val packageInvader = findPackageInvaderForType(rawType)
    val field = packageInvader?.fields?.get(rawType)

    if (field != null) {
      getStatic(packageInvader.type, field)
    } else {
      push(rawType.boxed())
    }
  }

  private fun findPackageInvaderForType(type: Type): PackageInvader? {
    val targetType = when (type) {
      is Type.Object -> type
      is Type.Array -> type.elementType as? Type.Object
      else -> null
    }

    return targetType?.let { generationContext.findPackageInvaderByTargetType(it) }
  }
}
