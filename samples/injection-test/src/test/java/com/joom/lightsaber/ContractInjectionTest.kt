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

package com.joom.lightsaber

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

class ContractInjectionTest {
  @Test
  fun testComplexContract() {
    val lightsaber = Lightsaber.Builder().build()
    val component = ContractComponent()
    val injector = lightsaber.createInjector(component)
    val contract = injector.getInstance<ComplexContract>()

    assertEquals(component.provideBoolean(), contract.boxedBoolean)
    assertEquals(component.provideByte(), contract.boxedByte)
    assertEquals(component.provideChar(), contract.boxedChar)
    assertEquals(component.provideDouble(), contract.boxedDouble)
    assertEquals(component.provideFloat(), contract.boxedFloat)
    assertEquals(component.provideInt(), contract.boxedInt)
    assertEquals(component.provideLong(), contract.boxedLong)
    assertEquals(component.provideShort(), contract.boxedShort)
    assertEquals(component.provideBoolean(), contract.defaultBoolean)
    assertEquals(component.provideByte(), contract.defaultByte)
    assertEquals(component.provideChar(), contract.defaultChar)
    assertEquals(component.provideDouble(), contract.defaultDouble, 0.0)
    assertEquals(component.provideFloat(), contract.defaultFloat, 0f)
    assertEquals(component.provideInt(), contract.defaultInt)
    assertEquals(component.provideLong(), contract.defaultLong)
    assertEquals(component.provideShort(), contract.defaultShort)
    assertEquals(component.provideString(), contract.defaultString)
    assertArrayEquals(component.provideBooleanArray(), contract.defaultBooleanArray)
    assertArrayEquals(component.provideByteArray(), contract.defaultByteArray)
    assertArrayEquals(component.provideCharArray(), contract.defaultCharArray)
    assertArrayEquals(component.provideDoubleArray(), contract.defaultDoubleArray, 0.0)
    assertArrayEquals(component.provideFloatArray(), contract.defaultFloatArray, 0f)
    assertArrayEquals(component.provideIntArray(), contract.defaultIntArray)
    assertArrayEquals(component.provideLongArray(), contract.defaultLongArray)
    assertArrayEquals(component.provideShortArray(), contract.defaultShortArray)
    assertArrayEquals(component.provideStringArray(), contract.defaultStringArray)
    assertEquals(component.provideIntList(), contract.defaultIntList)
    assertEquals(component.provideStringList(), contract.defaultStringList)

    assertEquals(component.provideAnnotatedBoolean(), contract.annotatedBoxedBoolean)
    assertEquals(component.provideAnnotatedByte(), contract.annotatedBoxedByte)
    assertEquals(component.provideAnnotatedChar(), contract.annotatedBoxedChar)
    assertEquals(component.provideAnnotatedDouble(), contract.annotatedBoxedDouble)
    assertEquals(component.provideAnnotatedFloat(), contract.annotatedBoxedFloat)
    assertEquals(component.provideAnnotatedInt(), contract.annotatedBoxedInt)
    assertEquals(component.provideAnnotatedLong(), contract.annotatedBoxedLong)
    assertEquals(component.provideAnnotatedShort(), contract.annotatedBoxedShort)
    assertEquals(component.provideAnnotatedBoolean(), contract.annotatedBoolean)
    assertEquals(component.provideAnnotatedByte(), contract.annotatedByte)
    assertEquals(component.provideAnnotatedChar(), contract.annotatedChar)
    assertEquals(component.provideAnnotatedDouble(), contract.annotatedDouble, 0.0)
    assertEquals(component.provideAnnotatedFloat(), contract.annotatedFloat, 0f)
    assertEquals(component.provideAnnotatedInt(), contract.annotatedInt)
    assertEquals(component.provideAnnotatedLong(), contract.annotatedLong)
    assertEquals(component.provideAnnotatedShort(), contract.annotatedShort)
    assertEquals(component.provideAnnotatedString(), contract.annotatedString)
    assertArrayEquals(component.provideAnnotatedBooleanArray(), contract.annotatedBooleanArray)
    assertArrayEquals(component.provideAnnotatedByteArray(), contract.annotatedByteArray)
    assertArrayEquals(component.provideAnnotatedCharArray(), contract.annotatedCharArray)
    assertArrayEquals(component.provideAnnotatedDoubleArray(), contract.annotatedDoubleArray, 0.0)
    assertArrayEquals(component.provideAnnotatedFloatArray(), contract.annotatedFloatArray, 0f)
    assertArrayEquals(component.provideAnnotatedIntArray(), contract.annotatedIntArray)
    assertArrayEquals(component.provideAnnotatedLongArray(), contract.annotatedLongArray)
    assertArrayEquals(component.provideAnnotatedShortArray(), contract.annotatedShortArray)
    assertArrayEquals(component.provideAnnotatedStringArray(), contract.annotatedStringArray)
    assertEquals(component.provideAnnotatedIntList(), contract.annotatedIntList)
    assertEquals(component.provideAnnotatedStringList(), contract.annotatedStringList)
  }

