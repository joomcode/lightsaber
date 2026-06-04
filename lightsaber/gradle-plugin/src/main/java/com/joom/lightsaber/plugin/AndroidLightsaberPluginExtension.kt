package com.joom.lightsaber.plugin

open class AndroidLightsaberPluginExtension {
  var validateUsage: Boolean? = null
  var validateUnusedImports: Boolean? = null
  var validateUnusedImportsVerbose: Boolean? = null
  var dumpDebugReport: Boolean? = null

  var cacheable: Boolean = false
}
