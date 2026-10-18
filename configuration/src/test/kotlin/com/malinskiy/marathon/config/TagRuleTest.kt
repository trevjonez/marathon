package com.malinskiy.marathon.config

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.regex.PatternSyntaxException

class TagRuleTest {
    @Test
    fun `without a capture group the value is the simple name`() {
        TagRule("team", "com\\.aetna\\.teams\\..*").valueFor("com.aetna.teams.Redwood") shouldBeEqualTo "Redwood"
    }

    @Test
    fun `the first capture group becomes the value`() {
        TagRule("env", "com\\.aetna\\.test\\.(Mock|QA\\d?)Test").valueFor("com.aetna.test.QA2Test") shouldBeEqualTo "QA2"
    }

    @Test
    fun `an empty or unmatched capture group falls back to the simple name`() {
        TagRule("x", "a\\.(b?)C").valueFor("a.C") shouldBeEqualTo "C"
        TagRule("x", "a\\.(b)?C").valueFor("a.C") shouldBeEqualTo "C"
    }

    @Test
    fun `the whole name has to match`() {
        val rule = TagRule("team", "com\\.aetna\\.teams\\.Red")
        rule.valueFor("com.aetna.teams.Redwood") shouldBeEqualTo null
        rule.valueFor("xcom.aetna.teams.Red") shouldBeEqualTo null
    }

    @Test
    fun `a name without a package is its own simple name`() {
        TagRule("x", ".*").valueFor("Flaky") shouldBeEqualTo "Flaky"
    }

    @Test
    fun `an invalid pattern fails on construction`() {
        assertThrows<PatternSyntaxException> { TagRule("x", "(unclosed") }
    }
}
