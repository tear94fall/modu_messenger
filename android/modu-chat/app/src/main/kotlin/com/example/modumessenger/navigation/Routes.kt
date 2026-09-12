package com.example.modumessenger.navigation

import android.net.Uri

/**
 * 화면 주소 한 곳(스펙 §5). 상수는 `NavHost` 등록에, 함수는 이동에 쓴다.
 * 경로에 넣는 값은 전부 [Uri.encode] 를 거친다(방 id 나 파일 이름에 `/` 가 섞여도 깨지지 않게).
 */
object Routes {

    /** 앱의 첫 화면. 세션 판정이 끝나면 [LOGIN] 이나 [MAIN] 으로 갈아타고 백스택에서 사라진다. */
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val MAIN = "main"

    const val PROFILE = "profile/{memberId}"
    const val PROFILE_EDIT = "profileEdit"
    const val PROFILE_HISTORY = "profileHistory/{memberId}"
    const val PROFILE_IMAGE = "profileImage/{memberId}/{type}?profileId={profileId}"

    const val CHAT = "chat/{roomId}"
    const val CHAT_ROOM_EDIT = "chatRoomEdit/{roomId}"
    const val INVITE = "invite/{roomId}?members={members}"
    const val CREATE_ROOM = "createRoom"
    const val CHAT_IMAGES = "chatImages?ids={ids}"

    const val SEARCH_FRIENDS = "searchFriends"
    const val FIND_FRIENDS = "findFriends"
    const val SET_FRIENDS = "setFriends"

    const val SETUP = "setup"
    const val ACCOUNT = "account"
    const val NOTICE = "notice"
    const val APP_INFO = "appInfo"

    const val ARG_MEMBER_ID = "memberId"
    const val ARG_ROOM_ID = "roomId"
    const val ARG_TYPE = "type"
    const val ARG_PROFILE_ID = "profileId"
    const val ARG_MEMBERS = "members"
    const val ARG_IDS = "ids"

    /** 여러 값을 한 쿼리 인자에 담을 때 쓰는 구분자. */
    const val LIST_SEPARATOR = ","

    fun profile(memberId: Long): String = "profile/$memberId"

    fun profileHistory(memberId: Long): String = "profileHistory/$memberId"

    /** [type] 은 `PROFILE_IMAGE` / `PROFILE_WALLPAPER` 같은 `ProfileType` 이름. */
    fun profileImage(memberId: Long, type: String, profileId: Long? = null): String =
        "profileImage/$memberId/${Uri.encode(type)}" +
            if (profileId != null) "?profileId=$profileId" else ""

    fun chat(roomId: String): String = "chat/${Uri.encode(roomId)}"

    fun chatRoomEdit(roomId: String): String = "chatRoomEdit/${Uri.encode(roomId)}"

    /** [memberUserIds] 는 이미 방에 있는 사람들(초대 목록에서 걸러낸다). */
    fun invite(roomId: String, memberUserIds: List<String> = emptyList()): String =
        "invite/${Uri.encode(roomId)}?members=${Uri.encode(memberUserIds.joinToString(LIST_SEPARATOR))}"

    fun chatImages(chatIds: List<String>): String =
        "chatImages?ids=${Uri.encode(chatIds.joinToString(LIST_SEPARATOR))}"

    /** 콤마로 이어 붙인 쿼리 인자를 되돌린다. */
    fun splitList(value: String?): List<String> =
        value?.split(LIST_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}
