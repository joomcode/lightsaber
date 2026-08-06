package com.example.incremental

import com.joom.lightsaber.Contract
import com.joom.lightsaber.ContractConfiguration
import com.joom.lightsaber.Import
import com.joom.lightsaber.ProvidedAs
import com.joom.lightsaber.ProvidedBy
import javax.inject.Inject

interface MainContract {
  val mainDependency: MainDependency
}

interface UsedContract {
  val usedDependency: UsedDependency
}

interface UnusedContract {
  val unusedDependency: UnusedDependency
}

interface MainDependency
interface UsedDependency
interface UnusedDependency

@ProvidedAs(MainDependency::class)
@ProvidedBy(MainContractConfiguration::class)
class MainDependencyImpl @Inject constructor(
  usedDependency: UsedDependency,
) : MainDependency

@ProvidedAs(UsedDependency::class)
@ProvidedBy(UsedContractConfiguration::class)
class UsedDependencyImpl @Inject constructor() : UsedDependency

@ProvidedAs(UnusedDependency::class)
@ProvidedBy(UnusedContractConfiguration::class)
class UnusedDependencyImpl @Inject constructor() : UnusedDependency

class UsedContractConfiguration : ContractConfiguration<UsedContract>()
class UnusedContractConfiguration : ContractConfiguration<UnusedContract>()

class MainContractConfiguration(
  @Import @Contract val usedContract: UsedContract,
  @Import @Contract val unusedContract: UnusedContract,
) : ContractConfiguration<MainContract>()
