package com.butterflydevs.billboard.data

data class RechargeRecord(
    val tokenNo: String?,
    val meterRent: Double?,
    val demandCharge: Double?,
    val pfcCharge: Double?,
    val vat: Double?,
    val paidArrears: Double?,
    val rebate: Double?,
    val electricityCharge: Double?,
    val rechargeAmount: Double?,
    val possibleUnitsKwh: Double?,
    val method: String?,
    val date: String?,
    val status: String?
)

data class ScrapedMeterData(
    val name: String?,
    val fatherOrHusbandName: String?,
    val address: String?,
    val mobile: String?,
    val office: String?,
    val feederName: String?,
    val consumerNo: String?,
    val meterNo: String?,
    val approvedLoadKw: String?,
    val tariff: String?,
    val meterType: String?,
    val meterStatus: String?,
    val installDate: String?,
    val minRechargeAmount: Double?,
    val balance: Double?,
    val balanceAsOf: String?,
    val lastRecharge: RechargeRecord?
)
