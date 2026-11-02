import Foundation
import Observation
import UserNotifications

@MainActor @Observable
final class DashboardStore {
    private(set) var cards: [MeterCardState] = []
    private(set) var lastUpdated: Date?
    private(set) var isRefreshing = false
    private let savedKey = "billboard.savedMeters.v1"
    private let snapshotKey = "billboard.snapshots.v1"
    private var refreshTask: Task<Void, Never>?

    // Public builds always start empty. Saved meters are created by the user
    // and remain in the app's local container.
    private static let firstRunMeters: [SavedMeter] = []

    init() { load() }

    func start() async {
        guard refreshTask == nil else { return }
        await refreshAll()
        refreshTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(300))
                await self?.refreshAll()
            }
        }
    }

    func refreshAll() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        for index in cards.indices { cards[index].isLoading = true }
        for card in cards {
            do { apply(.success(try await UtilityService.shared.fetch(card.meter)), to: card.id) }
            catch { apply(.failure(error), to: card.id) }
        }
        lastUpdated = Date(); isRefreshing = false; persistSnapshots()
    }

    func add(number: String, nickname: String, provider: MeterProvider, billingType: BillingType) async throws {
        let type: BillingType = provider == .desco ? .prepaid : provider == .pgcl ? .postpaid : billingType
        let trimmed = number.trimmingCharacters(in: .whitespacesAndNewlines)
        let candidate = SavedMeter(number: trimmed, nickname: nickname.trimmingCharacters(in: .whitespacesAndNewlines), provider: provider, billingType: type)
        guard !cards.contains(where: { $0.meter.number == trimmed && $0.meter.provider == provider }) else { throw BillBoardError.notFound("This meter is already in your list.") }
        let snapshot = try await UtilityService.shared.fetch(candidate)
        let final = SavedMeter(number: trimmed, nickname: candidate.nickname.isEmpty ? (snapshot.customerName ?? trimmed) : candidate.nickname, provider: provider, billingType: type)
        cards.append(MeterCardState(meter: final, snapshot: snapshot)); persistAll(); notifyIfNeeded(final, snapshot)
    }

    func delete(_ id: String) { cards.removeAll { $0.id == id }; persistAll() }

    private func apply(_ result: Result<MeterSnapshot, Error>, to id: String) {
        guard let index = cards.firstIndex(where: { $0.id == id }) else { return }
        switch result {
        case .success(let snapshot):
            cards[index].snapshot = snapshot; cards[index].error = nil; notifyIfNeeded(cards[index].meter, snapshot)
        case .failure(let error):
            if cards[index].snapshot == nil { cards[index].error = error.localizedDescription }
        }
        cards[index].isLoading = false
    }

    private func notifyIfNeeded(_ meter: SavedMeter, _ snapshot: MeterSnapshot) {
        guard let balance = snapshot.balance, balance < 100 else { return }
        let content = UNMutableNotificationContent()
        content.title = "Low balance"
        content.body = "\(meter.nickname) [\(meter.number)] has low balance. Please recharge."
        content.sound = .default
        let request = UNNotificationRequest(identifier: "low-\(meter.id)-\(Int(balance))", content: content, trigger: nil)
        UNUserNotificationCenter.current().add(request)
    }

    private func load() {
        let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601
        let savedData = UserDefaults.standard.data(forKey: savedKey)
        let meters = savedData.flatMap { try? decoder.decode([SavedMeter].self, from: $0) } ?? Self.firstRunMeters
        let snapshots = UserDefaults.standard.data(forKey: snapshotKey).flatMap { try? decoder.decode([String: MeterSnapshot].self, from: $0) } ?? [:]
        cards = meters.map { MeterCardState(meter: $0, snapshot: snapshots[$0.id]) }
        if savedData == nil { persistMeters() }
    }

    private func persistAll() { persistMeters(); persistSnapshots() }
    private func persistMeters() { UserDefaults.standard.set(try? JSONEncoder().encode(cards.map(\.meter)), forKey: savedKey) }
    private func persistSnapshots() {
        var values: [String: MeterSnapshot] = [:]
        cards.forEach { values[$0.id] = $0.snapshot }
        let encoder = JSONEncoder(); encoder.dateEncodingStrategy = .iso8601
        UserDefaults.standard.set(try? encoder.encode(values), forKey: snapshotKey)
    }
}
