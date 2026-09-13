import Foundation

/// 서버 시각 문자열 "yyyy-MM-dd HH:mm:ss" 변환. 안드로이드 ChatViewModel.CHAT_TIME_FORMAT 과 같다.
enum ChatTime {
    private static let server: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = .current
        f.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return f
    }()

    private static let shortTime: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateStyle = .none
        f.timeStyle = .short
        return f
    }()

    private static let dayHeader: DateFormatter = {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "yyyy년 M월 d일 EEEE"
        return f
    }()

    static func parse(_ text: String?) -> Date? {
        guard let text, !text.isEmpty else { return nil }
        return server.date(from: text)
    }

    static func serverString(_ date: Date) -> String { server.string(from: date) }

    static func now() -> String { serverString(Date()) }

    /// "오후 1:04" 형태. 파싱 실패면 빈 문자열.
    static func short(_ text: String?) -> String {
        guard let date = parse(text) else { return "" }
        return shortTime.string(from: date)
    }

    /// 채팅방 목록용. 오늘이면 시각, 아니면 날짜.
    static func listLabel(_ text: String?) -> String {
        guard let date = parse(text) else { return "" }
        if Calendar.current.isDateInToday(date) { return shortTime.string(from: date) }
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = Calendar.current.isDate(date, equalTo: Date(), toGranularity: .year) ? "M월 d일" : "yyyy.MM.dd"
        return f.string(from: date)
    }

    static func dayLabel(_ text: String?) -> String {
        guard let date = parse(text) else { return "" }
        return dayHeader.string(from: date)
    }

    static func sameMinute(_ a: String?, _ b: String?) -> Bool {
        guard let da = parse(a), let db = parse(b) else { return false }
        return Calendar.current.isDate(da, equalTo: db, toGranularity: .minute)
    }

    static func sameDay(_ a: String?, _ b: String?) -> Bool {
        guard let da = parse(a), let db = parse(b) else { return false }
        return Calendar.current.isDate(da, inSameDayAs: db)
    }
}
