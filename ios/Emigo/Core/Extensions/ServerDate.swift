import Foundation

/// Reads and writes the ISO-8601 instants the backend uses, e.g. `2026-10-06T02:40:11.605123Z`.
/// The server can send anywhere from zero to nine fractional digits, and `ISO8601DateFormatter`
/// only copes with exactly three, so the fraction is normalised before parsing.
enum ServerDate {
    private static let fractionalFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }()

    private static let plainFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()

    static func parse(_ value: String) -> Date? {
        let normalized = normalizingFraction(in: value)
        return fractionalFormatter.date(from: normalized) ?? plainFormatter.date(from: normalized)
    }

    /// Whole-second instant, the form the backend's `start`/`end` query parameters expect.
    static func string(from date: Date) -> String {
        plainFormatter.string(from: date)
    }

    private static func normalizingFraction(in value: String) -> String {
        guard let dot = value.firstIndex(of: ".") else { return value }
        let digitsStart = value.index(after: dot)
        var digitsEnd = digitsStart
        while digitsEnd < value.endIndex, value[digitsEnd].isASCII, value[digitsEnd].isNumber {
            digitsEnd = value.index(after: digitsEnd)
        }
        let digits = value[digitsStart..<digitsEnd]
        guard !digits.isEmpty else { return value }
        let threeDigits = String((digits + "000").prefix(3))
        return String(value[..<digitsStart]) + threeDigits + String(value[digitsEnd...])
    }
}

extension JSONDecoder {
    /// The decoder every API response goes through.
    static let emigo: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let text = try container.decode(String.self)
            guard let date = ServerDate.parse(text) else {
                throw DecodingError.dataCorruptedError(in: container, debugDescription: "Not an ISO-8601 date: \(text)")
            }
            return date
        }
        return decoder
    }()
}

extension JSONEncoder {
    /// The encoder every API request body goes through.
    static let emigo: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .custom { date, encoder in
            var container = encoder.singleValueContainer()
            try container.encode(ServerDate.string(from: date))
        }
        return encoder
    }()
}
