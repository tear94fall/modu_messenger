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

    @GET("chat/{userId}/rooms")
    Call<List<ChatRoomDto>> RequestChatRooms(@Path("userId") String userId);

    @GET("chat/{roomId}/room")
    Call<ChatRoomDto> RequestChatRoom(@Path("roomId") String roomId);

    @POST("chat/room/member")
    Call<ChatRoomDto> RequestCheckChatRoom(@Body List<String> userIds);

    @POST("chat/chat/room")
    Call<ChatRoomDto> RequestCreateChatRoom(@Body List<String> userIds);

    @DELETE("chat/{roomId}/{userId}")
    Call<ChatRoomDto> RequestExitChatRoom(@Path("roomId") String roomId, @Path("userId") String userId);

    @POST("chat/{roomId}/room")
    Call<ChatRoomDto> RequestUpdateChatRoom(@Path("roomId") String roomId, @Body ChatRoomDto chatRoomDto);

    @POST("chat/{roomId}/member")
    Call<ChatRoomDto> RequestAddMemberChatRoom(@Path("roomId") String roomId, @Body List<String> userIds);
}