  @Test
  fun testConverterContract() {
    val lightsaber = Lightsaber.Builder().build()
    val component = ConverterComponent()
    val injector = lightsaber.createInjector(component)
    val contract = injector.getInstance<ConverterContract>()

    assertSame(contract.valueProvider, contract.valueProvider)

    assertEquals(0, contract.value)
    assertEquals(1, contract.value)
    assertEquals(2, contract.valueProvider.get())
    assertEquals(3, contract.valueProvider.get())
    assertEquals(4, contract.valueLazy.get())
    assertEquals(5, contract.valueLazy.get())
  }

  @Test
  fun testSingletonContract() {
    val lightsaber = Lightsaber.Builder().build()
    val component = SingletonComponent()
    val injector = lightsaber.createInjector(component)
    val contract = injector.getInstance<ConverterContract>()

    assertSame(contract.valueProvider, contract.valueProvider)

    assertEquals(0, contract.value)
    assertEquals(0, contract.value)
    assertEquals(0, contract.valueProvider.get())
    assertEquals(0, contract.valueProvider.get())
    assertEquals(0, contract.valueLazy.get())
    assertEquals(0, contract.valueLazy.get())
  }

  @Test
  fun testOverrideResolvesTypeAmbiguity() {
    val lightsaber = Lightsaber.Builder().build()
    val component = OverrideComponent1()
    val injector = lightsaber.createInjector(component)

    assertEquals("String", injector.getInstance<OverrideContract1>().string)
  }

  @Test
  fun testOverrideClearsQualifier() {
    val lightsaber = Lightsaber.Builder().build()
    val component = OverrideComponent2()
    val injector = lightsaber.createInjector(component)

    assertEquals("String", injector.getInstance<OverrideContract2>().string)
  }

  @Test
  fun testOverrideChangesType() {
    val lightsaber = Lightsaber.Builder().build()
    val component = OverrideComponent3()
    val injector = lightsaber.createInjector(component)

    assertEquals("String", injector.getInstance<OverrideContract3>().string)
  }

  @Test
  fun testInheritanceDoesNotChangeTypeAndQualifier() {
    val lightsaber = Lightsaber.Builder().build()
    val component = OverrideComponent4()
    val injector = lightsaber.createInjector(component)

    assertEquals("String", injector.getInstance<OverrideContract4>().string)
  }

  @Test
  fun testOverrideChangesQualifier() {
    val lightsaber = Lightsaber.Builder().build()
    val component = OverrideComponent5()
    val injector = lightsaber.createInjector(component)

    assertEquals("String", injector.getInstance<OverrideContract5>().string)
  }

  @Component
  class ContractComponent {

    @Provide
    fun provideBoolean(): Boolean = true

    @Provide
    fun provideByte(): Byte = 42

