package com.example.modumessenger.Retrofit;

import com.example.modumessenger.dto.ChatDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface RetrofitChatAPI {

    @GET("chat/{roomId}/chats")
    Call<List<ChatDto>> RequestChatHistory(@Path("roomId") String roomId);

    @GET("chat/{roomId}/{chatId}/{size}")
    Call<List<ChatDto>> RequestPrevChatList(@Path("roomId") String roomId, @Path("chatId") String chatId, @Path("size") String size);
}
