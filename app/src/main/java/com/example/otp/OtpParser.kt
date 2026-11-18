package com.example.otp

data class ExtractedOtp(
    val code: String,
    val serviceName: String,
    val snippet: String
)

object OtpParser {
    private val contextKeywords = listOf(
        "otp", "one-time", "one time", "verification", "verify", "code", "passcode",
        "pin", "2fa", "two-factor", "login", "sign in", "sign-in", "security",
        "confirm", "auth", "token", "do not share", "never share", "expires", "valid for"
    )

    // Matches G-123456, 123456, 123-456, 123 456, or 4-8 digit codes bounded by non-digits
    private val googlePrefixPattern = Regex("""\bG-(\d{6})\b""")
    private val groupedDigitsPattern = Regex("""(?<!\d)(\d{3})[-\s](\d{3})(?!\d)""")
    private val plainDigitsPattern = Regex("""(?<![\d.$:/\-+])(\d{4,8})(?![\d.$:/\-])""")
    private val alphaNumOtpPattern = Regex("""(?i)\b(?:code|otp|pin|passcode)\s*(?:is|:|-)?\s*([A-Z0-9]{4,8})\b""")

    private val leadingBrandPattern = Regex("""^([A-Za-z0-9 &._'-]{2,26})\s*[:\-–|]\s*""")
    private val serviceCuePattern = Regex("""(?i)\b(?:for|from|at|to)\s+([A-Z][A-Za-z0-9&.'-]{1,22}(?:\s+[A-Z][A-Za-z0-9&.'-]{1,16})?)\b""")

    fun extractOtp(rawText: String, fallbackSender: String = "SMS Alert"): ExtractedOtp? {
        val normalized = rawText.trim().replace(Regex("""\s+"""), " ")
        if (normalized.isBlank()) return null

        val lower = normalized.lowercase()
        val hasContext = contextKeywords.any { lower.contains(it) }

        // If the user pasted just a standalone 4-8 digit code, allow it cleanly
        val pureCodeOnly = normalized.matches(Regex("""^(?:G-)?\d{4,8}$"""))
        if (!hasContext && !pureCodeOnly) {
            return null
        }

        val extractedCode = findBestCode(normalized) ?: return null
        val serviceName = detectServiceName(normalized, fallbackSender)
        val snippet = if (normalized.length > 160) normalized.take(157) + "..." else normalized

        return ExtractedOtp(
            code = extractedCode,
            serviceName = serviceName,
            snippet = snippet
        )
    }

    private fun findBestCode(text: String): String? {
        googlePrefixPattern.find(text)?.let { return "G-${it.groupValues[1]}" }

        groupedDigitsPattern.find(text)?.let { match ->
            val combined = match.groupValues[1] + match.groupValues[2]
            if (!isLikelyYearOrPhone(combined, text, match.range)) {
                return combined
            }
        }

        val candidates = plainDigitsPattern.findAll(text)
            .map { it to it.groupValues[1] }
            .filter { (match, digits) -> !isLikelyYearOrPhone(digits, text, match.range) }
            .toList()

        if (candidates.isNotEmpty()) {
            // Prefer 6-digit codes first, then 5/4/7/8
            val preferred = candidates.sortedBy { (_, digits) ->
                when (digits.length) {
                    6 -> 0
                    4 -> 1
                    5 -> 2
                    8 -> 3
                    else -> 4
                }
            }
            return preferred.first().second
        }

        alphaNumOtpPattern.find(text)?.let { match ->
            val token = match.groupValues[1]
            if (token.any { it.isDigit() }) {
                return token.uppercase()
            }
        }

        return null
    }

    private fun isLikelyYearOrPhone(digits: String, fullText: String, range: IntRange): Boolean {
        if (digits.length == 4) {
            val year = digits.toIntOrNull()
            if (year != null && year in 1990..2035) {
                val before = fullText.substring(maxOf(0, range.first - 14), range.first).lowercase()
                if (!before.contains("code") && !before.contains("pin") && !before.contains("otp")) {
                    return true
                }
            }
        }
        // Reject if preceded by $ / € / £ or followed by % / am / pm
        val prevChar = fullText.getOrNull(range.first - 1)
        if (prevChar == '$' || prevChar == '€' || prevChar == '£' || prevChar == '+') return true
        val after = fullText.substring(range.last + 1, minOf(fullText.length, range.last + 4)).lowercase()
        if (after.startsWith("%") || after.startsWith("am") || after.startsWith("pm")) return true
        return false
    }

    private fun detectServiceName(text: String, fallbackSender: String): String {
        leadingBrandPattern.find(text)?.let { match ->
            val brand = match.groupValues[1].trim()
            if (brand.isNotEmpty() && !brand.lowercase().startsWith("your ")) {
                return brand
            }
        }
        serviceCuePattern.find(text)?.let { match ->
            val candidate = match.groupValues[1].trim()
            if (candidate.lowercase() !in setOf("your", "this", "the", "account", "login", "sign")) {
                return candidate
            }
        }
        return fallbackSender.ifBlank { "2FA Verification" }
    }
}
