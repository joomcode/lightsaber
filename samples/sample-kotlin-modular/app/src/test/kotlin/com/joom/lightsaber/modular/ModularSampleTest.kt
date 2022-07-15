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


package com.joom.lightsaber.modular

import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class ModularSampleTest {

  private val stream = ByteArrayOutputStream()
  private val out = System.out

  @Before
  fun replaceOutput() {
    System.setOut(PrintStream(stream))
  }

  @Test
  fun runShouldProduceExpectedOutput() {
    ModularSample().run()

    val output = stream.toString(Charsets.UTF_8.name())

    Assert.assertEquals(
      listOf(
        "ModuleDependencyImpl",
        "FactoryDependencyImpl",
        "ComponentDependencyImpl",
        ""
      ).joinToString(separator = "\n"), output
    )
  }

  @After
  fun restoreOutput() {
    System.setOut(out)
  }
}
