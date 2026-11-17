package com.github.fakegps

/**
 * Numeric release version such as `v2.2.2`, compared as major.minor.patch.
 * Pre-release suffixes (`-beta.1`, `+build`) are ignored for comparison.
 */
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<Version> {

    override fun compareTo(other: Version): Int {
        major.compareTo(other.major).let { if (it != 0) return it }
        minor.compareTo(other.minor).let { if (it != 0) return it }
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /** Returns null for blank, non-numeric or negative parts. */
        @JvmStatic
        fun parse(raw: String?): Version? {
            if (raw.isNullOrBlank()) {
                return null
            }
            val core = raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore('-')
                .substringBefore('+')
            val parts = core.split('.').map { it.trim() }
            if (parts.isEmpty() || parts.size > 3) {
                return null
            }
            val numbers = parts.map { it.toIntOrNull() ?: return null }
            if (numbers.any { it < 0 }) {
                return null
            }
            return Version(
                numbers.getOrElse(0) { 0 },
                numbers.getOrElse(1) { 0 },
                numbers.getOrElse(2) { 0 }
            )
        }
    }
}
