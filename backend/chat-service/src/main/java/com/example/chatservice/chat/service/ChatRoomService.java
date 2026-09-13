package com.example.chatservice.chat.service;

import com.example.chatservice.api.admin.dto.AdminChatRoomSummaryDto;
import com.example.chatservice.chat.dto.ChatReadCursorDto;
import com.example.chatservice.chat.dto.ChatRoomDto;
import com.example.chatservice.chat.dto.ChatRoomLastReadChatDto;
import com.example.chatservice.chat.entity.Chat;
import com.example.chatservice.chat.entity.ChatRoom;
import com.example.chatservice.chat.entity.ChatRoomMember;
import com.example.chatservice.chat.repository.ChatRepository;
import com.example.chatservice.chat.repository.ChatRoomMemberRepository;
import com.example.chatservice.chat.repository.ChatRoomRepository;
import com.example.chatservice.chat.repository.ChatRoomSort;
import com.example.chatservice.common.exception.CustomException;
import com.example.chatservice.common.exception.ErrorCode;
import com.example.chatservice.kafka.producer.KafkaProducerService;
import com.example.chatservice.member.client.MemberFeignClient;
import com.example.chatservice.member.dto.MemberDto;
import com.example.chatservice.member.dto.ChatRoomMemberDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRepository chatRepository;
    private final MemberFeignClient memberFeignClient;
    private final ModelMapper modelMapper;
    private final KafkaProducerService kafkaProducerService;

    public List<ChatRoomDto> searchChatRoomByUserId(String memberId) {
        List<ChatRoomMember> chatRoomMemberList = chatRoomMemberRepository.findAllByMemberId(Long.valueOf(memberId));
        if (chatRoomMemberList.isEmpty()) {
            // 빈 id 목록으로 member-service 를 부르면 경로 변수가 비어 404 → 500 이 된다. 방이 없으면 바로 빈 목록.
            return List.of();
        }

        List<ChatRoom> chatRooms = chatRoomMemberList
                .stream()
                .map(ChatRoomMember::getChatRoom)
                .toList();

        List<Long> memberIds = chatRooms
                .stream()
                .map(chatRoom ->
                        chatRoom.getChatRoomMemberList()
                                .stream()
                                .map(ChatRoomMember::getMemberId)
                                .collect(Collectors.toList())
                ).toList()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        List<MemberDto> memberDtoList = memberFeignClient.getMembersById(memberIds);

        return chatRooms
                .stream()
                .map(chatRoom -> {
                    List<MemberDto> members = memberDtoList
                            .stream()
                            .filter(m -> chatRoom.getChatRoomMemberList()
                                    .stream()
                                    .anyMatch(c -> c.getMemberId().equals(m.getId())))
                            .collect(Collectors.toList());

                    return new ChatRoomDto(chatRoom, members);
                }).collect(Collectors.toList());
    }

    public ChatRoomDto searchChatRoomByRoomId(String roomId) {
        ChatRoom chatRoom = chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND_ERROR, roomId));

        List<Long> chatRoomMemberIds = chatRoom.getChatRoomMemberList()
                .stream()
                .map(ChatRoomMember::getMemberId)
                .collect(Collectors.toList());

        List<MemberDto> members = memberFeignClient.getMembersById(chatRoomMemberIds);

        return new ChatRoomDto(chatRoom, members);
    }

    public List<ChatRoomDto> searchOneOnOneChatRoom(String userId, String roomUserId) {
        MemberDto member = memberFeignClient.getMember(userId);
        List<MemberDto> members = memberFeignClient.getMembersByUserId(new ArrayList<>(Arrays.asList(userId, roomUserId)));

        List<ChatRoomMember> chatRoomMemberList = chatRoomMemberRepository.findAllByMemberId(member.getId());
        List<ChatRoom> chatRoomList = chatRoomMemberList.stream()
//                .filter(ChatRoomMember::isOneOnOne)
                .map(ChatRoomMember::getChatRoom)
                .filter(chatRoom -> {
                    List<Long> roomMemberId = chatRoom.getChatRoomMemberList().stream()
                            .map(ChatRoomMember::getMemberId)
                            .toList();

                    return roomMemberId.size() == 2 && roomMemberId.contains(members.get(0).getId()) && roomMemberId.contains(members.get(1).getId());
                })
                .toList();

        return chatRoomList.stream()
                .map(chatRoom -> {
                    return new ChatRoomDto(chatRoom, members);
                })
                .collect(Collectors.toList());
    }

    /**
     * 같은 멤버 구성의 방이 이미 있으면 새로 만들지 않고 그 방을 돌려준다.
     * 클라이언트는 응답의 roomId 로 이동하므로 자연스럽게 기존 방으로 들어간다.
     */
    public ChatRoomDto createChatRoom(List<Long> ids) {
        List<Long> memberIds = ids.stream().distinct().toList();
        List<MemberDto> members = memberFeignClient.getMembersById(memberIds);
        if(members.isEmpty()) {
            throw new CustomException(ErrorCode.USERID_NOT_FOUND_ERROR, ids.toString());
        }

        Optional<ChatRoom> existingRoom = findChatRoomByExactMembers(members);
        if (existingRoom.isPresent()) {
            return new ChatRoomDto(existingRoom.get(), members);
        }

        //legacy
        String chatRoomCreateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        ChatRoom chatRoom = new ChatRoom(UUID.randomUUID().toString(), "새로운 채팅방", "", "", "", chatRoomCreateTime);

        ChatRoomDto chatRoomDto = addNewChatRoomMember(chatRoom, members);

        // ws-service 컨슈머가 이 이벤트를 받고 바로 방을 조회하므로, 커밋 전에 보내면
        // 아직 안 보이는 방을 찾게 된다. 커밋 이후로 미룬다.
        publishRoomCreatedAfterCommit(chatRoomDto.getRoomId());

        return chatRoomDto;
    }

    /**
     * 트랜잭션이 커밋된 뒤에만 발행한다. 트랜잭션 동기화가 없는 컨텍스트(단위 테스트 등)에서
     * 호출되면 등록할 트랜잭션이 없으므로 즉시 보낸다.
     */
    private void publishRoomCreatedAfterCommit(String roomId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    kafkaProducerService.sendRoomCreatedMessage(roomId);
                }
            });
        } else {
            kafkaProducerService.sendRoomCreatedMessage(roomId);
        }
    }

    public ChatRoomDto exitChatRoomMember(String roomId, String userId) {
        ChatRoom chatRoom = chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND_ERROR, roomId));
        List<MemberDto> members = memberFeignClient.getMembersByUserId(new ArrayList<>(Collections.singletonList(userId)));
        if(members.isEmpty()) {
            throw new CustomException(ErrorCode.USERID_NOT_FOUND_ERROR, userId);
        }

        ChatRoomDto chatRoomDto = addNewChatRoomMember(chatRoom, members);

        ChatRoomMemberDto memberInviteDto = new ChatRoomMemberDto(chatRoomDto, members);

        List<MemberDto> invited = memberFeignClient.exitChatRoom(memberInviteDto);

        return addNewChatRoomMember(chatRoom, invited);
    }

    public ChatRoomDto updateChatRoom(String roomId, ChatRoomDto chatRoomDto) {
        ChatRoom findChatRoom = chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND_ERROR, roomId));

        findChatRoom.updateChatRoom(chatRoomDto);

        ChatRoom updateChatRoom = chatRoomRepository.save(findChatRoom);
        return modelMapper.map(updateChatRoom, ChatRoomDto.class);
    }

    public ChatRoomDto addMemberChatRoom(String roomId, List<String> userIds) {
        ChatRoom chatRoom = chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND_ERROR, roomId));
        List<MemberDto> members = memberFeignClient.getMembersByUserId(userIds);
        if(members.isEmpty()) {
            throw new CustomException(ErrorCode.USERID_NOT_FOUND_ERROR, userIds.toString());
        }

        ChatRoomDto chatRoomDto = addNewChatRoomMember(chatRoom, members);

        ChatRoomMemberDto memberInviteDto = new ChatRoomMemberDto(chatRoomDto, members);

        List<MemberDto> invited = memberFeignClient.inviteChatRoom(memberInviteDto);

        return addNewChatRoomMember(chatRoom, invited);
    }

    public List<ChatRoomLastReadChatDto> searchUnreadChatRoom(String userId) {
        List<ChatRoomMember> chatRoomMemberList =
                chatRoomMemberRepository.findAllByMemberId(Long.valueOf(userId));

        // 마지막 메시지를 내가 보냈다면 그 방은 이미 본 것으로 본다.
        // chat.sender 는 member id 가 아니라 userId 문자열이라 한 번 조회해 둔다.
        String myUserId = findMyUserId(userId);
        Map<Long, String> senderByChatId = findLastChatSenders(chatRoomMemberList);

        return chatRoomMemberList.stream()
                .map(chatRoomMember -> {
                    String roomId = chatRoomMember.getChatRoom().getRoomId();

                    // 메시지가 하나도 없는 새 방은 두 값이 모두 null 이다.
                    Long lastReadChatId = parseIdOrZero(chatRoomMember.getLastReadChatId());
                    Long lastSendChatId = parseIdOrZero(chatRoomMember.getChatRoom().getLastChatId());

                    Long unreadChatCount = 0L;
                    if (lastSendChatId > lastReadChatId
                            && !sentByMe(senderByChatId, lastSendChatId, myUserId)) {
                        // BETWEEN 은 양끝을 포함한다. 마지막으로 '읽은' 메시지는 빼야 하므로 +1.
                        unreadChatCount = chatRepository.countByRoomIdAndIdBetween(
                                roomId, lastReadChatId + 1, lastSendChatId);
                    }

                    return ChatRoomLastReadChatDto.createChatRoomLastReadChatDto(
                            roomId, lastSendChatId, lastReadChatId, unreadChatCount);
                })
                .toList();
    }

    /** 마지막 메시지의 발신자가 나인지. 판단할 수 없으면 false 라서 기존 계산이 그대로 남는다. */
    private boolean sentByMe(Map<Long, String> senderByChatId, Long lastSendChatId, String myUserId) {
        if (myUserId == null) {
            return false;
        }
        return myUserId.equals(senderByChatId.get(lastSendChatId));
    }

    /** member-service 가 죽어도 배지 조회 자체는 살아야 하므로 실패를 삼키고 null 을 준다. */
    private String findMyUserId(String memberId) {
        try {
            List<MemberDto> members = memberFeignClient.getMembersById(List.of(Long.valueOf(memberId)));
            if (members == null || members.isEmpty()) {
                return null;
            }
            return members.get(0).getUserId();
        } catch (Exception e) {
            log.warn("failed to resolve userId for member {}, falling back to id-only count", memberId, e);
            return null;
        }
    }

    /** 방마다 마지막 채팅을 따로 조회하면 N+1 이 된다. 한 번에 가져온다. */
    private Map<Long, String> findLastChatSenders(List<ChatRoomMember> chatRoomMemberList) {
        List<Long> lastChatIds = chatRoomMemberList.stream()
                .map(chatRoomMember -> parseIdOrZero(chatRoomMember.getChatRoom().getLastChatId()))
                .filter(chatId -> chatId > 0)
                .distinct()
                .toList();

        if (lastChatIds.isEmpty()) {
            return Map.of();
        }

        return chatRepository.findAllByIdIn(lastChatIds).stream()
                .filter(chat -> chat.getSender() != null)
                .collect(Collectors.toMap(Chat::getId, Chat::getSender, (a, b) -> a));
    }

    private Long parseIdOrZero(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 방 멤버별 읽음 커서. chat.sender 는 member id 가 아니라 userId 문자열이라
     * member-service 에서 한 번에 변환해 내려준다.
     * member-service 가 죽으면 빈 목록을 준다. 숫자가 안 뜨는 것이 500 보다 낫다.
     */
    public List<ChatReadCursorDto> searchReadCursors(String roomId) {
        ChatRoom chatRoom = chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND_ERROR, roomId));

        List<ChatRoomMember> chatRoomMemberList = chatRoom.getChatRoomMemberList();
        if (chatRoomMemberList.isEmpty()) {
            return List.of();
        }

        List<Long> memberIds = chatRoomMemberList.stream()
                .map(ChatRoomMember::getMemberId)
                .toList();

        Map<Long, String> userIdByMemberId;
        try {
            userIdByMemberId = memberFeignClient.getMembersById(memberIds).stream()
                    .filter(member -> member.getUserId() != null)
                    .collect(Collectors.toMap(MemberDto::getId, MemberDto::getUserId, (a, b) -> a));
        } catch (Exception e) {
            log.warn("failed to resolve userIds for room {}, returning no cursors", roomId, e);
            return List.of();
        }

        return chatRoomMemberList.stream()
                .map(chatRoomMember -> {
                    String userId = userIdByMemberId.get(chatRoomMember.getMemberId());
                    if (userId == null) {
                        return null;
                    }
                    return ChatReadCursorDto.builder()
                            .userId(userId)
                            .lastReadChatId(parseIdOrZero(chatRoomMember.getLastReadChatId()))
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 이 엔드포인트는 호출자가 둘이라 식별자 형태가 둘이다.
     * 안드로이드는 REST 로 memberId(숫자, PK) 를 보내고 — 방 목록 안읽음 배지를 지우는
     * 기존 경로 — ws-service 의 READ 프레임은 OAuth userId 문자열을 보낸다.
     * 그래서 먼저 memberId 로 시도하고, 방 멤버 중에 없으면(혹은 애초에 Long 파싱이
     * 안 되면) OAuth userId 로 보고 member-service 에서 한 번 변환한다.
     */
    public void updateLastReadChat(String roomId, String userId) {
        ChatRoom chatRoom = chatRoomRepository.findByRoomId(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHATROOM_NOT_FOUND_ERROR, roomId));

        Long memberId = null;
        try {
            memberId = Long.valueOf(userId);
        } catch (NumberFormatException e) {
            // OAuth userId 는 Long 범위를 넘는 21자리 숫자라 여기로 떨어진다.
        }

        final Long directMemberId = memberId;
        ChatRoomMember findChatRoomMember = chatRoom.getChatRoomMemberList().stream()
                .filter(chatRoomMember -> chatRoomMember.getMemberId().equals(directMemberId))
                .findFirst()
                .orElse(null);

        if (findChatRoomMember == null) {
            MemberDto member = memberFeignClient.getMember(userId);
            findChatRoomMember = chatRoom.getChatRoomMemberList().stream()
                    .filter(chatRoomMember -> chatRoomMember.getMemberId().equals(member.getId()))
                    .findFirst()
                    .orElseThrow(() -> new CustomException(ErrorCode.USERID_NOT_FOUND, userId));
        }

        findChatRoomMember.updateLastReadChatId(chatRoom.getLastChatId());
    }

    /** 멤버 id 집합이 정확히 일치하는 방을 DB 집계로 찾는다. 1:1 방과 그룹방 모두 해당한다. */
    private Optional<ChatRoom> findChatRoomByExactMembers(List<MemberDto> members) {
        Set<Long> wantedMemberIds = members.stream()
                .map(MemberDto::getId)
                .collect(Collectors.toSet());

        return chatRoomMemberRepository.findRoomIdByExactMemberIds(wantedMemberIds)
                .flatMap(chatRoomRepository::findById);
    }

    private ChatRoomDto addNewChatRoomMember(ChatRoom chatRoom, List<MemberDto> members) {
        List<ChatRoomMember> chatRoomMemberList = members.stream()
                .map(member -> {
                    ChatRoomMember chatRoomMember = new ChatRoomMember(member.getId(), chatRoom.getLastChatId(), chatRoom);
                    chatRoom.getChatRoomMemberList().add(chatRoomMember);
                    return chatRoomMember;
                })
                .collect(Collectors.toList());

        chatRoomMemberRepository.saveAll(chatRoomMemberList);
        ChatRoom newRoom = chatRoomRepository.save(chatRoom);

        return modelMapper.map(newRoom, ChatRoomDto.class);
    }

    /**
     * 백오피스 방 목록. 멤버 수를 세기 위해 같은 트랜잭션 안에서 컬렉션을 초기화한다.
     * 정렬은 {@link ChatRoomSort} 가 정한다 — 멤버 수만 엔티티 속성이 아니라서 전용 질의로 간다.
     * pageable 에는 쪽 번호와 크기만 있으면 된다. 정렬은 여기서 붙인다.
     */
    @Transactional(readOnly = true)
    public Page<AdminChatRoomSummaryDto> searchChatRoomsForAdmin(ChatRoomSort sort, Pageable pageable) {
        PageRequest request = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort.sort());
        Page<ChatRoom> rooms = switch (sort) {
            case MEMBER_COUNT_ASC -> chatRoomRepository.findAllOrderByMemberCountAsc(request);
            case MEMBER_COUNT_DESC -> chatRoomRepository.findAllOrderByMemberCountDesc(request);
            default -> chatRoomRepository.findAll(request);
        };
        return rooms.map(AdminChatRoomSummaryDto::new);
    }
}
