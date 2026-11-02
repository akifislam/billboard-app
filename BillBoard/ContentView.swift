import SwiftUI

struct ContentView: View {
    @Environment(DashboardStore.self) private var store
    @State private var showingAdd = false
    @State private var showingAbout = false

    var body: some View {
        NavigationStack {
            Group {
                if store.cards.isEmpty {
                    ContentUnavailableView("No meters yet", systemImage: "gauge.with.dots.needle.67percent", description: Text("Tap + to add your first electricity or gas meter."))
                } else {
                    List {
                        ForEach(store.cards) { card in
                            NavigationLink { MeterDetailsView(card: card) } label: { MeterCardView(card: card) }
                                .listRowSeparator(.hidden)
                                .listRowBackground(Color.clear)
                                .swipeActions { Button(role: .destructive) { store.delete(card.id) } label: { Label("Delete", systemImage: "trash") } }
                        }
                        if let date = store.lastUpdated {
                            Text("Updated \(date.formatted(date: .omitted, time: .standard))").font(.caption).foregroundStyle(.secondary).listRowBackground(Color.clear)
                        }
                    }
                    .listStyle(.plain)
                    .refreshable { await store.refreshAll() }
                }
            }
            .navigationTitle("BillBoard")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button { showingAbout = true } label: { Image(systemName: "info.circle") } }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button { Task { await store.refreshAll() } } label: { store.isRefreshing ? AnyView(ProgressView()) : AnyView(Image(systemName: "arrow.clockwise")) }.disabled(store.isRefreshing)
                    Button { showingAdd = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showingAdd) { AddMeterView() }
            .sheet(isPresented: $showingAbout) { AboutView() }
        }
        .tint(.purple)
        .task { await store.start() }
    }
}

struct MeterCardView: View {
    let card: MeterCardState
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(card.meter.provider.label, systemImage: card.meter.provider.symbol).font(.caption.bold()).foregroundStyle(card.meter.provider.color)
                Spacer()
                Text(card.meter.billingType.rawValue.uppercased()).font(.caption2.bold()).padding(.horizontal, 8).padding(.vertical, 4).background(card.meter.provider.color.opacity(0.18), in: Capsule())
            }
            Text(card.meter.nickname).font(.title3.bold())
            Text(card.meter.number).font(.caption.monospaced()).foregroundStyle(.secondary)
            if let snapshot = card.snapshot {
                if let balance = snapshot.balance {
                    Text(balance.taka).font(.system(size: 30, weight: .bold, design: .rounded)).foregroundStyle(balance < 150 ? .red : .green)
                    if let days = snapshot.predictionDays { Text(days < 1 ? "Less than a day remaining" : "About \(Int(days.rounded())) days remaining").font(.caption).foregroundStyle(.secondary) }
                } else if let summary = snapshot.summary {
                    Text(summary).font(.headline).foregroundStyle(snapshot.isDue ? .red : .green)
                }
                Divider()
                if let amount = snapshot.lastPaymentAmount { LabeledContent("Last payment", value: amount.taka) }
                if let date = snapshot.lastPaymentDate { LabeledContent("Date", value: date) }
            } else if let error = card.error {
                Label(error, systemImage: "exclamationmark.triangle").font(.caption).foregroundStyle(.red)
            } else {
                ProgressView("Checking utility portal…").font(.caption)
            }
            HStack { Spacer(); Text("Meter Details").font(.subheadline.bold()); Image(systemName: "chevron.right").font(.caption) }.foregroundStyle(card.meter.provider.color)
        }
        .padding(16)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 18))
        .overlay(alignment: .leading) { RoundedRectangle(cornerRadius: 3).fill(card.meter.provider.color).frame(width: 5).padding(.vertical, 14) }
    }
}

