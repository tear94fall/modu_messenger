package com.example.modumessenger.chat.controller;

import com.example.modumessenger.chat.dto.ChatRoomDto;
import com.example.modumessenger.chat.service.ChatRoomService;
import com.example.modumessenger.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatRoomController {
    private final ChatRoomService chatRoomService;
    private final MemberService memberService;

    @GetMapping("/chat/{userId}/rooms")
    public ResponseEntity<List<ChatRoomDto>> chatRoomList(@Valid @PathVariable("userId") String userId) {
        List<ChatRoomDto> chatRoomList = chatRoomService.searchChatRoomByUserId(userId);
        return ResponseEntity.ok().body(chatRoomList);
    }

    @GetMapping("/chat/{roomId}/room")
    public ResponseEntity<ChatRoomDto> getChatRoomInfo(@Valid @PathVariable("roomId") String roomId) {
        ChatRoomDto chatRoomDto = chatRoomService.searchChatRoomByRoomId(roomId);
        return ResponseEntity.ok().body(chatRoomDto);
    }

    @PostMapping("/chat/chat/room")
    public ResponseEntity<ChatRoomDto> createChatRoom(@Valid @RequestBody List<String> userIds) {
        ChatRoomDto chatRoomDto = chatRoomService.createChatRoom(userIds);
        return ResponseEntity.ok().body(chatRoomDto);
    }

    @DeleteMapping("/chat/{roomId}/{userId}")
    public ResponseEntity<ChatRoomDto> removeChatRoomMember(@Valid @PathVariable("roomId") String roomId, @PathVariable("userId") String userId) {
        ChatRoomDto chatRoomDto = chatRoomService.removeChatRoomMember(roomId, userId);
        return ResponseEntity.ok().body(chatRoomDto);
    }

    @PostMapping("/chat/{roomId}/room")
    public ResponseEntity<ChatRoomDto> updateChatRoom(@Valid @PathVariable("roomId") String roomId, @RequestBody ChatRoomDto requestChatRoomDto) {
        ChatRoomDto chatRoomDto = chatRoomService.updateChatRoom(roomId, requestChatRoomDto);
        return ResponseEntity.ok().body(chatRoomDto);
    }

    @PostMapping("/chat/{roomId}/member")
    public ResponseEntity<ChatRoomDto> addMemberChatRoom(@Valid @PathVariable String roomId, @RequestBody List<String> userIds) {
        ChatRoomDto chatRoomDto = chatRoomService.addMemberChatRoom(roomId, userIds);
        return ResponseEntity.ok().body(chatRoomDto);
    }

    @PostMapping("/chat/room/{userId}")
    public ResponseEntity<List<ChatRoomDto>> getOneOnOneChatRoom(@PathVariable("userId") String userId, @Valid @RequestBody String roomUserId) {
        List<ChatRoomDto> chatRoomDtoList = chatRoomService.searchOneOnOneChatRoom(userId, roomUserId);
        return ResponseEntity.ok().body(chatRoomDtoList);
    }
}
