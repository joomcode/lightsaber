package com.example.api

interface FirstService
interface SecondService

interface AppContract {
  val firstService: FirstService
}

fun implementationValue(): Int = 2
