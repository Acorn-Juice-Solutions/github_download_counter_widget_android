package com.acornjuice.downloadwidget.util

import android.util.Log

/**
 * Thin wrapper around [android.util.Log] that redacts common credential patterns before
 * a message is emitted. Prevents accidental leaks of GitHub PATs / Authorization headers
 * in stack traces or debug logs.
 *
 * The redactor is a pure function ([redact]) so it can be unit-tested without an Android
 * runtime.
 */
object SafeLogger {

    private const val MASK = "***"

    // Order matters: put the most specific pattern first so its group boundaries are respected.
    private val PATTERNS: List<Pair<Regex, String>> = listOf(
        // "Authorization: Bearer <token>" (HTTP header form)
        Regex("""(?i)(authorization\s*:\s*bearer\s+)\S+""") to "$1$MASK",
        // Standalone "Bearer <token>"
        Regex("""(?i)(bearer\s+)[A-Za-z0-9._~+/=-]+""") to "$1$MASK",
        // key=value or key: value shapes with a "token"-like key
        Regex("""(?i)(?<key>(?:github[_-]?)?(?:access[_-]?)?token|api[_-]?key|pat)(?<sep>\s*[=:]\s*)(?<val>[A-Za-z0-9._~+/=-]+)""")
            to "\${key}\${sep}$MASK",
    )

    /**
     * Returns [input] with any recognized credential patterns replaced by `***`.
     * Safe to call on any string; unmatched inputs are returned unchanged.
     */
    fun redact(input: String): String {
        var result = input
        for ((pattern, replacement) in PATTERNS) {
            result = pattern.replace(result, replacement)
        }
        return result
    }

    fun d(tag: String, message: String) {
        Log.d(tag, redact(message))
    }

    fun i(tag: String, message: String) {
        Log.i(tag, redact(message))
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) Log.w(tag, redact(message)) else Log.w(tag, redact(message), throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) Log.e(tag, redact(message)) else Log.e(tag, redact(message), throwable)
    }
}
