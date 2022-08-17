package com.joom.lightsaber.plugin

import java.io.File

open class AndroidLightsaberPluginExtension {
  var validateUsage: Boolean = true
  var cacheable: Boolean = false
  var bootClasspath: List<File> = emptyList()
}
