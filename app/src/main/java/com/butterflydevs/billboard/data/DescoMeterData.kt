package com.butterflydevs.billboard.data

data class DescoRechargeRow(
    val rechargeDate: String?,
    val totalAmount: Double?,
    val vat: Double?,
    val energyAmount: Double?
)

data class DescoMonthlyConsumptionRow(
    val month: String,
    val consumedTaka: Double?,
    val consumedUnit: Double?,
    val maximumDemand: Double?
)

data class DescoMeterData(
    val accountNo: String?,
    val meterNo: String?,
    val balance: Double?,
    val currentMonthConsumptionTaka: Double?,
    val readingTime: String?,
    val customerName: String?,
    val contactNo: String?,
    val feederName: String?,
    val installationAddress: String?,
    val installationDate: String?,
    val phaseType: String?,
    val registerDate: String?,
    val sanctionLoad: String?,
    val tariffSolution: String?,
    val meterModel: String?,
    val transformer: String?,
    val sdName: String?,
    val lastRecharge: DescoRechargeRow?,
    val rechargedThisMonth: Double?,
    val rechargedThisYear: Double?,
    val usedThisMonthKwh: Double?,
    val maxLoadLastMonth: Double?,
    val maxLoadLastYear: Double?,
    val monthlyConsumption: List<DescoMonthlyConsumptionRow>,
    val rechargeHistory: List<DescoRechargeRow>
)
