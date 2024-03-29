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

import com.joom.grip.mirrors.Type
import com.joom.grip.mirrors.getObjectType
import com.joom.lightsaber.Component
import com.joom.lightsaber.Contract
import com.joom.lightsaber.ContractConfiguration
import com.joom.lightsaber.Eager
import com.joom.lightsaber.Factory
import com.joom.lightsaber.Import
import com.joom.lightsaber.ImportedBy
import com.joom.lightsaber.Injector
import com.joom.lightsaber.Key
import com.joom.lightsaber.Module
import com.joom.lightsaber.Provide
import com.joom.lightsaber.ProvidedAs
import com.joom.lightsaber.ProvidedBy
import io.michaelrocks.bimap.BiMap
import io.michaelrocks.bimap.HashBiMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton
import java.lang.reflect.Type as JavaType

object Types {
  val OBJECT_TYPE = getObjectType<Any>()
  val STRING_TYPE = getObjectType<String>()
  val INJECT_TYPE = getObjectType<Inject>()
  val IMPORT_TYPE = getObjectType<Import>()
  val IMPORTED_BY_TYPE = getObjectType<ImportedBy>()
  val PROVIDE_TYPE = getObjectType<Provide>()
  val PROVIDED_AS_TYPE = getObjectType<ProvidedAs>()
  val PROVIDED_BY_TYPE = getObjectType<ProvidedBy>()
  val COMPONENT_TYPE = getObjectType<Component>()
  val COMPONENT_NONE_TYPE = getObjectType<Component.None>()
  val MODULE_TYPE = getObjectType<Module>()
  val QUALIFIER_TYPE = getObjectType<Qualifier>()
  val SINGLETON_TYPE = getObjectType<Singleton>()
  val EAGER_TYPE = getObjectType<Eager>()
  val FACTORY_TYPE = getObjectType<Factory>()
  val FACTORY_INJECT_TYPE = getObjectType<Factory.Inject>()
  val FACTORY_PARAMETER_TYPE = getObjectType<Factory.Parameter>()
  val FACTORY_RETURN_TYPE = getObjectType<Factory.Return>()
  val CONTRACT_TYPE = getObjectType<Contract>()
  val CONTRACT_CONFIGURATION_TYPE = getObjectType<ContractConfiguration<*>>()
  val INJECTOR_TYPE = getObjectType<Injector>()
  val PROVIDER_TYPE = getObjectType<Provider<*>>()
  val LAZY_TYPE = getObjectType<com.joom.lightsaber.Lazy<*>>()
  val KEY_TYPE = getObjectType<Key<*>>()
  val CLASS_TYPE = getObjectType<Class<*>>()
  val TYPE_TYPE = getObjectType<JavaType>()
  val ANNOTATION_TYPE = getObjectType<Annotation>()
  val KOTLIN_LAZY_TYPE = getObjectType<Lazy<*>>()

  val BOXED_VOID_TYPE = getObjectType<Void>()
  val BOXED_BOOLEAN_TYPE = getObjectType<Boolean>()
  val BOXED_BYTE_TYPE = getObjectType<Byte>()
  val BOXED_CHAR_TYPE = getObjectType<Char>()
  val BOXED_DOUBLE_TYPE = getObjectType<Double>()
  val BOXED_FLOAT_TYPE = getObjectType<Float>()
  val BOXED_INT_TYPE = getObjectType<Int>()
  val BOXED_LONG_TYPE = getObjectType<Long>()
  val BOXED_SHORT_TYPE = getObjectType<Short>()

  private val primitiveToBoxedMap: BiMap<Type, Type.Object>

  init {
    primitiveToBoxedMap = HashBiMap()
    primitiveToBoxedMap.put(Type.Primitive.Void, BOXED_VOID_TYPE)
    primitiveToBoxedMap.put(Type.Primitive.Boolean, BOXED_BOOLEAN_TYPE)
    primitiveToBoxedMap.put(Type.Primitive.Byte, BOXED_BYTE_TYPE)
    primitiveToBoxedMap.put(Type.Primitive.Char, BOXED_CHAR_TYPE)
    primitiveToBoxedMap.put(Type.Primitive.Double, BOXED_DOUBLE_TYPE)
    primitiveToBoxedMap.put(Type.Primitive.Float, BOXED_FLOAT_TYPE)
    primitiveToBoxedMap.put(Type.Primitive.Int, BOXED_INT_TYPE)
    primitiveToBoxedMap.put(Type.Primitive.Long, BOXED_LONG_TYPE)
    primitiveToBoxedMap.put(Type.Primitive.Short, BOXED_SHORT_TYPE)
  }

  fun box(type: Type.Primitive): Type.Object = primitiveToBoxedMap[type]!!
  fun box(type: Type): Type = primitiveToBoxedMap[type] ?: type
  fun unbox(type: Type): Type = primitiveToBoxedMap.inverse[type] ?: type
}

fun Type.Primitive.boxed(): Type.Object = Types.box(this)
fun Type.boxed(): Type = Types.box(this)
fun Type.unboxed(): Type = Types.unbox(this)

fun Type.boxedOrElementType(): Type.Object {
  return when (this) {
    is Type.Primitive -> boxed()
    is Type.Array -> elementType.boxedOrElementType()
    is Type.Object -> this
    is Type.Method -> throw IllegalArgumentException("Cannot extract an object type from $this")
  }
}
