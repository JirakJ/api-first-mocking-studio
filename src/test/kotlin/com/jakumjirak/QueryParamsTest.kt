package com.jakumjirak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class QueryParamsTest {

    @Test
    fun `parse decodes pairs and handles keys without values`() {
        assertEquals(
            listOf("q" to "a b", "flag" to "", "x" to "1=2"),
            QueryParams.parse("https://h/p?q=a+b&flag&x=1%3D2#frag")
        )
    }

    @Test
    fun `parse returns null without query and tolerates malformed escapes`() {
        assertNull(QueryParams.parse("https://h/p"))
        assertEquals(listOf("bad" to "%zz"), QueryParams.parse("https://h/p?bad=%zz"))
        assertEquals(emptyList<Pair<String, String>>(), QueryParams.parse("https://h/p?"))
    }

    @Test
    fun `build replaces the query, skips blank keys and keeps variables readable`() {
        assertEquals(
            "https://h/p?q=a+b&id={{id}}",
            QueryParams.build("https://h/p?old=1", listOf("q" to "a b", "" to "x", "id" to "{{id}}"))
        )
        assertEquals("https://h/p", QueryParams.build("https://h/p?old=1", emptyList()))
    }
}
