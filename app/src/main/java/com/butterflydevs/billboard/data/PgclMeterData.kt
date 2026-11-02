package com.butterflydevs.billboard.data

data class PgclPayment(
    val sl: String,
    val billMonth: String,
    val bank: String?,
    val billedAmount: Double?,
    val surcharge: Double?,
    val total: Double?,
    val collectionDate: String?
)

data class PgclMeterData(
    val customerCode: String?,
    val customerName: String?,
    val address: String?,
    val phone: String?,
    val category: String?,
    val status: String?,
    val connectionDate: String?,
    val meteredStatus: String?,
    val maxLoad: String?,
    val minLoad: String?,
    val burnerCount: String?,
    val regionalOffice: String?,
    val dueStatusMessage: String?,
    val payments: List<PgclPayment>
)
