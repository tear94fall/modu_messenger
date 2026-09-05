import Foundation

/// 재연결 지연 계산기. 1초부터 2배씩 늘어 30초에서 멈춘다. 서버 재시작 시 몰림을 막는 ±20% jitter.
struct ReconnectPolicy {
    static let initialDelay: TimeInterval = 1
    static let maxDelay: TimeInterval = 30
    private static let jitterRatio = 0.2

    private var attempt = 0
    private let jitter: () -> Double

    /// `jitter` 는 [0,1) 난수. 테스트에서 고정값을 넣는다.
    init(jitter: @escaping () -> Double = { Double.random(in: 0..<1) }) {
        self.jitter = jitter
    }

    mutating func nextDelay() -> TimeInterval {
        var base = Self.initialDelay
        for _ in 0..<attempt where base < Self.maxDelay { base *= 2 }
        base = min(base, Self.maxDelay)
        attempt += 1
        let factor = (1 - Self.jitterRatio) + 2 * Self.jitterRatio * jitter()
        return base * factor
    }

    mutating func reset() { attempt = 0 }
}
