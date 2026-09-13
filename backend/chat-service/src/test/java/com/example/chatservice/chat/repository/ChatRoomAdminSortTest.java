package com.example.chatservice.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.chatservice.chat.entity.ChatRoom;
import com.example.chatservice.chat.entity.ChatRoomMember;
import com.example.chatservice.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * 백오피스 방 목록 정렬을 H2 에서 확인한다.
 * 멤버 수는 엔티티 필드가 아니라 컬렉션 크기라 {@link org.springframework.data.domain.Sort} 로 못 담는다.
 * JPQL 의 size() 로 정렬하는 전용 질의가 방향과 페이지를 모두 지키는지 본다.
 */
@DataJpaTest
@Import(QuerydslConfig.class)
class ChatRoomAdminSortTest {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @BeforeEach
    void setUp() {
        saveRoom("room-three", "Charlie", "zebra", 1L, 2L, 3L);
        saveRoom("room-one", "Alpha", "apple", 1L);
        saveRoom("room-two", "Bravo", "melon", 2L, 3L);
    }

    private void saveRoom(String roomId, String roomName, String lastChatMsg, Long... memberIds) {
        ChatRoom room = chatRoomRepository.save(
                new ChatRoom(roomId, roomName, "", lastChatMsg, "", "2026-09-08 00:00:00"));
        for (Long memberId : memberIds) {
            chatRoomMemberRepository.save(new ChatRoomMember(memberId, "", room));
        }
    }

    @Test
    @DisplayName("멤버 수 오름차순은 사람이 적은 방부터다")
    void ordersByMemberCountAscending() {
        Page<ChatRoom> page = chatRoomRepository.findAllOrderByMemberCountAsc(
                PageRequest.of(0, 10, ChatRoomSort.MEMBER_COUNT_ASC.sort()));

        assertThat(page.getContent()).extracting(ChatRoom::getRoomId)
                .containsExactly("room-one", "room-two", "room-three");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("멤버 수 내림차순은 사람이 많은 방부터다")
    void ordersByMemberCountDescending() {
        Page<ChatRoom> page = chatRoomRepository.findAllOrderByMemberCountDesc(
                PageRequest.of(0, 10, ChatRoomSort.MEMBER_COUNT_DESC.sort()));

        assertThat(page.getContent()).extracting(ChatRoom::getRoomId)
                .containsExactly("room-three", "room-two", "room-one");
    }

    /** 페이지를 잘라도 건수와 차례가 맞아야 한다. 안 그러면 쪽을 넘길 때 같은 방이 두 번 보인다. */
    @Test
    @DisplayName("멤버 수 정렬도 페이지를 자를 수 있다")
    void pagesMemberCountOrdering() {
        Page<ChatRoom> first = chatRoomRepository.findAllOrderByMemberCountAsc(
                PageRequest.of(0, 2, ChatRoomSort.MEMBER_COUNT_ASC.sort()));
        Page<ChatRoom> second = chatRoomRepository.findAllOrderByMemberCountAsc(
                PageRequest.of(1, 2, ChatRoomSort.MEMBER_COUNT_ASC.sort()));

        assertThat(first.getContent()).extracting(ChatRoom::getRoomId).containsExactly("room-one", "room-two");
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(second.getContent()).extracting(ChatRoom::getRoomId).containsExactly("room-three");
    }

    /** 나머지 열은 엔티티 속성이라 Sort 로 끝난다. 속성 이름을 잘못 적으면 여기서 걸린다. */
    @Test
    @DisplayName("마지막 메시지와 방 이름은 속성 정렬로 줄선다")
    void ordersByPropertySorts() {
        assertThat(chatRoomRepository.findAll(PageRequest.of(0, 10, ChatRoomSort.LAST_CHAT_MSG_ASC.sort())).getContent())
                .extracting(ChatRoom::getLastChatMsg)
                .containsExactly("apple", "melon", "zebra");
        assertThat(chatRoomRepository.findAll(PageRequest.of(0, 10, ChatRoomSort.LAST_CHAT_MSG_DESC.sort())).getContent())
                .extracting(ChatRoom::getLastChatMsg)
                .containsExactly("zebra", "melon", "apple");
        assertThat(chatRoomRepository.findAll(PageRequest.of(0, 10, ChatRoomSort.ROOM_NAME_ASC.sort())).getContent())
                .extracting(ChatRoom::getRoomId)
                .containsExactly("room-one", "room-two", "room-three");
    }
}
