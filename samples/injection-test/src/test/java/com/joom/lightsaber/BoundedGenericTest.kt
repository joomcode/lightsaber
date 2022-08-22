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

import org.junit.Assert
import org.junit.Test

class BoundedGenericTest {
  @Test
  fun test() {
    val contract = Lightsaber.Builder().build().createContract(BoundedGenericContractConfiguration())

    Assert.assertEquals(FirstTestKey("Key"), contract.firstContainer.key)
    Assert.assertEquals(FirstTestValue("Value"), contract.firstContainer.value)

    Assert.assertEquals(SecondTestKey("Key"), contract.secondContainer.key)
    Assert.assertEquals(SecondTestValue("Value"), contract.secondContainer.value)
  }

  private interface BoundedGenericContainer<K : Key<*>, V : Value<*>> {
    val key: K
    val value: V
  }

  private interface Key<T : Any>

  private interface Value<T : Any>

  private data class FirstTestKey<T : Any>(val key: T) : Key<T>

  private data class FirstTestValue<T : Any>(val value: T) : Value<T>

  private data class SecondTestKey<T : Any>(val key: T) : Key<T>

  private data class SecondTestValue<T : Any>(val value: T) : Value<T>

  private interface BoundedGenericContract {
    val firstContainer: BoundedGenericContainer<FirstTestKey<*>, FirstTestValue<*>>
    val secondContainer: BoundedGenericContainer<SecondTestKey<*>, SecondTestValue<*>>
  }

  private class BoundedGenericContractConfiguration : ContractConfiguration<BoundedGenericContract>() {
    @Provide
    fun provideFirstTestContainer(): BoundedGenericContainer<FirstTestKey<*>, FirstTestValue<*>> {
      return BoundedGenericContainerImpl(FirstTestKey("Key"), FirstTestValue("Value"))
    }

    @Provide
    fun provideSecondTestContainer(): BoundedGenericContainer<SecondTestKey<*>, SecondTestValue<*>> {
      return BoundedGenericContainerImpl(SecondTestKey("Key"), SecondTestValue("Value"))
    }

    private class BoundedGenericContainerImpl<K : Key<*>, V : Value<*>>(
      override val key: K,
      override val value: V
    ) : BoundedGenericContainer<K, V>
  }
}
