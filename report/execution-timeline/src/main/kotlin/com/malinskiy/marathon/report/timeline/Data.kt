package com.malinskiy.marathon.report.timeline

data class Data(
    val testName: String,
    val metricType: MetricType,
    val startDate: Long,
    val endDate: Long,
    val expectedValue: Double,
    val variance: Double,
    /** Test batch that produced this bar; null for non-test bars (device init / prepare). */
    val batchId: String? = null,
    /** 0-based attempt index within the logical test; null for non-test bars. */
    val attemptIndex: Int? = null,
    /**
     * Pool that owns this test. Populated only for test bars. Combined with
     * [testFilename] + measure device serial, the UI can construct the same
     * relative href the pool list uses to jump to the test detail page.
     */
    val poolId: String? = null,
    /**
     * File name of the emitted test HTML (e.g. `Foo.method.html`) — mirrors
     * what `HtmlSummaryReporter` writes for the pool row. Null on non-test bars.
     */
    val testFilename: String? = null,
    /** Serialized safe device id (escape()'d) so the URL construction is a pure join. */
    val deviceSerial: String? = null,
    /** Test parsing bars: href of the attempt's log page, relative to the report root. */
    val logHref: String? = null,
    /** Test parsing and device phase bars: the step failed. */
    val failed: Boolean? = null,
    /** Device phase bars: the DevicePhase name, e.g. INSTALL or CLEAR_DATA. [testName] holds the step's label. */
    val phase: String? = null,
    /** Device phase bars: the step repeats around every batch rather than happening once at preparation. */
    val betweenTests: Boolean? = null,
)
