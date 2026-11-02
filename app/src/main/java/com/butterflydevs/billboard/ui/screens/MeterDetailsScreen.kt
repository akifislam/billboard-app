package com.butterflydevs.billboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.butterflydevs.billboard.data.DescoMonthlyConsumptionRow
import com.butterflydevs.billboard.data.MeterProvider
import com.butterflydevs.billboard.data.MeterType
import com.butterflydevs.billboard.data.MonthlyUsageRow
import com.butterflydevs.billboard.data.PgclPayment
import com.butterflydevs.billboard.data.PostpaidBillRow
import com.butterflydevs.billboard.ui.components.InfoRow
import com.butterflydevs.billboard.ui.components.formatDaysRemaining
import com.butterflydevs.billboard.ui.components.formatTaka
import com.butterflydevs.billboard.ui.components.postpaidDueSummary
import com.butterflydevs.billboard.viewmodel.MeterCardState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeterDetailsScreen(meter: MeterCardState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meter.nickname, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        when {
            meter.provider == MeterProvider.NESCO && meter.billingType == MeterType.POSTPAID ->
                NescoPostpaidDetails(meter, innerPadding)
            meter.provider == MeterProvider.NESCO -> NescoDetails(meter, innerPadding)
            meter.provider == MeterProvider.DESCO -> DescoDetails(meter, innerPadding)
            else -> PgclDetails(meter, innerPadding)
        }
    }
}

