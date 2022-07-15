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

package com.joom.lightsaber

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider

class LazyContractImportTest {
  @Test
  fun testComplexContract() {
    val lightsaber = Lightsaber.Builder().build()
    val contract1 = lazy {
      ComplexContractImpl(
        defaultBoolean = true,
        defaultByte = 42,
        defaultChar = 'x',
        defaultDouble = Double.MAX_VALUE,
        defaultFloat = Float.MAX_VALUE,
        defaultInt = 42,
        defaultLong = 42L,
        defaultShort = 42,
        defaultString = "String",
        defaultBooleanArray = booleanArrayOf(true),
        defaultByteArray = byteArrayOf(42),
        defaultCharArray = charArrayOf('x'),
        defaultDoubleArray = doubleArrayOf(Double.MAX_VALUE),
        defaultFloatArray = floatArrayOf(Float.MAX_VALUE),
        defaultIntArray = intArrayOf(42),
        defaultLongArray = longArrayOf(42L),
        defaultShortArray = shortArrayOf(42),
        defaultStringArray = arrayOf("String"),
        defaultIntList = listOf(42),
        defaultStringList = listOf("String"),
        annotatedBoolean = false,
        annotatedByte = -42,
        annotatedChar = 'y',
        annotatedDouble = Double.MIN_VALUE,
        annotatedFloat = Float.MIN_VALUE,
        annotatedInt = -42,
        annotatedLong = -42L,
        annotatedShort = -42,
        annotatedString = "Annotated String",
        annotatedBooleanArray = booleanArrayOf(false),
        annotatedByteArray = byteArrayOf(-42),
        annotatedCharArray = charArrayOf('y'),
        annotatedDoubleArray = doubleArrayOf(Double.MIN_VALUE),
        annotatedFloatArray = floatArrayOf(Float.MIN_VALUE),
        annotatedIntArray = intArrayOf(-42),
        annotatedLongArray = longArrayOf(-42L),
        annotatedShortArray = shortArrayOf(-42),
        annotatedStringArray = arrayOf("Annotated", "String"),
        annotatedIntList = listOf(-42),
        annotatedStringList = listOf("Annotated", "String")
      )
    }
    val component = ContractComponent(contract1)
    val injector = lightsaber.createInjector(component)
    val contract2 = injector.getInstance<ComplexContractImpl>()


    assertNotSame(contract1.value, contract2)

    assertEquals(contract1.value.defaultBoolean, contract2.defaultBoolean)
    assertEquals(contract1.value.defaultByte, contract2.defaultByte)
    assertEquals(contract1.value.defaultChar, contract2.defaultChar)
    assertEquals(contract1.value.defaultDouble, contract2.defaultDouble, 0.0)
    assertEquals(contract1.value.defaultFloat, contract2.defaultFloat, 0f)
    assertEquals(contract1.value.defaultInt, contract2.defaultInt)
    assertEquals(contract1.value.defaultLong, contract2.defaultLong)
    assertEquals(contract1.value.defaultShort, contract2.defaultShort)
    assertEquals(contract1.value.defaultString, contract2.defaultString)
    assertArrayEquals(contract1.value.defaultBooleanArray, contract2.defaultBooleanArray)
    assertArrayEquals(contract1.value.defaultByteArray, contract2.defaultByteArray)
    assertArrayEquals(contract1.value.defaultCharArray, contract2.defaultCharArray)
    assertArrayEquals(contract1.value.defaultDoubleArray, contract2.defaultDoubleArray, 0.0)
    assertArrayEquals(contract1.value.defaultFloatArray, contract2.defaultFloatArray, 0f)
    assertArrayEquals(contract1.value.defaultIntArray, contract2.defaultIntArray)
    assertArrayEquals(contract1.value.defaultLongArray, contract2.defaultLongArray)
    assertArrayEquals(contract1.value.defaultShortArray, contract2.defaultShortArray)
    assertArrayEquals(contract1.value.defaultStringArray, contract2.defaultStringArray)
    assertEquals(contract1.value.defaultIntList, contract2.defaultIntList)
    assertEquals(contract1.value.defaultStringList, contract2.defaultStringList)

    assertEquals(contract1.value.annotatedByte, contract2.annotatedByte)
    assertEquals(contract1.value.annotatedChar, contract2.annotatedChar)
    assertEquals(contract1.value.annotatedDouble, contract2.annotatedDouble, 0.0)
    assertEquals(contract1.value.annotatedFloat, contract2.annotatedFloat, 0f)
    assertEquals(contract1.value.annotatedInt, contract2.annotatedInt)
    assertEquals(contract1.value.annotatedLong, contract2.annotatedLong)
    assertEquals(contract1.value.annotatedShort, contract2.annotatedShort)
    assertEquals(contract1.value.annotatedString, contract2.annotatedString)
    assertArrayEquals(contract1.value.annotatedBooleanArray, contract2.annotatedBooleanArray)
    assertArrayEquals(contract1.value.annotatedByteArray, contract2.annotatedByteArray)
    assertArrayEquals(contract1.value.annotatedCharArray, contract2.annotatedCharArray)
    assertArrayEquals(contract1.value.annotatedDoubleArray, contract2.annotatedDoubleArray, 0.0)
    assertArrayEquals(contract1.value.annotatedFloatArray, contract2.annotatedFloatArray, 0f)
    assertArrayEquals(contract1.value.annotatedIntArray, contract2.annotatedIntArray)
    assertArrayEquals(contract1.value.annotatedLongArray, contract2.annotatedLongArray)
    assertArrayEquals(contract1.value.annotatedShortArray, contract2.annotatedShortArray)
    assertArrayEquals(contract1.value.annotatedStringArray, contract2.annotatedStringArray)
    assertEquals(contract1.value.annotatedIntList, contract2.annotatedIntList)
    assertEquals(contract1.value.annotatedStringList, contract2.annotatedStringList)
  }

