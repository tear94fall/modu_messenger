package com.example.modumessenger.data.socket

/** 채팅 소켓의 상태. 기존 자바 앱의 `Global/socket/ConnectionState` 와 같은 값이다. */
enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }
