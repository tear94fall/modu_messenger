import SwiftUI
import PhotosUI

/// 채팅방. 안드로이드 ChatActivity.
struct ChatRoomView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var chat: ChatRepository
    @Environment(\.dismiss) private var dismiss
    let roomId: String

    @State private var draft = ""
    @State private var photo: PhotosPickerItem?
    @State private var sendingPhoto = false
    @State private var showMembers = false
    @State private var confirmExit = false
    @State private var loadingPrev = false

    private var room: ChatRoomDto? { chat.room(roomId: roomId) }
    private var messages: [ChatMessage] { chat.chats(roomId: roomId) }
    private var memberIds: [String] { room?.members?.compactMap(\.userId) ?? [] }

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 6) {
                        if messages.filter({ $0.id > 0 }).count >= ChatRepository.pageSize {
                            Button(loadingPrev ? "불러오는 중…" : "이전 메시지 보기") {
                                loadingPrev = true
                                Task { await chat.loadPrevious(roomId); loadingPrev = false }
                            }
                            .font(.footnote).disabled(loadingPrev).padding(.vertical, 8)
                        }
                        ForEach(Array(messages.enumerated()), id: \.element.id) { index, item in
                            let previous = index > 0 ? messages[index - 1] : nil
                            if previous == nil || !ChatTime.sameDay(previous?.dto.chatTime, item.dto.chatTime) {
                                DayHeader(text: ChatTime.dayLabel(item.dto.chatTime))
                            }
                            ChatBubbleView(
                                item: item,
                                isMine: item.dto.sender == session.myUserId,
                                sender: room?.member(userId: item.dto.sender ?? ""),
                                showSender: previous?.dto.sender != item.dto.sender || !ChatTime.sameMinute(previous?.dto.chatTime, item.dto.chatTime),
                                unreadCount: item.id > 0 ? chat.unreadMemberCount(roomId: roomId, chatId: item.id, memberIds: memberIds) : 0,
                                onRetry: { chat.retry(roomId: roomId, tempId: item.id) })
                            .id(item.id)
                        }
                    }
                    .padding(.horizontal, 12).padding(.vertical, 8)
                }
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: messages.last?.id) { _, last in
                    if let last { withAnimation { proxy.scrollTo(last, anchor: .bottom) } }
                }
                .onAppear { if let last = messages.last?.id { proxy.scrollTo(last, anchor: .bottom) } }
            }
            Divider()
            inputBar
        }
        .background(Color(.systemBackground))
        .navigationTitle(room?.displayName(myUserId: session.myUserId) ?? "채팅")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button { showMembers = true } label: { Label("대화상대 \(memberIds.count)", systemImage: "person.2") }
                    Button(role: .destructive) { confirmExit = true } label: { Label("나가기", systemImage: "rectangle.portrait.and.arrow.right") }
                } label: { Image(systemName: "line.3.horizontal") }
            }
        }
        .task { await chat.enterRoom(roomId) }
        .onDisappear { chat.leaveRoom() }
        .sheet(isPresented: $showMembers) { MembersSheet(members: room?.members ?? [], myUserId: session.myUserId) }
        .confirmationDialog("이 채팅방에서 나갈까요?", isPresented: $confirmExit, titleVisibility: .visible) {
            Button("나가기", role: .destructive) { Task { try? await chat.exitRoom(roomId: roomId); dismiss() } }
        }
        .onChange(of: photo) { _, item in
            guard let item else { return }
            Task { await sendPhoto(item) }
        }
    }

    private var inputBar: some View {
        HStack(alignment: .bottom, spacing: 8) {
            PhotosPicker(selection: $photo, matching: .images) {
                Image(systemName: "photo").font(.title3).frame(width: 36, height: 36)
            }
            .disabled(sendingPhoto)
            TextField("메시지 입력", text: $draft, axis: .vertical)
                .lineLimit(1...5)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            Button(action: sendText) {
                Image(systemName: "arrow.up.circle.fill").font(.title).frame(width: 36, height: 36)
            }
            .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .padding(.horizontal, 10).padding(.vertical, 8)
        .background(.bar)
    }

    private func sendText() {
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        draft = ""
        chat.sendText(roomId: roomId, text: text)
    }

    private func sendPhoto(_ item: PhotosPickerItem) async {
        sendingPhoto = true
        defer { sendingPhoto = false; photo = nil }
        guard let raw = try? await item.loadTransferable(type: Data.self),
              let jpeg = ImageDownscaler.jpegData(from: raw) else { return }
        try? await chat.sendImage(roomId: roomId, data: jpeg, fileName: "chat-\(Int(Date().timeIntervalSince1970)).jpg")
    }
}

private struct DayHeader: View {
    let text: String
    var body: some View {
        Text(text).font(.caption).foregroundStyle(.secondary)
            .padding(.horizontal, 12).padding(.vertical, 4)
            .background(Color(.secondarySystemBackground), in: Capsule())
            .padding(.vertical, 6)
    }
}

private struct MembersSheet: View {
    let members: [MemberDto]
    let myUserId: String
    var body: some View {
        NavigationStack {
            List(members) { member in
                MemberRow(member: member, subtitle: member.userId == myUserId ? "나" : member.statusMessage)
            }
            .navigationTitle("대화상대")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }
}
