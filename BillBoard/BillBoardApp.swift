//
//  BillBoardApp.swift
//  BillBoard
//
//  Created by Akif Islam on 3/11/26.
//

import SwiftUI
import UserNotifications

@main
struct BillBoardApp: App {
    @State private var store = DashboardStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(store)
                .preferredColorScheme(.dark)
                .task {
                    _ = try? await UNUserNotificationCenter.current()
                        .requestAuthorization(options: [.alert, .sound, .badge])
                }
        }
    }
}
