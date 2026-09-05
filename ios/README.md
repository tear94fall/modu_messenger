# Modu-Messenger iOS

아이폰 전용 앱입니다. 안드로이드 앱과 같은 백엔드(gateway `:8000`)에 붙습니다.

## 요구 사항

- Xcode 16 이상 (Swift 5 언어 모드, iOS 17.0+)
- iPhone 전용 (`TARGETED_DEVICE_FAMILY = 1`), 세로 모드만 지원
- 외부 의존성은 Swift Package Manager 의 `GoogleSignIn-iOS` 하나뿐입니다. 프로젝트를 열면 자동으로 받아옵니다.

## 실행

```bash
open ios/ModuMessenger/ModuMessenger.xcodeproj
```

1. `Config/AppConfig.xcconfig` 에서 서버 주소를 확인합니다.
2. Google 클라이언트 ID 는 깃에 올리지 않는 `Config/Secrets.xcconfig` 를 만들어 적습니다 (아래 참고).
3. 시뮬레이터 또는 실기기를 골라 실행합니다. 실기기는 Mac 과 같은 Wi-Fi 에 있어야 합니다.

### 설정값 (`Config/AppConfig.xcconfig`)

| 키 | 설명 |
| --- | --- |
| `API_BASE_URL` | 게이트웨이 REST 주소. 안드로이드 `API_BASE_URL` 과 같은 값 |
| `WS_BASE_URL` | 게이트웨이 웹소켓 주소. 뒤에 `ws-service/modu-chat` 이 붙습니다 |
| `GOOGLE_IOS_CLIENT_ID` | Google Cloud 콘솔에서 만든 **iOS 용** OAuth 클라이언트 ID (번들 ID `com.example.modumessenger`) |
| `GOOGLE_IOS_REVERSED_CLIENT_ID` | 위 값을 점(.) 기준으로 뒤집은 URL 스킴 |
| `GOOGLE_SERVER_CLIENT_ID` | 백엔드가 ID 토큰 audience 로 검사하는 웹 클라이언트 ID. 안드로이드와 같습니다 |

### 비밀값 (`Config/Secrets.xcconfig`, 깃 제외)

Firebase 콘솔에서 프로젝트 `moduchat-346913` 에 iOS 앱(번들 ID `com.example.modumessenger`)을 추가하고
받은 `GoogleService-Info.plist` 의 `CLIENT_ID`, `REVERSED_CLIENT_ID` 를 아래 파일에 적습니다.
`AppConfig.xcconfig` 맨 아래 `#include? "Secrets.xcconfig"` 가 이 값을 덮어씁니다. 플리스트 자체는 프로젝트에 넣지 않습니다.

```
// ios/ModuMessenger/Config/Secrets.xcconfig
GOOGLE_IOS_CLIENT_ID = <CLIENT_ID>
GOOGLE_IOS_REVERSED_CLIENT_ID = <REVERSED_CLIENT_ID>
```

이 파일이 없으면 로그인 버튼을 눌러도 설정 안내만 나옵니다.
`GIDConfiguration(clientID:serverClientID:)` 로 로그인하므로 발급되는 ID 토큰의 audience 가 웹 클라이언트 ID 가 되고,
member-service 의 `GoogleIdTokenValidator` 를 그대로 통과합니다.

개발 게이트웨이가 http 라 `Info.plist` 에 `NSAllowsArbitraryLoads` 가 켜져 있습니다. 배포 시 https 로 바꾸고 지우세요.

## 테스트

```bash
cd ios/ModuMessenger
xcodebuild -project ModuMessenger.xcodeproj -scheme ModuMessenger \
  -destination 'platform=iOS Simulator,name=iPhone 16' CODE_SIGNING_ALLOWED=NO test
```

네트워크 없이 도는 유닛 테스트입니다: DTO 디코딩, 소켓 프레임 파싱, 엔드포인트 경로, 401 재발급, 재연결 백오프, 채팅 저장소(낙관적 에코·읽음·갭 복구).

## 구조

```
ModuMessenger/
  App/          앱 진입, 전역 객체 조립(AppEnvironment), 루트 화면, Google 로그인 래퍼
  Config/       xcconfig → Info.plist 로 들어온 설정 읽기
  Models/       백엔드 DTO (안드로이드 dto 패키지와 1:1), 시각 포맷
  Network/      Endpoint(경로 정의), APIClient(인증 헤더·401 재발급), Keychain 토큰 보관, 서비스 어댑터
  Socket/       URLSessionWebSocketTask 연결 관리, 프레임 파싱, 재연결 정책
  Repository/   SessionStore(로그인 상태), ChatRepository(방·채팅·안읽음·읽음 커서), FriendsStore
  Features/     화면: 로그인, 탭(친구/채팅/설정), 친구 찾기, 채팅방 만들기, 채팅방, 프로필 수정, 앱 정보
  Components/   인증 헤더가 필요한 이미지 로더, 아바타, 브랜드 색
```

### 안드로이드와 맞춘 것

- REST 경로와 요청/응답 모양은 안드로이드 Retrofit 인터페이스와 글자 단위로 같습니다 (`EndpointTests` 가 지킵니다).
- 로그인 응답 헤더의 토큰은 `"Bearer "` 를 붙여 보관하고 `Authorization` 헤더로 보냅니다.
- 소켓 핸드셰이크 헤더 `userId`, `Authorization`, 20초 ping, 1초→30초 지수 백오프(±20% jitter).
- 보내는 프레임: `ChatDto` JSON, 읽음은 `{"type":"READ","roomId","sender"}`.
  받는 프레임: `ChatDto` / `READ` / `ROOM_CREATED` (방 목록 재조회).
- 낙관적 에코(임시 음수 id) → 브로드캐스트가 돌아오면 교체. 실패한 말풍선은 재전송 버튼.
- 채팅 시각은 `yyyy-MM-dd HH:mm:ss` 문자열 그대로 주고받습니다.

### 아직 없는 것

- 푸시 알림(FCM). 안드로이드는 방 토픽을 구독하지만 iOS 는 APNs 연동이 필요해 뒤로 미뤘습니다.
- 프로필 이력, 채팅방 이름/이미지 수정, 채팅 검색, 사진 모아보기.
- 로컬 DB 캐시(안드로이드 Room). 지금은 메모리에만 두고 화면 진입 시 다시 받습니다.