  @Test
  fun testBoxedPrimitivesInContract() {
    val lightsaber = Lightsaber.Builder().build()
    val contract1 = LazyAdapter {
      BoxedPrimitivesImpl(
        boxedBoolean = true,
        boxedByte = 42,
        boxedChar = 'x',
        boxedDouble = Double.MAX_VALUE,
        boxedFloat = Float.MAX_VALUE,
        boxedInt = 42,
        boxedLong = 42L,
        boxedShort = 42
      )
    }
    val component = BoxedPrimitivesComponent(contract1 as Lazy<BoxedPrimitives>)
    val injector = lightsaber.createInjector(component)
    val contract2 = injector.getInstance<BoxedPrimitivesImpl>()

    assertNotSame(contract1.get(), contract2)
    assertEquals(contract1.get().boxedBoolean, contract2.boxedBoolean)
    assertEquals(contract1.get().boxedByte, contract2.boxedByte)
    assertEquals(contract1.get().boxedChar, contract2.boxedChar)
    assertEquals(contract1.get().boxedDouble, contract2.boxedDouble, 0.0)
    assertEquals(contract1.get().boxedFloat, contract2.boxedFloat, 0f)
    assertEquals(contract1.get().boxedInt, contract2.boxedInt)
    assertEquals(contract1.get().boxedLong, contract2.boxedLong)
    assertEquals(contract1.get().boxedShort, contract2.boxedShort)
  }

  @Test
  fun testProviderContract() {
    val lightsaber = Lightsaber.Builder().build()
    val contract = lazy {
      object : ProviderContract {
        override val stringProvider: Provider<String> = Provider { "String" }
        override val annotatedStringProvider: Provider<String> = Provider { "Annotated String" }
      }
    }
    val component = ProviderComponent(contract)
    val injector = lightsaber.createInjector(component)
    val strings = injector.getInstance<Strings>()

    assertEquals("String", strings.string)
    assertEquals("Annotated String", strings.annotatedString)
  }

  @Test
  fun testLazyContract() {
    val lightsaber = Lightsaber.Builder().build()
    val contract = LazyAdapter {
      object : LazyContract {
        override val stringLazy: Lazy<String> = Lazy { "String" }
        override val annotatedStringLazy: Lazy<String> = Lazy { "Annotated String" }
      }
    }

    val component = LazyComponent(contract as Lazy<LazyContract>)
    val injector = lightsaber.createInjector(component)
    val strings = injector.getInstance<Strings>()

    assertEquals("String", strings.string)
    assertEquals("Annotated String", strings.annotatedString)
  }

  @Test
  fun testOverrideResolvesTypeAmbiguity() {
    val lightsaber = Lightsaber.Builder().build()
    val contract = lazy { OverrideContractImpl() }
    val component = OverrideComponent1(contract)
    val injector = lightsaber.createInjector(component)

    assertResolves(injector, String::class.java)
    assertNotResolves(injector, CharSequence::class.java)
  }

