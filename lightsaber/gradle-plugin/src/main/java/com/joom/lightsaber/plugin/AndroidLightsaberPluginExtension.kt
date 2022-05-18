package com.joom.lightsaber.plugin

import java.io.File

open class AndroidLightsaberPluginExtension {
  var cacheable: Boolean = false
  var bootClasspath: List<File> = emptyList()
}
