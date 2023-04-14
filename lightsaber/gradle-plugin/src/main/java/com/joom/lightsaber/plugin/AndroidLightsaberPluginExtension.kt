package com.joom.lightsaber.plugin

import java.io.File

open class AndroidLightsaberPluginExtension {
  var validateUsage: Boolean = true
  var validateUnusedImports: Boolean = false
  var dumpDebugReport: Boolean = false
  var cacheable: Boolean = false
  var bootClasspath: List<File> = emptyList()
}
