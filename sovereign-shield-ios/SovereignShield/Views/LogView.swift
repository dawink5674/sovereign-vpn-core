import SwiftUI

struct LogView: View {
    @ObservedObject var vpnManager = VPNManager.shared

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("CONNECTION LOG")
                    .font(.system(size: 18, weight: .bold))
                    .tracking(3)
                    .foregroundColor(Theme.textBright)

                Spacer()

                Button(action: { vpnManager.clearLogs() }) {
                    HStack(spacing: 4) {
                        Image(systemName: "trash")
                            .font(.system(size: 12))
                        Text("CLEAR")
                            .font(.system(size: 10, weight: .bold))
                            .tracking(1)
                    }
                    .foregroundColor(Theme.textMuted)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(
                        Capsule()
                            .fill(Theme.spaceCard.opacity(0.8))
                    )
                    .overlay(
                        Capsule()
                            .stroke(Theme.glassBorder, lineWidth: 1)
                    )
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 12)

            // Log entries
            if vpnManager.logs.isEmpty {
                emptyState
            } else {
                logList
            }
        }
        .background(Theme.spaceBlack)
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: 12) {
            Spacer()
            Image(systemName: "doc.text")
                .font(.system(size: 40))
                .foregroundColor(Theme.textMuted)
            Text("No log entries")
                .font(.bodyMedium)
                .foregroundColor(Theme.textSecondary)
            Text("Connection events will appear here")
                .font(.bodySmall)
                .foregroundColor(Theme.textMuted)
            Spacer()
        }
    }

    // MARK: - Log List

    private var logList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 2) {
                    ForEach(vpnManager.logs) { entry in
                        logEntryRow(entry)
                            .id(entry.id)
                    }
                }
                .padding(.horizontal, 12)
            }
            .onChange(of: vpnManager.logs.count) { _ in
                if let lastLog = vpnManager.logs.last {
                    withAnimation(.easeOut(duration: 0.3)) {
                        proxy.scrollTo(lastLog.id, anchor: .bottom)
                    }
                }
            }
        }
    }

    // MARK: - Log Entry Row

    private func logEntryRow(_ entry: LogEntry) -> some View {
        HStack(alignment: .top, spacing: 10) {
            // Icon
            Image(systemName: entry.icon)
                .font(.system(size: 12))
                .foregroundColor(entry.color)
                .frame(width: 20)

            // Timestamp
            Text(entry.formattedTime)
                .font(.monoSmall)
                .foregroundColor(Theme.textMuted)

            // Message
            Text(entry.message)
                .font(.system(size: 12))
                .foregroundColor(entry.color.opacity(0.9))
                .lineLimit(2)

            Spacer()
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 12)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(Theme.spaceCard.opacity(0.3))
        )
    }
}

#Preview {
    LogView()
}
