import Foundation
import SwiftUI

enum MeterProvider: String, Codable, CaseIterable, Identifiable {
    case nesco = "NESCO"
    case desco = "DESCO"
    case pgcl = "PGCL"

    var id: Self { self }
    var label: String {
        switch self {
        case .nesco: "NESCO Electric"
        case .desco: "DESCO Electric"
        case .pgcl: "PGCL Gas"
        }
    }
    var inputLabel: String {
        switch self {
        case .nesco: "Meter number"
        case .desco: "Account or meter number"
        case .pgcl: "Customer code"
        }
    }
    var color: Color {
        switch self {
        case .nesco: .purple
        case .desco: .blue
        case .pgcl: .orange
        }
    }
    var symbol: String { self == .pgcl ? "flame.fill" : "bolt.fill" }
}

enum BillingType: String, Codable, CaseIterable, Identifiable {
    case prepaid = "Prepaid"
    case postpaid = "Postpaid"
    var id: Self { self }
}

struct SavedMeter: Codable, Identifiable, Hashable {
    var id: String { "\(provider.rawValue)-\(number)" }
    let number: String
    let nickname: String
    let provider: MeterProvider
    let billingType: BillingType
}

struct Field: Codable, Identifiable, Hashable {
    var id: String { label }
    let label: String
    let value: String
}

struct HistoryRow: Codable, Identifiable, Hashable {
    var id: String { title + fields.map(\.value).joined() }
    let title: String
    let fields: [Field]
}

struct MeterSnapshot: Codable, Hashable {
    var balance: Double?
    var balanceAsOf: String?
    var summary: String?
    var isDue = false
    var customerName: String?
    var lastPaymentAmount: Double?
    var lastPaymentDate: String?
    var predictionDays: Double?
    var details: [Field] = []
    var primaryHistoryTitle = "History"
    var primaryHistory: [HistoryRow] = []
    var secondaryHistoryTitle: String?
    var secondaryHistory: [HistoryRow] = []
    var fetchedAt = Date()
}

struct MeterCardState: Identifiable {
    let meter: SavedMeter
    var snapshot: MeterSnapshot?
    var isLoading = false
    var error: String?
    var id: String { meter.id }
}

enum BillBoardError: LocalizedError {
    case invalidIdentifier
    case notFound(String)
    case badResponse(String)
    case parsing(String)

    var errorDescription: String? {
        switch self {
        case .invalidIdentifier: "Enter a valid number (4–30 letters, numbers, or hyphens)."
        case .notFound(let message), .badResponse(let message), .parsing(let message): message
        }
    }
}

extension Double {
    var taka: String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .currency
        formatter.currencySymbol = "৳"
        formatter.maximumFractionDigits = 2
        return formatter.string(from: NSNumber(value: self)) ?? "৳\(self)"
    }
}
