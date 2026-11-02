package com.butterflydevs.billboard.data

enum class MeterProvider(val label: String, val hint: String) {
    NESCO("NESCO Electric", "Meter number"),
    DESCO("DESCO Electric", "Account number"),
    PGCL("PGCL Gas", "Customer code")
}