  @Test
  fun testOverrideClearsQualifier() {
    val lightsaber = Lightsaber.Builder().build()
    val contract = LazyAdapter { OverrideContractImpl() }
    val component = OverrideComponent2(contract as Lazy<OverrideContract2>)
    val injector = lightsaber.createInjector(component)

    assertResolves(injector, String::class.java)
    assertNotResolves(injector, CharSequence::class.java)
    assertNotResolves(injector, String::class.java, named("Base"))
  }

  @Test
  fun testOverrideChangesType() {
    val lightsaber = Lightsaber.Builder().build()
    val contract = lazy { OverrideContractImpl() }
    val component = OverrideComponent3(contract)
    val injector = lightsaber.createInjector(component)

    assertResolves(injector, String::class.java)
    assertNotResolves(injector, CharSequence::class.java)
  }

  @Test
  fun testInheritanceDoesNotChangeTypeAndQualifier() {
    val lightsaber = Lightsaber.Builder().build()
    val contract = LazyAdapter { OverrideContractImpl() }
    val component = OverrideComponent4(contract as Lazy<OverrideContract4>)
    val injector = lightsaber.createInjector(component)

    assertNotResolves(injector, String::class.java)
    assertNotResolves(injector, CharSequence::class.java)
    assertResolves(injector, String::class.java, named("Base"))
  }

  @Test
  fun testOverrideChangesQualifier() {
    val lightsaber = Lightsaber.Builder().build()
    val contract = lazy { OverrideContractImpl() }
    val component = OverrideComponent5(contract)
    val injector = lightsaber.createInjector(component)

    assertNotResolves(injector, String::class.java)
    assertNotResolves(injector, CharSequence::class.java)
    assertNotResolves(injector, String::class.java, named("Base"))
    assertResolves(injector, String::class.java, named("Annotated"))
  }

  @Component
  class ContractComponent(
    @Import
    @Contract
    private val contract: kotlin.Lazy<ComplexContract>
  ) {

    @Provide
    fun provideMoreComplexContract(
      defaultBoolean: Boolean,
      defaultByte: Byte,
      defaultChar: Char,
      defaultDouble: Double,
      defaultFloat: Float,
      defaultInt: Int,
      defaultLong: Long,
      defaultShort: Short,
      defaultString: String,
      defaultBooleanArray: BooleanArray,
      defaultByteArray: ByteArray,
      defaultCharArray: CharArray,
      defaultDoubleArray: DoubleArray,
      defaultFloatArray: FloatArray,
      defaultIntArray: IntArray,
      defaultLongArray: LongArray,
      defaultShortArray: ShortArray,
      defaultStringArray: Array<String>,
      defaultIntList: List<Int>,
      defaultStringList: List<String>,
      @Named("Annotated") annotatedBoolean: Boolean,
      @Named("Annotated") annotatedByte: Byte,
      @Named("Annotated") annotatedChar: Char,
      @Named("Annotated") annotatedDouble: Double,
      @Named("Annotated") annotatedFloat: Float,
      @Named("Annotated") annotatedInt: Int,
      @Named("Annotated") annotatedLong: Long,
      @Named("Annotated") annotatedShort: Short,
      @Named("Annotated") annotatedString: String,
      @Named("Annotated") annotatedBooleanArray: BooleanArray,
      @Named("Annotated") annotatedByteArray: ByteArray,
      @Named("Annotated") annotatedCharArray: CharArray,
      @Named("Annotated") annotatedDoubleArray: DoubleArray,
      @Named("Annotated") annotatedFloatArray: FloatArray,
      @Named("Annotated") annotatedIntArray: IntArray,
      @Named("Annotated") annotatedLongArray: LongArray,
      @Named("Annotated") annotatedShortArray: ShortArray,
      @Named("Annotated") annotatedStringArray: Array<String>,
      @Named("Annotated") annotatedIntList: List<Int>,
      @Named("Annotated") annotatedStringList: List<String>
    ): ComplexContractImpl {
      return ComplexContractImpl(
        defaultBoolean,
        defaultByte,
        defaultChar,
        defaultDouble,
        defaultFloat,
        defaultInt,
        defaultLong,
        defaultShort,
        defaultString,
        defaultBooleanArray,
        defaultByteArray,
        defaultCharArray,
        defaultDoubleArray,
        defaultFloatArray,
        defaultIntArray,
        defaultLongArray,
        defaultShortArray,
        defaultStringArray,
        defaultIntList,
        defaultStringList,
        annotatedBoolean,
        annotatedByte,
        annotatedChar,
        annotatedDouble,
        annotatedFloat,
        annotatedInt,
        annotatedLong,
        annotatedShort,
        annotatedString,
        annotatedBooleanArray,
        annotatedByteArray,
        annotatedCharArray,
        annotatedDoubleArray,
        annotatedFloatArray,
        annotatedIntArray,
        annotatedLongArray,
        annotatedShortArray,
        annotatedStringArray,
        annotatedIntList,
        annotatedStringList
      )
    }
  }

