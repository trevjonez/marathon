package com.malinskiy.marathon.report

import com.google.gson.annotations.SerializedName

/**
 * A test annotation surfaced as a report tag, see `OutputConfiguration.tags`. [attributes] holds the annotation's
 * non-blank values; it's only populated on the per-test page and left null (so gson omits it) in pool listings.
 */
data class HtmlTag(
    @SerializedName("group") val group: String,
    @SerializedName("value") val value: String,
    @SerializedName("attributes") val attributes: Map<String, String>? = null,
)
