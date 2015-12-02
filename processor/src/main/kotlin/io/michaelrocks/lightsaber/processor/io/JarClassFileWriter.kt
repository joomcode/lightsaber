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

package io.michaelrocks.lightsaber.processor.io

import io.michaelrocks.lightsaber.processor.commons.closeQuitely
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class JarClassFileWriter @Throws(IOException::class) constructor(
    targetFile: File
) : ClassFileWriter() {
  private val stream: JarOutputStream = JarOutputStream(FileOutputStream(targetFile))

  @Throws(IOException::class)
  override fun writeFile(path: String, fileData: ByteArray) {
    val entry = JarEntry(path)
    stream.putNextEntry(entry)
    stream.write(fileData)
    stream.closeEntry()
  }

  @Throws(IOException::class)
  override fun createDirectory(path: String) {
    val directoryPath = if (path.endsWith("/")) path else "$path/"
    val entry = JarEntry(directoryPath)
    stream.putNextEntry(entry)
    stream.closeEntry()
  }

  override fun close() {
    stream.closeQuitely()
  }
}