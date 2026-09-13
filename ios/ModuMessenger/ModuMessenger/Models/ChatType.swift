import Foundation

/// 안드로이드 `dto/ChatType` 의 정수 상수와 같은 값이다. 서버는 이 숫자를 그대로 저장한다.
enum ChatType: Int, Codable {
    case invalid = 0
    case text = 1
    case image = 2
    case file = 3
    case audio = 4
}