  @Contract
  @ProvidedBy(ContractComponent::class)
  interface ComplexContract {

    val defaultBoolean: Boolean
    val defaultByte: Byte
    val defaultChar: Char
    val defaultDouble: Double
    val defaultFloat: Float
    val defaultInt: Int
    val defaultLong: Long
    val defaultShort: Short
    val defaultString: String
    val defaultBooleanArray: BooleanArray
    val defaultByteArray: ByteArray
    val defaultCharArray: CharArray
    val defaultDoubleArray: DoubleArray
    val defaultFloatArray: FloatArray
    val defaultIntArray: IntArray
    val defaultLongArray: LongArray
    val defaultShortArray: ShortArray
    val defaultStringArray: Array<String>
    val defaultIntList: List<Int>
    val defaultStringList: List<String>
    @get:Named("Annotated") val annotatedBoolean: Boolean
    @get:Named("Annotated") val annotatedByte: Byte
    @get:Named("Annotated") val annotatedChar: Char
    @get:Named("Annotated") val annotatedDouble: Double
    @get:Named("Annotated") val annotatedFloat: Float
    @get:Named("Annotated") val annotatedInt: Int
    @get:Named("Annotated") val annotatedLong: Long
    @get:Named("Annotated") val annotatedShort: Short
    @get:Named("Annotated") val annotatedString: String
    @get:Named("Annotated") val annotatedBooleanArray: BooleanArray
    @get:Named("Annotated") val annotatedByteArray: ByteArray
    @get:Named("Annotated") val annotatedCharArray: CharArray
    @get:Named("Annotated") val annotatedDoubleArray: DoubleArray
    @get:Named("Annotated") val annotatedFloatArray: FloatArray
    @get:Named("Annotated") val annotatedIntArray: IntArray
    @get:Named("Annotated") val annotatedLongArray: LongArray
    @get:Named("Annotated") val annotatedShortArray: ShortArray
    @get:Named("Annotated") val annotatedStringArray: Array<String>
    @get:Named("Annotated") val annotatedIntList: List<Int>
    @get:Named("Annotated") val annotatedStringList: List<String>
  }

  class ComplexContractImpl(
    override val defaultBoolean: Boolean,
    override val defaultByte: Byte,
    override val defaultChar: Char,
    override val defaultDouble: Double,
    override val defaultFloat: Float,
    override val defaultInt: Int,
    override val defaultLong: Long,
    override val defaultShort: Short,
    override val defaultString: String,
    override val defaultBooleanArray: BooleanArray,
    override val defaultByteArray: ByteArray,
    override val defaultCharArray: CharArray,
    override val defaultDoubleArray: DoubleArray,
    override val defaultFloatArray: FloatArray,
    override val defaultIntArray: IntArray,
    override val defaultLongArray: LongArray,
    override val defaultShortArray: ShortArray,
    override val defaultStringArray: Array<String>,
    override val defaultIntList: List<Int>,
    override val defaultStringList: List<String>,
    @get:Named("Annotated") override val annotatedBoolean: Boolean,
    @get:Named("Annotated") override val annotatedByte: Byte,
    @get:Named("Annotated") override val annotatedChar: Char,
    @get:Named("Annotated") override val annotatedDouble: Double,
    @get:Named("Annotated") override val annotatedFloat: Float,
    @get:Named("Annotated") override val annotatedInt: Int,
    @get:Named("Annotated") override val annotatedLong: Long,
    @get:Named("Annotated") override val annotatedShort: Short,
    @get:Named("Annotated") override val annotatedString: String,
    @get:Named("Annotated") override val annotatedBooleanArray: BooleanArray,
    @get:Named("Annotated") override val annotatedByteArray: ByteArray,
    @get:Named("Annotated") override val annotatedCharArray: CharArray,
    @get:Named("Annotated") override val annotatedDoubleArray: DoubleArray,
    @get:Named("Annotated") override val annotatedFloatArray: FloatArray,
    @get:Named("Annotated") override val annotatedIntArray: IntArray,
    @get:Named("Annotated") override val annotatedLongArray: LongArray,
    @get:Named("Annotated") override val annotatedShortArray: ShortArray,
    @get:Named("Annotated") override val annotatedStringArray: Array<String>,
    @get:Named("Annotated") override val annotatedIntList: List<Int>,
    @get:Named("Annotated") override val annotatedStringList: List<String>
  ) : ComplexContract

