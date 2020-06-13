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

import org.junit.Assert
import javax.inject.Named

fun named(name: String): Named {
  return AnnotationBuilder(Named::class.java).addMember(Named::value.name, name).build()
}

fun assertResolves(injector: Injector, type: Class<*>, qualifier: Annotation? = null) {
  val key = Key.of(type, qualifier)
  try {
    injector.getInstance(key)
  } catch (exception: ConfigurationException) {
    Assert.fail("Failed to resolve a dependency for key $key")
  }
}

fun assertNotResolves(injector: Injector, type: Class<*>, qualifier: Annotation? = null) {
  val key = Key.of(type, qualifier)
  try {
    injector.getInstance(key)
    Assert.fail("Dependency for key $key shouldn't resolve")
  } catch (exception: ConfigurationException) {
    // Do nothing.
  }
}

inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
  try {
    block()
    Assert.fail("${T::class.java} expected but no exception has been thrown")
  } catch (throwable: Throwable) {
    if (throwable !is T) {
      Assert.fail("${T::class.java} expected but $throwable has been thrown")
    }
  }
}
