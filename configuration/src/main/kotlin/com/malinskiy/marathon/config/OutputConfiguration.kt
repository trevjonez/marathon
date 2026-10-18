package com.malinskiy.marathon.config

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

// Value 0 is equivalent to unlimited path length
const val OUTPUT_MAX_PATH = 0
// Value 0 is equivalent to unlimited filename length
const val OUTPUT_MAX_FILENAME = 255

data class OutputConfiguration(
    @JsonProperty("maxPath") val maxPath: Int = OUTPUT_MAX_PATH,
    @JsonProperty("maxFilename") val maxFilename: Int = OUTPUT_MAX_FILENAME,
    /**
     * Turns test annotations into report tags. Each annotation is matched against the rules in order and tagged by the
     * first one whose [TagRule.regex] matches its fully qualified name; unmatched annotations aren't shown.
     */
    @JsonProperty("tags") val tags: List<TagRule> = emptyList(),
)

/**
 * Tags every annotation whose fully qualified name fully matches [regex] with [group]. The tag value is the regex's first
 * capture group when it has one and it matched something, otherwise the annotation's simple name.
 */
data class TagRule(
    @JsonProperty("group") val group: String,
    @JsonProperty("regex") val regex: String,
) {
    // Compiled eagerly so a bad pattern fails at configuration load rather than at report time
    @get:JsonIgnore
    val pattern: Regex = Regex(regex)

    fun valueFor(annotationName: String): String? {
        val match = pattern.matchEntire(annotationName) ?: return null
        return match.groups.drop(1).firstOrNull()?.value?.takeIf { it.isNotEmpty() }
            ?: annotationName.substringAfterLast('.')
    }
}