  @Component
  class BoxedPrimitivesComponent(
    @Import @Contract private val boxedPrimitives: Lazy<BoxedPrimitives>
  ) {

    @Provide
    private fun provideBoxedPrimitivesImpl(
      defaultBoolean: Boolean,
      defaultByte: Byte,
      defaultChar: Char,
      defaultDouble: Double,
      defaultFloat: Float,
      defaultInt: Int,
      defaultLong: Long,
      defaultShort: Short
    ): BoxedPrimitivesImpl {
      return BoxedPrimitivesImpl(
        defaultBoolean,
        defaultByte,
        defaultChar,
        defaultDouble,
        defaultFloat,
        defaultInt,
        defaultLong,
        defaultShort
      )
    }
  }

  interface BoxedPrimitives {
    val boxedBoolean: Boolean?
    val boxedByte: Byte?
    val boxedChar: Char?
    val boxedDouble: Double?
    val boxedFloat: Float?
    val boxedInt: Int?
    val boxedLong: Long?
    val boxedShort: Short?
  }

  class BoxedPrimitivesImpl(
    override val boxedBoolean: Boolean,
    override val boxedByte: Byte,
    override val boxedChar: Char,
    override val boxedDouble: Double,
    override val boxedFloat: Float,
    override val boxedInt: Int,
    override val boxedLong: Long,
    override val boxedShort: Short
  ) : BoxedPrimitives

  @Component
  class ProviderComponent(
    @Import @Contract private val providerContract: kotlin.Lazy<ProviderContract>
  )

  interface ProviderContract {
    val stringProvider: Provider<String>
    @get:Named("Annotated") val annotatedStringProvider: Provider<String>
  }

  @Component
  class LazyComponent(
    @Import @Contract private val lazyContract: Lazy<LazyContract>
  )

  interface LazyContract {
    val stringLazy: Lazy<String>
    @get:Named("Annotated") val annotatedStringLazy: Lazy<String>
  }

  @ProvidedBy(ProviderComponent::class, LazyComponent::class)
  class Strings @Inject constructor(
    val string: String,
    @Named("Annotated") val annotatedString: String
  )

  @Component
  class OverrideComponent1(@Import @Contract private val contract: kotlin.Lazy<OverrideContract1>)

  @Component
  class OverrideComponent2(@Import @Contract private val contract: Lazy<OverrideContract2>)

  @Component
  class OverrideComponent3(@Import @Contract private val contract: kotlin.Lazy<OverrideContract3>)

  @Component
  class OverrideComponent4(@Import @Contract private val contract: Lazy<OverrideContract4>)

  @Component
  class OverrideComponent5(@Import @Contract private val contract: kotlin.Lazy<OverrideContract5>)

  interface BaseContract1 {
    val string: CharSequence
  }

  interface BaseContract2 {
    val string: String
  }

  interface BaseContract3 : BaseContract2 {
    @get:Named("Base")
    override val string: String
  }

  interface OverrideContract1 : BaseContract1, BaseContract2 {
    override val string: String
  }

  interface OverrideContract2 : BaseContract1, BaseContract3 {
    override val string: String
  }

  interface OverrideContract3 : BaseContract1 {
    override val string: String
  }

  interface OverrideContract4 : BaseContract3

  interface OverrideContract5 : BaseContract3 {
    @get:Named("Annotated")
    override val string: String
  }

  class OverrideContractImpl : OverrideContract1, OverrideContract2, OverrideContract3, OverrideContract4, OverrideContract5 {
    override val string: String get() = "String"
  }
}
