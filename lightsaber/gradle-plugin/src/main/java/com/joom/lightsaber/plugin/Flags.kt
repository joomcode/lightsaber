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

import org.gradle.api.Project

internal object Flags {
  fun processTestByDefault(project: Project): Boolean {
    return project.providers.gradleProperty("lightsaber.process.test")
      .forUseAtConfigurationTime()
      .getOrElse("true")
      .toBoolean()
  }

  fun validateUsageByDefault(project: Project): Boolean {
    return project.providers.gradleProperty("lightsaber.validate.usage")
      .forUseAtConfigurationTime()
      .getOrElse("true")
      .toBoolean()
  }

  fun validateUnusedImportsByDefault(project: Project): Boolean {
    return project.providers.gradleProperty("lightsaber.validate.unused.imports")
      .forUseAtConfigurationTime()
      .getOrElse("true")
      .toBoolean()
  }

  fun validateUnusedImportsVerboseByDefault(project: Project): Boolean {
    return project.providers.gradleProperty("lightsaber.validate.unused.imports.verbose")
      .forUseAtConfigurationTime()
      .getOrElse("true")
      .toBoolean()
  }

  fun dumpDebugReportByDefault(project: Project): Boolean {
    return project.providers.gradleProperty("lightsaber.dump.debug.report")
      .forUseAtConfigurationTime()
      .getOrElse("false")
      .toBoolean()
  }
}
