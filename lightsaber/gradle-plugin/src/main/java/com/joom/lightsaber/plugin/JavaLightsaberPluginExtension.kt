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

package com.joom.lightsaber.plugin

open class JavaLightsaberPluginExtension {
  var processTest: Boolean? = null
  var validateUsage: Boolean? = null
  var validateUnusedImports: Boolean? = null
  var validateUnusedImportsVerbose: Boolean? = null
  var dumpDebugReport: Boolean? = null
}