@Composable
private fun NescoDetails(meter: MeterCardState, innerPadding: androidx.compose.foundation.layout.PaddingValues) {
    var showLastRecharge by remember { mutableStateOf(false) }
    var showMonthlyUsage by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DetailsCard {
                val isLow = meter.balance != null && meter.balance < 150.0
                Text(
                    text = formatTaka(meter.balance),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                )
                if (!meter.balanceAsOf.isNullOrBlank()) {
                    Text(
                        text = "as of ${meter.balanceAsOf}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                InfoRow("Estimated to last", formatDaysRemaining(meter.predictionDaysRemaining))
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                InfoRow("Customer Name", meter.name ?: "—")
                InfoRow("Father/Husband's Name", meter.fatherOrHusbandName ?: "—")
                InfoRow("Address", meter.address ?: "—")
                InfoRow("Mobile", meter.mobile ?: "—")
                InfoRow("Office", meter.office ?: "—")
                InfoRow("Feeder", meter.feederName ?: "—")
                InfoRow("Consumer Number", meter.consumerNo ?: "—")
                InfoRow("Meter Number", meter.meterSerialNo ?: "—")
                InfoRow("Approved Load (kW)", meter.approvedLoadKw ?: "—")
                InfoRow("Approved Tariff", meter.tariff ?: "—")
                InfoRow("Meter Type", meter.meterType ?: "—")
                InfoRow("Meter Status", meter.meterStatus ?: "—")
                InfoRow("Meter Install Date", meter.installDate ?: "—")
                InfoRow("Min Recharge Amount", formatTaka(meter.minRechargeAmount))
            }
        }
        item {
            ExpandableSection(
                title = "Last Recharge",
                expanded = showLastRecharge,
                onToggle = { showLastRecharge = !showLastRecharge }
            ) {
                val lr = meter.lastRecharge
                if (lr == null) {
                    Text("No recharge history available yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    InfoRow("Token Number", lr.tokenNo ?: "—")
                    InfoRow("Meter Rent", formatTaka(lr.meterRent))
                    InfoRow("Demand Charge", formatTaka(lr.demandCharge))
                    InfoRow("PFC Charge", formatTaka(lr.pfcCharge))
                    InfoRow("VAT", formatTaka(lr.vat))
                    InfoRow("Paid Arrears/Penalty", formatTaka(lr.paidArrears))
                    InfoRow("Rebate", formatTaka(lr.rebate))
                    InfoRow("Electricity", formatTaka(lr.electricityCharge))
                    InfoRow("Recharge Amount", formatTaka(lr.rechargeAmount))
                    InfoRow("Possible Units (kWh)", lr.possibleUnitsKwh?.toString() ?: "—")
                    InfoRow("Method", lr.method ?: "—")
                    InfoRow("Date", lr.date ?: "—")
                    InfoRow("Status", lr.status ?: "—")
                }
            }
        }
        item {
            ExpandableSection(
                title = "মাসিক ব্যবহার (Monthly Usage)",
                expanded = showMonthlyUsage,
                onToggle = { showMonthlyUsage = !showMonthlyUsage }
            ) {
                if (meter.monthlyUsage.isEmpty()) {
                    Text("Loading monthly usage…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        meter.monthlyUsage.forEach { row -> NescoMonthlyUsageCard(row) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NescoPostpaidDetails(meter: MeterCardState, innerPadding: androidx.compose.foundation.layout.PaddingValues) {
    var showDueBills by remember { mutableStateOf(false) }
    var showAllBills by remember { mutableStateOf(false) }
    val data = meter.nescoPostpaidData
    val (message, isDue) = postpaidDueSummary(data)

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DetailsCard {
                Text(
                    text = message,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isDue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                InfoRow("Customer Name", data?.name ?: "—")
                InfoRow("Father/Husband's Name", data?.fatherOrHusbandName ?: "—")
                InfoRow("Address", data?.address ?: "—")
                InfoRow("Mobile", data?.mobile ?: "—")
                InfoRow("Office", data?.office ?: "—")
                InfoRow("Feeder", data?.feederName ?: "—")
                InfoRow("Consumer Number", data?.consumerNo ?: "—")
                InfoRow("Accounts Office No", data?.accountsOfficeNo ?: "—")
                InfoRow("Previous Consumer No", data?.previousConsumerNo ?: "—")
                InfoRow("Meter Number", data?.meterNo ?: "—")
                InfoRow("Approved Load (kW)", data?.approvedLoadKw ?: "—")
                InfoRow("Approved Tariff", data?.tariff ?: "—")
                InfoRow("Meter Status", data?.meterStatus ?: "—")
                InfoRow("Connection Phase", data?.connectionPhase ?: "—")
                InfoRow("Bill Group", data?.billGroup ?: "—")
                InfoRow("Book No", data?.bookNo ?: "—")
                InfoRow("Work Order", data?.workOrder ?: "—")
            }
        }
        item {
            ExpandableSection(
                title = "Due Bills",
                expanded = showDueBills,
                onToggle = { showDueBills = !showDueBills }
            ) {
                val dueBills = data?.dueBills.orEmpty()
                if (dueBills.isEmpty()) {
                    Text("No due bills.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dueBills.forEach { bill -> PostpaidBillCard(bill) }
                    }
                }
            }
        }
        item {
            ExpandableSection(
                title = "সকল বিল (All Bills)",
                expanded = showAllBills,
                onToggle = { showAllBills = !showAllBills }
            ) {
                val allBills = data?.allBills.orEmpty()
                if (allBills.isEmpty()) {
                    Text("Loading bill history…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allBills.forEach { bill -> PostpaidBillCard(bill) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DescoDetails(meter: MeterCardState, innerPadding: androidx.compose.foundation.layout.PaddingValues) {
    var showLastRecharge by remember { mutableStateOf(false) }
    var showMonthlyConsumption by remember { mutableStateOf(false) }
    val data = meter.descoData

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DetailsCard {
                val isLow = meter.balance != null && meter.balance < 150.0
                Text(
                    text = formatTaka(meter.balance),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                )
                if (!meter.balanceAsOf.isNullOrBlank()) {
                    Text(
                        text = "Reading time: ${meter.balanceAsOf}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                InfoRow("Estimated to last", formatDaysRemaining(meter.predictionDaysRemaining))
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                InfoRow("Used This Month (kWh)", data?.usedThisMonthKwh?.toString() ?: "—")
                InfoRow("In BDT", formatTaka(data?.currentMonthConsumptionTaka))
                InfoRow("Recharged This Month", formatTaka(data?.rechargedThisMonth))
                InfoRow("Recharged This Year", formatTaka(data?.rechargedThisYear))
                InfoRow("Max Load Last Month", data?.maxLoadLastMonth?.let { "$it kW" } ?: "—")
                InfoRow("Max Load Last Year", data?.maxLoadLastYear?.let { "$it kW" } ?: "—")
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                InfoRow("Name", data?.customerName ?: "—")
                InfoRow("Account No", data?.accountNo ?: meter.meter)
                InfoRow("Meter No", data?.meterNo ?: "—")
                InfoRow("Contact No", data?.contactNo ?: "—")
                InfoRow("Tariff Solution", data?.tariffSolution ?: "—")
                InfoRow("S & D", data?.sdName ?: "—")
                InfoRow("Transformer", data?.transformer ?: "—")
                InfoRow("Feeder", data?.feederName ?: "—")
                InfoRow("Installation Date", data?.installationDate ?: "—")
                InfoRow("Registration Date", data?.registerDate ?: "—")
                InfoRow("Meter Model", data?.meterModel ?: "—")
                InfoRow("Phase Type", data?.phaseType ?: "—")
                InfoRow("Sanctioned Load", data?.sanctionLoad ?: "—")
                InfoRow("Installation Address", data?.installationAddress ?: "—")
            }
        }
        item {
            ExpandableSection(
                title = "Last Recharge",
                expanded = showLastRecharge,
                onToggle = { showLastRecharge = !showLastRecharge }
            ) {
                val lr = data?.lastRecharge
                if (lr == null) {
                    Text("No recharge history available yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    InfoRow("Recharge Amount", formatTaka(lr.totalAmount))
                    InfoRow("VAT", formatTaka(lr.vat))
                    InfoRow("Energy Amount", formatTaka(lr.energyAmount))
                    InfoRow("Recharge Time", lr.rechargeDate ?: "—")
                }
            }
        }
        item {
            ExpandableSection(
                title = "Monthly Consumption",
                expanded = showMonthlyConsumption,
                onToggle = { showMonthlyConsumption = !showMonthlyConsumption }
            ) {
                val rows = data?.monthlyConsumption.orEmpty()
                if (rows.isEmpty()) {
                    Text("Loading monthly consumption…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { row -> DescoMonthlyConsumptionCard(row) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PgclDetails(meter: MeterCardState, innerPadding: androidx.compose.foundation.layout.PaddingValues) {
    val data = meter.pgclData

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(innerPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DetailsCard {
                val dueMessage = data?.dueStatusMessage ?: "—"
                val isDue = !dueMessage.contains("no due", ignoreCase = true)
                Text(
                    text = dueMessage,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                InfoRow("Customer Code", data?.customerCode ?: meter.meter)
                InfoRow("Customer Name", data?.customerName ?: "—")
                InfoRow("Address", data?.address ?: "—")
                InfoRow("Phone", data?.phone ?: "—")
                InfoRow("Category", data?.category ?: "—")
                InfoRow("Status", data?.status ?: "—")
                InfoRow("Connection Date", data?.connectionDate ?: "—")
                InfoRow("Metered Status", data?.meteredStatus ?: "—")
                InfoRow("Max Load", data?.maxLoad ?: "—")
                InfoRow("Min Load", data?.minLoad ?: "—")
                InfoRow("Burner Count", data?.burnerCount ?: "—")
                InfoRow("Regional Office", data?.regionalOffice ?: "—")
            }
        }
        item {
            DetailsCard {
                Text(
                    "Last 3 Months' Payments",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                val payments = data?.payments.orEmpty()
                if (payments.isEmpty()) {
                    Text("No payment history available.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        payments.forEach { payment -> PgclPaymentCard(payment) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(shape = RoundedCorner(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(shape = RoundedCorner(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(title)
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                content()
            }
        }
    }
}

@Composable
private fun NescoMonthlyUsageCard(row: MonthlyUsageRow) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCorner(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${row.month} ${row.year}", style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            InfoRow("Total Usage/Deduction", formatTaka(row.totalUsage))
            InfoRow("End of Month Balance", formatTaka(row.endOfMonthBalance))
            if (expanded) {
                InfoRow("Total Recharge", formatTaka(row.totalRecharge))
                InfoRow("Rebate", formatTaka(row.rebate))
                InfoRow("Electricity Used", formatTaka(row.electricityUsedTaka))
                InfoRow("Meter Rent", formatTaka(row.meterRent))
                InfoRow("Demand Charge", formatTaka(row.demandCharge))
                InfoRow("PFC Charge", formatTaka(row.pfcCharge))
                InfoRow("Paid Arrears/Penalty", formatTaka(row.paidArrears))
                InfoRow("VAT", formatTaka(row.vat))
                InfoRow("Units Used (kWh)", row.unitsUsedKwh?.toString() ?: "—")
            }
        }
    }
}

@Composable
private fun PostpaidBillCard(bill: PostpaidBillRow) {
    Card(
        shape = RoundedCorner(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text("${bill.month} ${bill.year}", style = MaterialTheme.typography.titleSmall)
            InfoRow("Bill No", bill.billNo)
            InfoRow("Total Bill", formatTaka(bill.totalBillTaka))
            InfoRow("Late Fee", formatTaka(bill.lateFeeTaka))
            InfoRow("Due Date", bill.dueDate ?: "—")
            InfoRow("Paid Amount", formatTaka(bill.paidAmountTaka))
            InfoRow("Paid Date", bill.paidDate ?: "—")
            InfoRow("Payment Method", bill.paymentMethod ?: "—")
            InfoRow("Payment Status", bill.paymentStatus ?: "—")
        }
    }
}

@Composable
private fun DescoMonthlyConsumptionCard(row: DescoMonthlyConsumptionRow) {
    Card(
        shape = RoundedCorner(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(row.month, style = MaterialTheme.typography.titleSmall)
            InfoRow("Consumed (BDT)", formatTaka(row.consumedTaka))
            InfoRow("Consumed (kWh)", row.consumedUnit?.toString() ?: "—")
            InfoRow("Maximum Demand (kW)", row.maximumDemand?.toString() ?: "—")
        }
    }
}

@Composable
private fun PgclPaymentCard(payment: PgclPayment) {
    Card(
        shape = RoundedCorner(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(payment.billMonth, style = MaterialTheme.typography.titleSmall)
            InfoRow("Bank", payment.bank ?: "—")
            InfoRow("Billed Amount", formatTaka(payment.billedAmount))
            InfoRow("Surcharge", formatTaka(payment.surcharge))
            InfoRow("Total", formatTaka(payment.total))
            InfoRow("Collection Date", payment.collectionDate ?: "—")
        }
    }
}

@Composable
private fun RoundedCorner() = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
