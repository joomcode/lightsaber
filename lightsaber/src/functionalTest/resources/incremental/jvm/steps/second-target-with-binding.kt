package com.example.incremental

import com.joom.lightsaber.ProvidedBy
import javax.inject.Inject

@ProvidedBy(TestModule::class)
class SecondTarget @Inject constructor()
