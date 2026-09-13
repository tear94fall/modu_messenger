package com.example.chatservice.api.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.chatservice.api.admin.dto.AdminChatRoomSummaryDto;
import com.example.chatservice.chat.entity.ChatRoom;
import com.example.chatservice.chat.repository.ChatRoomSort;
import com.example.chatservice.chat.service.ChatRoomService;
import com.example.chatservice.chat.service.ChatService;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ChatAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ChatRoomService chatRoomService;
    @MockitoBean ChatService chatService;

    @Test
    void withoutToken_is403() throws Exception {
        mockMvc.perform(get("/api-admin/chat/rooms")).andExpect(status().isForbidden());
    }

    @Test
    void rooms_returnsPage() throws Exception {
        ChatRoom room = new ChatRoom("r1", "room-name", "room.jpg", "hi", "1", "2024-01-01T00:00:00");
        AdminChatRoomSummaryDto dto = new AdminChatRoomSummaryDto(room);
        when(chatRoomService.searchChatRoomsForAdmin(any(), any())).thenReturn(new PageImpl<>(List.of(dto)));
        mockMvc.perform(get("/api-admin/chat/rooms").header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].roomName").value("room-name"))
                .andExpect(jsonPath("$.content[0].roomImage").value("room.jpg"));
    }

    @Test
    void chats_returnsPageNewestFirst() throws Exception {
        when(chatService.searchChatsForAdmin(eq("r1"), any())).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api-admin/chat/rooms/r1/chats").param("page", "0").param("size", "10").header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(chatService).searchChatsForAdmin(eq("r1"), captor.capture());
        Sort sort = captor.getValue().getSort();
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("createdDate").getDirection());
    }

    @Test
    void rooms_acceptsEachAllowedSort() throws Exception {
        when(chatRoomService.searchChatRoomsForAdmin(any(), any())).thenReturn(new PageImpl<>(List.of()));

        assertSortParsedAs("roomName,asc", ChatRoomSort.ROOM_NAME_ASC);
        assertSortParsedAs("roomName,desc", ChatRoomSort.ROOM_NAME_DESC);
        assertSortParsedAs("memberCount,asc", ChatRoomSort.MEMBER_COUNT_ASC);
        assertSortParsedAs("memberCount,desc", ChatRoomSort.MEMBER_COUNT_DESC);
        assertSortParsedAs("lastChatMsg,asc", ChatRoomSort.LAST_CHAT_MSG_ASC);
        assertSortParsedAs("lastChatMsg,desc", ChatRoomSort.LAST_CHAT_MSG_DESC);
        assertSortParsedAs("lastChatTime,desc", ChatRoomSort.LAST_CHAT_DESC);
        assertSortParsedAs("lastChatTime,asc", ChatRoomSort.LAST_CHAT_ASC);
        assertSortParsedAs("createdDate,asc", ChatRoomSort.CREATED_ASC);
        assertSortParsedAs("createdDate,desc", ChatRoomSort.CREATED_DESC);
    }

    /**
     * 정렬은 서비스가 붙인다. 컨트롤러는 허용 목록으로 해석한 값만 넘기고 Pageable 에는 쪽 정보만 싣는다.
     * 속성 정렬의 마지막 키는 언제나 id 다. 같은 값이 여럿이면 페이지를 넘길 때 같은 방이 두 번 보일 수 있다.
     */
    private void assertSortParsedAs(String param, ChatRoomSort expected) throws Exception {
        org.mockito.Mockito.clearInvocations(chatRoomService);
        mockMvc.perform(get("/api-admin/chat/rooms").param("sort", param).header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk());
        ArgumentCaptor<ChatRoomSort> sortCaptor = ArgumentCaptor.forClass(ChatRoomSort.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(chatRoomService).searchChatRoomsForAdmin(sortCaptor.capture(), pageableCaptor.capture());
        assertEquals(expected, sortCaptor.getValue());
        assertEquals(Sort.unsorted(), pageableCaptor.getValue().getSort());
        if (!expected.byMemberCount()) {
            assertEquals(expected.sort().iterator().next().getDirection(),
                    expected.sort().getOrderFor("id").getDirection());
        }
    }

    /** 멤버 수는 엔티티 속성이 아니라서 Sort 가 비어 있다. 정렬은 서비스의 전용 질의가 맡는다. */
    @Test
    void memberCountSort_carriesNoPropertySort() {
        assertEquals(Sort.unsorted(), ChatRoomSort.MEMBER_COUNT_ASC.sort());
        assertEquals(Sort.unsorted(), ChatRoomSort.MEMBER_COUNT_DESC.sort());
        assertEquals(true, ChatRoomSort.MEMBER_COUNT_ASC.byMemberCount());
        assertEquals(false, ChatRoomSort.ROOM_NAME_ASC.byMemberCount());
    }

    @Test
    void rooms_rejectsUnknownSort() throws Exception {
        // 목록에 값이 보이지 않는 열은 정렬할 수 없다.
        mockMvc.perform(get("/api-admin/chat/rooms").param("sort", "roomImage,desc").header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api-admin/chat/rooms").param("sort", "roomName,sideways").header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verify(chatRoomService, org.mockito.Mockito.never()).searchChatRoomsForAdmin(any(), any());
    }

    /** sort 를 안 주면 지금까지와 같은 최신 생성 순이다. */
    @Test
    void list_sortsNewestFirst() throws Exception {
        when(chatRoomService.searchChatRoomsForAdmin(any(), any())).thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api-admin/chat/rooms").header("X-Internal-Token", "test-internal-token")).andExpect(status().isOk());
        ArgumentCaptor<ChatRoomSort> captor = ArgumentCaptor.forClass(ChatRoomSort.class);
        org.mockito.Mockito.verify(chatRoomService).searchChatRoomsForAdmin(captor.capture(), any());
        assertEquals(ChatRoomSort.CREATED_DESC, captor.getValue());
        Sort sort = captor.getValue().sort();
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("createdDate").getDirection());
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("id").getDirection());
    }
}
