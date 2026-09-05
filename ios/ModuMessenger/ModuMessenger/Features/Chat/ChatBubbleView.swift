import SwiftUI

/// 말풍선 하나. 내 것은 오른쪽 라벤더, 남의 것은 왼쪽 회색에 프로필/이름.
struct ChatBubbleView: View {
    let item: ChatMessage
    let isMine: Bool
    let sender: MemberDto?
    let showSender: Bool
    let unreadCount: Int
    let onRetry: () -> Void

    var body: some View {
        HStack(alignment: .bottom, spacing: 6) {
            if isMine {
                Spacer(minLength: 60)
                meta
                bubble
            } else {
                if showSender { AvatarView(fileName: sender?.profileImage, size: 36) } else { Color.clear.frame(width: 36, height: 1) }
                VStack(alignment: .leading, spacing: 3) {
                    if showSender { Text(sender?.displayName ?? item.dto.sender ?? "").font(.caption).foregroundStyle(.secondary) }
                    HStack(alignment: .bottom, spacing: 6) { bubble; meta }
                }
                Spacer(minLength: 60)
            }
        }
    }

    @ViewBuilder private var bubble: some View {
        switch item.dto.kind {
        case .image:
            RemoteImage(fileName: item.dto.message) {
                ZStack { Color(.tertiarySystemFill); ProgressView() }
            }
            .frame(width: 200, height: 200)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        default:
            Text(item.dto.message ?? "")
                .padding(.horizontal, 12).padding(.vertical, 8)
                .foregroundStyle(isMine ? Color.onBubble : Color.primary)
                .background(isMine ? Color.bubbleMine : Color.bubbleOther, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .textSelection(.enabled)
        }
    }

    private var meta: some View {
        VStack(alignment: isMine ? .trailing : .leading, spacing: 1) {
            if item.isFailed {
                Button(action: onRetry) { Label("재전송", systemImage: "exclamationmark.arrow.circlepath").font(.caption2).foregroundStyle(.red) }
            } else if item.isPending {
                Image(systemName: "clock").font(.caption2).foregroundStyle(.secondary)
            } else if unreadCount > 0 {
                Text("\(unreadCount)").font(.caption2.bold()).foregroundStyle(Color.brand)
            }
            Text(ChatTime.short(item.dto.chatTime)).font(.caption2).foregroundStyle(.secondary)
        }
    }
}
