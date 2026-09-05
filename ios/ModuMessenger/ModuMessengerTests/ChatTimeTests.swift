import XCTest
@testable import ModuMessenger

final class ChatTimeTests: XCTestCase {
    func testParsesServerFormat() {
        let date = ChatTime.parse("2026-09-05 13:04:09")
        XCTAssertNotNil(date)
        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: date!)
        XCTAssertEqual(comps.hour, 13)
        XCTAssertEqual(comps.minute, 4)
    }

    func testFormatsBackToServerFormat() {
        let date = ChatTime.parse("2026-09-05 13:04:09")!
        XCTAssertEqual(ChatTime.serverString(date), "2026-09-05 13:04:09")
    }

    func testShortTimeForGarbageIsEmpty() {
        XCTAssertEqual(ChatTime.short(""), "")
        XCTAssertEqual(ChatTime.short("nope"), "")
    }

    func testSameMinuteGrouping() {
        XCTAssertTrue(ChatTime.sameMinute("2026-09-05 13:04:09", "2026-09-05 13:04:59"))
        XCTAssertFalse(ChatTime.sameMinute("2026-09-05 13:04:09", "2026-09-05 13:05:00"))
    }
}
