package com.butterflydevs.billboard.data

data class MonthlyUsageRow(
    val year: String,
    val month: String,
    val totalRecharge: Double?,
    val rebate: Double?,
    val electricityUsedTaka: Double?,
    val meterRent: Double?,
    val demandCharge: Double?,
    val pfcCharge: Double?,
    val paidArrears: Double?,
    val vat: Double?,
    val totalUsage: Double?,
    val endOfMonthBalance: Double?,
    val unitsUsedKwh: Double?
)
