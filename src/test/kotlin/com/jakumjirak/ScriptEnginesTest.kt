package com.jakumjirak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScriptEnginesTest {

    @Test
    fun `response script extracts json paths`() {
        val body = """{"token": "secret_abc_123", "user": {"id": 1}}"""
        val extracted = ResponseScriptEngine.extract("auth_token=$.token\nuser_id=$.user.id", body)
        assertEquals(mapOf("auth_token" to "secret_abc_123", "user_id" to "1"), extracted)
    }

    @Test
    fun `response script ignores comments, blank names, missing paths and invalid json`() {
        val body = """{"a": 1}"""
        val script = "// x=$.a\n=$.a\nmissing=$.nope\nbad=$..[\nok = $.a "
        assertEquals(mapOf("ok" to "1"), ResponseScriptEngine.extract(script, body))
        assertTrue(ResponseScriptEngine.extract("a=$.a", "not json").isEmpty())
        assertTrue(ResponseScriptEngine.extract(null, body).isEmpty())
    }

    @Test
    fun `pre-request script resolves variables and dynamic values`() {
        val assigned = PreRequestScriptEngine.evaluate(
            "ts={{\$timestamp}}\nid={{\$guid}}\nurl={{base}}/x\nchained={{url}}?t={{ts}}\n// skip=1",
            mapOf("base" to "http://h"),
            now = { 1000L },
            guid = { "G-1" },
        )
        assertEquals(
            mapOf("ts" to "1000", "id" to "G-1", "url" to "http://h/x", "chained" to "http://h/x?t=1000"),
            assigned
        )
    }

    @Test
    fun `pre-request script with nothing to do`() {
        assertTrue(PreRequestScriptEngine.evaluate("  ", emptyMap()).isEmpty())
        assertTrue(PreRequestScriptEngine.evaluate("no assignment here", emptyMap()).isEmpty())
    }
}