    @Provide
    fun provideChar(): Char = 'x'
    @Provide
    fun provideDouble(): Double = Double.MAX_VALUE
    @Provide
    fun provideFloat(): Float = Float.MAX_VALUE
    @Provide
    fun provideInt(): Int = 42
    @Provide
    fun provideLong(): Long = 42L
    @Provide
    fun provideShort(): Short = 42
    @Provide
    fun provideString(): String = "String"
    @Provide
    fun provideBooleanArray(): BooleanArray = booleanArrayOf(true)
    @Provide
    fun provideByteArray(): ByteArray = byteArrayOf(42)
    @Provide
    fun provideCharArray(): CharArray = charArrayOf('x')
    @Provide
    fun provideDoubleArray(): DoubleArray = doubleArrayOf(Double.MAX_VALUE)
    @Provide
    fun provideFloatArray(): FloatArray = floatArrayOf(Float.MAX_VALUE)
    @Provide
    fun provideIntArray(): IntArray = intArrayOf(42)
    @Provide
    fun provideLongArray(): LongArray = longArrayOf(42L)
    @Provide
    fun provideShortArray(): ShortArray = shortArrayOf(42)
    @Provide
    fun provideStringArray(): Array<String> = arrayOf("String")
    @Provide
    fun provideIntList(): List<Int> = listOf(42)
    @Provide
    fun provideStringList(): List<String> = listOf("String")
    @Provide
    @Named("Annotated")
    fun provideAnnotatedBoolean(): Boolean = false
    @Provide
    @Named("Annotated")
    fun provideAnnotatedByte(): Byte = -42
    @Provide
    @Named("Annotated")
    fun provideAnnotatedChar(): Char = 'y'
    @Provide
    @Named("Annotated")
    fun provideAnnotatedDouble(): Double = Double.MIN_VALUE
    @Provide
    @Named("Annotated")
    fun provideAnnotatedFloat(): Float = Float.MIN_VALUE
    @Provide
    @Named("Annotated")
    fun provideAnnotatedInt(): Int = -42
    @Provide
    @Named("Annotated")
    fun provideAnnotatedLong(): Long = -42L
    @Provide
    @Named("Annotated")
    fun provideAnnotatedShort(): Short = -42
    @Provide
    @Named("Annotated")
    fun provideAnnotatedString(): String = "Annotated String"
    @Provide
    @Named("Annotated")
    fun provideAnnotatedBooleanArray(): BooleanArray = booleanArrayOf(false)
    @Provide
    @Named("Annotated")
    fun provideAnnotatedByteArray(): ByteArray = byteArrayOf(-42)
    @Provide
    @Named("Annotated")
    fun provideAnnotatedCharArray(): CharArray = charArrayOf('y')
    @Provide
    @Named("Annotated")
    fun provideAnnotatedDoubleArray(): DoubleArray = doubleArrayOf(Double.MIN_VALUE)
    @Provide
    @Named("Annotated")
    fun provideAnnotatedFloatArray(): FloatArray = floatArrayOf(Float.MIN_VALUE)
    @Provide
    @Named("Annotated")
    fun provideAnnotatedIntArray(): IntArray = intArrayOf(-42)
    @Provide
    @Named("Annotated")
    fun provideAnnotatedLongArray(): LongArray = longArrayOf(-42L)
    @Provide
    @Named("Annotated")
    fun provideAnnotatedShortArray(): ShortArray = shortArrayOf(-42)
    @Provide
    @Named("Annotated")
    fun provideAnnotatedStringArray(): Array<String> = arrayOf("Annotated", "String")
    @Provide
    @Named("Annotated")
    fun provideAnnotatedIntList(): List<Int> = listOf(-42)
    @Provide
    @Named("Annotated")
    fun provideAnnotatedStringList(): List<String> = listOf("Annotated", "String")
  }

  @Contract
  @ProvidedBy(ContractComponent::class)
  interface ComplexContract {

    val boxedBoolean: Boolean?
    val boxedByte: Byte?
    val boxedChar: Char?
    val boxedDouble: Double?
    val boxedFloat: Float?
    val boxedInt: Int?
    val boxedLong: Long?
    val boxedShort: Short?
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
    @get:Named("Annotated") val annotatedBoxedBoolean: Boolean?
    @get:Named("Annotated") val annotatedBoxedByte: Byte?
    @get:Named("Annotated") val annotatedBoxedChar: Char?
    @get:Named("Annotated") val annotatedBoxedDouble: Double?
    @get:Named("Annotated") val annotatedBoxedFloat: Float?
    @get:Named("Annotated") val annotatedBoxedInt: Int?
    @get:Named("Annotated") val annotatedBoxedLong: Long?
    @get:Named("Annotated") val annotatedBoxedShort: Short?
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

  @Component
  class ConverterComponent {

    private val counter = AtomicInteger()

    @Provide
    fun provideObject(): Any = counter.getAndIncrement()
  }

  @Component
  class SingletonComponent {

    private val counter = AtomicInteger()

    @Provide
    @Singleton
    fun provideSingleton(): Any = counter.getAndIncrement()
  }

  @Contract
  @ProvidedBy(ConverterComponent::class, SingletonComponent::class)
  interface ConverterContract {

    val value: Any
    val valueProvider: Provider<Any>
    val valueLazy: Lazy<Any>
  }

  @Component
  class OverrideComponent1 {

    @Provide
    fun provideString(): String = "String"
  }

  @Component
  class OverrideComponent2 {

    @Provide
    fun provideString(): String = "String"
  }

  @Component
  class OverrideComponent3 {

    @Provide
    fun provideString(): String = "String"
  }

  @Component
  class OverrideComponent4 {

    @Provide
    @Named("Base")
    fun provideString(): String = "String"
  }

  @Component
  class OverrideComponent5 {

    @Provide
    @Named("Annotated")
    fun provideString(): String = "String"
  }

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

  @Contract
  @ProvidedBy(OverrideComponent1::class)
  interface OverrideContract1 : BaseContract1, BaseContract2 {

    override val string: String
  }

  @Contract
  @ProvidedBy(OverrideComponent2::class)
  interface OverrideContract2 : BaseContract1, BaseContract3 {

    override val string: String
  }

  @Contract
  @ProvidedBy(OverrideComponent3::class)
  interface OverrideContract3 : BaseContract1 {

    override val string: String
  }

  @Contract
  @ProvidedBy(OverrideComponent4::class)
  interface OverrideContract4 : BaseContract3

  @Contract
  @ProvidedBy(OverrideComponent5::class)
  interface OverrideContract5 : BaseContract3 {

    @get:Named("Annotated")
    override val string: String
  }
}
