package com.example.modumessenger.Retrofit;

import com.example.modumessenger.dto.ChatRoomDto;
import com.example.modumessenger.dto.MemberDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface RetrofitChatRoomAPI {

    @GET("chat-service/chat/{memberId}/rooms")
    Call<List<ChatRoomDto>> RequestChatRooms(@Path("memberId") String memberId);

    @GET("chat-service/chat/{roomId}/room")
    Call<ChatRoomDto> RequestChatRoom(@Path("roomId") String roomId);

    @GET("chat-service/chat/search/{roomName}")
    Call<List<ChatRoomDto>> RequestSearchChatRooms(@Path("roomName") String roomName);

    @POST("chat-service/chat/chat/room")
    Call<ChatRoomDto> RequestCreateChatRoom(@Body List<Long> ids);

    @DELETE("chat-service/chat/{roomId}/{userId}")
    Call<ChatRoomDto> RequestExitChatRoom(@Path("roomId") String roomId, @Path("userId") String userId);

    @POST("chat-service/chat/{roomId}/room")
    Call<ChatRoomDto> RequestUpdateChatRoom(@Path("roomId") String roomId, @Body ChatRoomDto chatRoomDto);

    @POST("chat-service/chat/{roomId}/member")
    Call<ChatRoomDto> RequestAddMemberChatRoom(@Path("roomId") String roomId, @Body List<String> userIds);
}