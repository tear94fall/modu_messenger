import XCTest
@testable import ModuMessenger

final class ReconnectPolicyTests: XCTestCase {
    func testDoublesFromOneSecondAndCapsAtThirty() {
        var policy = ReconnectPolicy(jitter: { 0.5 })
        XCTAssertEqual(policy.nextDelay(), 1)
        XCTAssertEqual(policy.nextDelay(), 2)
        XCTAssertEqual(policy.nextDelay(), 4)
        XCTAssertEqual(policy.nextDelay(), 8)
        XCTAssertEqual(policy.nextDelay(), 16)
        XCTAssertEqual(policy.nextDelay(), 30)
        XCTAssertEqual(policy.nextDelay(), 30)
    }

    func testResetStartsOver() {
        var policy = ReconnectPolicy(jitter: { 0.5 })
        _ = policy.nextDelay(); _ = policy.nextDelay()
        policy.reset()
        XCTAssertEqual(policy.nextDelay(), 1)
    }

    func testJitterStaysWithinTwentyPercent() {
        var low = ReconnectPolicy(jitter: { 0.0 })
        var high = ReconnectPolicy(jitter: { 1.0 - .ulpOfOne })
        XCTAssertEqual(low.nextDelay(), 0.8, accuracy: 0.001)
        XCTAssertEqual(high.nextDelay(), 1.2, accuracy: 0.001)
    }
}
