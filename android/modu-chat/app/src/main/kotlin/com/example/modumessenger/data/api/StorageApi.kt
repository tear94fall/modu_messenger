package com.example.modumessenger.data.api

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface StorageApi {

    /**
     * 파트 이름은 반드시 `file`. 응답 본문은 저장된 파일 이름 문자열이라 [ResponseBody] 로 받아 직접 읽는다
     * (ScalarsConverter 를 쓰지 않는다).
     */
    @Multipart
    @POST("storage-service/api-public/upload")
    suspend fun upload(@Part file: MultipartBody.Part): ResponseBody
}
