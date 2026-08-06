package com.example.api

interface FirstService
interface SecondService

interface AppContract {
  val firstService: FirstService
  val secondService: SecondService
}

fun implementationValue(): Int = 2
