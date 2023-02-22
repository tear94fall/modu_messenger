package com.example.modumessenger.common.handler;

import com.example.modumessenger.chat.dto.ChatDto;
import com.example.modumessenger.chat.dto.ChatRoomDto;
import com.example.modumessenger.chat.service.ChatRoomService;
import com.example.modumessenger.chat.service.ChatService;
import com.example.modumessenger.fcm.dto.FcmMessageDto;
import com.example.modumessenger.fcm.service.FcmService;
import com.example.modumessenger.message.service.MessageService;
import com.example.modumessenger.messaging.entity.ChatMessage;
import com.example.modumessenger.messaging.entity.SubscribeType;
import com.example.modumessenger.messaging.service.MessagingPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.parser.ParseException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.modumessenger.common.time.TimeCalculator.calculateTime;

@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler implements MessageListener {

    private final MessageService messageService;
    private final ChatRoomService chatRoomService;
    private final ChatService chatService;
    private final FcmService fcmService;
    private final MessagingPublisher messagingPublisher;

    private final ObjectMapper objectMapper;

    private static final ConcurrentHashMap<String, WebSocketSession> CLIENTS = new ConcurrentHashMap<String, WebSocketSession>();
    private static final String FILE_UPLOAD_PATH = "/modu-chat/images";
    private static final String CHAT_MESSAGING_TOPIC_NAME = "modu-chat";

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException, FirebaseMessagingException {
        ChatDto recvChatDto = objectMapper.readValue(message.getPayload(), ChatDto.class);
        ChatRoomDto chatRoomDto = chatRoomService.searchChatRoomByRoomId(recvChatDto.getRoomId());

        if(chatRoomDto.checkChatRoomMember(recvChatDto.getSender())) return;

        recvChatDto.setChatTime(calculateTime(recvChatDto.getChatTime()));
        Long chatId = chatService.saveChat(recvChatDto);

        chatRoomDto.updateLastChat(chatId.toString(), recvChatDto);

        chatRoomService.updateChatRoom(chatRoomDto.getRoomId(), chatRoomDto);

        ChatMessage chatMessage = new ChatMessage(SubscribeType.BROAD_CAST, chatRoomDto.getRoomId(), chatId.toString());

        ChannelTopic channel = new ChannelTopic(CHAT_MESSAGING_TOPIC_NAME);
        messagingPublisher.publish(channel, chatMessage);

        // send to rabbitmq
        messageService.sendMsgToRabbitMq(recvChatDto);

        FcmMessageDto fcmMessageDto = new FcmMessageDto(chatRoomDto, recvChatDto);
        fcmService.sendTopicMessageWithData(fcmMessageDto);
    }

    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String url = Objects.requireNonNull(session.getUri()).toString();
        String roomId = url.split("/modu-chat/")[1];
        String sender = Objects.requireNonNull(session.getHandshakeHeaders().get("userId")).get(0);

        ChatRoomDto chatRoomDto = chatRoomService.searchChatRoomByRoomId(roomId);

        ByteBuffer byteBuffer = message.getPayload();
        boolean save = saveImageFile(byteBuffer);
        byteBuffer.position(0);

        if(save) {
            chatRoomDto.getMembers().forEach(memberDto -> {
                String userId = memberDto.getUserId();
                if (!sender.equals(userId)) {
                    WebSocketSession s = CLIENTS.get(userId);
                    if (s != null) {
                        try {
                            s.sendMessage(new BinaryMessage(byteBuffer));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);

        String url = Objects.requireNonNull(session.getUri()).toString();
        String roomId = url.split("/modu-chat/")[1];
        String userId = Objects.requireNonNull(session.getHandshakeHeaders().get("userId")).get(0);

        ChatRoomDto chatRoomDto = chatRoomService.searchChatRoomByRoomId(roomId);

        if(chatRoomDto!=null) {
            CLIENTS.put(userId, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);

        String url = Objects.requireNonNull(session.getUri()).toString();
        String roomId = url.split("/modu-chat/")[1];
        String userId = Objects.requireNonNull(session.getHandshakeHeaders().get("userId")).get(0);

        if(CLIENTS.get(userId)!=null) {
            CLIENTS.remove(userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

    }

    // recv from rabbitmq
    @RabbitListener(queues = "modu-chat.queue")
    public void consume(ChatDto chatDto) {
        log.info("message: {}", chatDto.toString());
        log.info(chatDto.toString());
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String subscribeMessage = new String(message.getBody());
            ChatMessage chatMessage = objectMapper.readValue(subscribeMessage, ChatMessage.class);
            log.info("[subscribe][message] {}", chatMessage);

            ChatRoomDto chatRoomDto = chatRoomService.searchChatRoomByRoomId(chatMessage.getRoomId());
            ChatDto chatDto = chatService.searchChatByRoomIdAndChatId(chatMessage.getRoomId(), chatMessage.getChatId());
            String payload = objectMapper.writeValueAsString(chatDto);

            TextMessage textMessage = new TextMessage(payload);

            chatRoomDto.getMembers().forEach(member -> {
                String userId = member.getUserId();
                WebSocketSession s = CLIENTS.get(userId);
                if (s != null) {
                    try {
                        s.sendMessage(textMessage);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean saveImageFile(ByteBuffer byteBuffer) {
        String fileName = "temp.jpg";
        File dir = new File(FILE_UPLOAD_PATH);

        if(!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(FILE_UPLOAD_PATH, fileName);
        FileOutputStream out = null;
        FileChannel outChannel = null;

        try {
            byteBuffer.flip();
            out = new FileOutputStream(file, true);
            outChannel = out.getChannel();
            byteBuffer.compact();
            outChannel.write(byteBuffer);
        }catch(Exception e) {
            e.printStackTrace();
        }finally {
            try {
                if(out != null) {
                    out.close();
                }
                if(outChannel != null) {
                    outChannel.close();
                }
            }catch (IOException e) {
                e.printStackTrace();
            }
        }

        byteBuffer.position(0);

        return true;
    }
}