struct AddMeterView: View {
    @Environment(DashboardStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var provider = MeterProvider.nesco
    @State private var billingType = BillingType.prepaid
    @State private var number = ""
    @State private var nickname = ""
    @State private var isSubmitting = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Provider") { Picker("Provider", selection: $provider) { ForEach(MeterProvider.allCases) { Text($0.rawValue).tag($0) } }.pickerStyle(.segmented) }
                Section("Billing") { Picker("Billing type", selection: $billingType) { ForEach(BillingType.allCases) { Text($0.rawValue).tag($0) } }.pickerStyle(.segmented).disabled(provider != .nesco).opacity(provider == .nesco ? 1 : 0.55) }
                Section("Meter") {
                    TextField(provider.inputLabel, text: $number).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.asciiCapable)
                    TextField("Nickname (optional)", text: $nickname)
                }
                if let error { Section { Label(error, systemImage: "exclamationmark.triangle.fill").foregroundStyle(.red) } }
            }
            .navigationTitle("Add Meter")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.disabled(isSubmitting) }
                ToolbarItem(placement: .confirmationAction) { Button { add() } label: { isSubmitting ? AnyView(ProgressView()) : AnyView(Text("Add")) }.disabled(number.trimmingCharacters(in: .whitespaces).isEmpty || isSubmitting) }
            }
            .onChange(of: provider) { _, newValue in if newValue == .desco { billingType = .prepaid }; if newValue == .pgcl { billingType = .postpaid } }
        }
        .interactiveDismissDisabled(isSubmitting)
    }

    private func add() {
        isSubmitting = true; error = nil
        Task {
            do { try await store.add(number: number, nickname: nickname, provider: provider, billingType: billingType); dismiss() }
            catch { self.error = error.localizedDescription; isSubmitting = false }
        }
    }
}

struct MeterDetailsView: View {
    let card: MeterCardState
    var body: some View {
        List {
            if let snapshot = card.snapshot {
                Section {
                    if let balance = snapshot.balance { Text(balance.taka).font(.largeTitle.bold()).foregroundStyle(balance < 150 ? .red : .green) }
                    if let summary = snapshot.summary { Text(summary).font(.title3.bold()).foregroundStyle(snapshot.isDue ? .red : .green) }
                    if let asOf = snapshot.balanceAsOf { Text("As of \(asOf)").font(.caption).foregroundStyle(.secondary) }
                    if let days = snapshot.predictionDays { LabeledContent("Estimated to last", value: "\(Int(days.rounded())) days") }
                }
                Section("Customer & meter") { ForEach(snapshot.details) { field in LabeledContent(field.label, value: field.value).textSelection(.enabled) } }
                if !snapshot.primaryHistory.isEmpty { HistorySection(title: snapshot.primaryHistoryTitle, rows: snapshot.primaryHistory) }
                if let title = snapshot.secondaryHistoryTitle, !snapshot.secondaryHistory.isEmpty { HistorySection(title: title, rows: snapshot.secondaryHistory) }
            } else {
                ContentUnavailableView("No data", systemImage: "wifi.exclamationmark", description: Text(card.error ?? "Pull to refresh from the dashboard."))
            }
        }
        .navigationTitle(card.meter.nickname)
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct HistorySection: View {
    let title: String
    let rows: [HistoryRow]
    var body: some View { Section(title) { ForEach(rows) { row in DisclosureGroup(row.title) { ForEach(row.fields) { field in LabeledContent(field.label, value: field.value) } } } } }
}

struct AboutView: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                Section { Label("All your utility balances in one place", systemImage: "rectangle.3.group.fill") }
                Section("Supported providers") { Label("NESCO prepaid and postpaid", systemImage: "bolt.fill"); Label("DESCO prepaid", systemImage: "bolt.fill"); Label("PGCL gas", systemImage: "flame.fill") }
                Section("Privacy") { Text("No login and no cloud account. Meter numbers, nicknames, and cached readings stay on this device. BillBoard reads the providers’ public customer portals directly.") }
                Section("Disclaimer") { Text("BillBoard is an independent, unofficial app and is not affiliated with NESCO, DESCO, or PGCL.") }
            }
            .navigationTitle("About BillBoard")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }
}

#Preview { ContentView().environment(DashboardStore()).preferredColorScheme(.dark) }
