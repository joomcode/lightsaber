package com.example.impl

import com.example.api.AppContract
import com.example.api.FirstService
import com.example.api.SecondService
import com.joom.lightsaber.ContractConfiguration
import com.joom.lightsaber.ProvidedAs
import com.joom.lightsaber.ProvidedBy
import javax.inject.Inject

class AppConfiguration : ContractConfiguration<AppContract>()

@ProvidedAs(FirstService::class)
@ProvidedBy(AppConfiguration::class)
class FirstServiceImpl @Inject constructor() : FirstService

@ProvidedAs(SecondService::class)
@ProvidedBy(AppConfiguration::class)
class SecondServiceImpl @Inject constructor() : SecondService
