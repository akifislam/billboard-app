package com.butterflydevs.billboard.data

enum class MeterType { PREPAID, POSTPAID }

data class MeterInfo(
    val meter: String,
    val nickname: String,
    val provider: MeterProvider? = null,
    val meterType: MeterType? = null
) {
    val resolvedProvider: MeterProvider get() = provider ?: MeterProvider.NESCO

    // DESCO is always prepaid and PGCL is always postpaid; only NESCO lets the user choose.
    val resolvedMeterType: MeterType
        get() = when (resolvedProvider) {
            MeterProvider.DESCO -> MeterType.PREPAID
            MeterProvider.PGCL -> MeterType.POSTPAID
            MeterProvider.NESCO -> meterType ?: MeterType.PREPAID
        }
}
