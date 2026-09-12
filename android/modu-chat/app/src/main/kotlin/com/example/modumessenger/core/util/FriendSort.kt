package com.example.modumessenger.core.util

/** 서버 member-service 의 `FriendSort` 와 문자열이 같아야 한다. 다르면 400 이 온다. */
object FriendSort {
    const val NAME_ASC = "name,asc"
    const val NAME_DESC = "name,desc"
    const val EMAIL_ASC = "email,asc"
    const val EMAIL_DESC = "email,desc"
    const val DEFAULT = NAME_ASC
}
