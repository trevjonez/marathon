package com.malinskiy.marathon.report

import com.google.gson.annotations.SerializedName

data class HtmlPoolSummary(
    @SerializedName("id") val id: String,
    @SerializedName("tests") val tests: List<HtmlShortTest>,
    @SerializedName("passed_count") val passedCount: Int,
    @SerializedName("failed_count") val failedCount: Int,
    @SerializedName("ignored_count") val ignoredCount: Int,
    @SerializedName("flaky_count") val flakyCount: Int,
    @SerializedName("duration_millis") val durationMillis: Long,
    /** Earliest [HtmlAttempt.startTimeMs] across every test in the pool, or 0 if none had timing. */
    @SerializedName("start_time_ms") val startTimeMs: Long,
    /** Latest [HtmlAttempt.endTimeMs] across every test in the pool, or 0 if none had timing. */
    @SerializedName("end_time_ms") val endTimeMs: Long,
    /** `end_time_ms - start_time_ms` — real elapsed time, as opposed to [durationMillis] which sums every test serially. */
    @SerializedName("wallclock_duration_millis") val wallclockDurationMillis: Long,
    /** Sum of [HtmlAttempt.durationMillis] for every non-final (retried-away) attempt in the pool. */
    @SerializedName("flake_overhead_millis") val flakeOverheadMillis: Long,
    @SerializedName("devices") val devices: List<HtmlDevice>,
)
