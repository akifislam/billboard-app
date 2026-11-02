package com.butterflydevs.billboard.data

data class PostpaidBillRow(
    val billNo: String,
    val year: String,
    val month: String,
    val totalBillTaka: Double?,
    val lateFeeTaka: Double?,
    val dueDate: String?,
    val paidAmountTaka: Double?,
    val paidDate: String?,
    val paymentMethod: String?,
    val paymentStatus: String?
)

data class NescoPostpaidMeterData(
    val name: String?,
    val fatherOrHusbandName: String?,
    val address: String?,
    val mobile: String?,
    val office: String?,
    val feederName: String?,
    val consumerNo: String?,
    val accountsOfficeNo: String?,
    val previousConsumerNo: String?,
    val meterNo: String?,
    val approvedLoadKw: String?,
    val tariff: String?,
    val meterStatus: String?,
    val connectionPhase: String?,
    val billGroup: String?,
    val bookNo: String?,
    val workOrder: String?,
    val dueBills: List<PostpaidBillRow>,
    val allBills: List<PostpaidBillRow>
)
