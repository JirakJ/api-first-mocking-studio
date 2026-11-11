package com.jakumjirak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CurlParserTest {

    private val parser = CurlParser()

    @Test
    fun `rejects non-curl input`() {
        assertNull(parser.parse("wget https://example.com"))
        assertNull(parser.parse(""))
    }

    @Test
    fun `parses method, url, quoted headers with spaces and json body`() {
        val cmd = """curl -X PUT 'https://api.example.com/users/1' -H 'Content-Type: application/json' """ +
            """-H "Authorization: Bearer abc def" --data-raw '{"name": "Jane Doe"}'"""
        val req = parser.parse(cmd)!!
        assertEquals("PUT", req.method)
        assertEquals("https://api.example.com/users/1", req.url)
        assertEquals(mapOf("Content-Type" to "application/json", "Authorization" to "Bearer abc def"), req.headers)
        assertEquals("""{"name": "Jane Doe"}""", req.body)
        assertEquals("Raw", req.bodyType)
    }

    @Test
    fun `data implies POST and no data means GET with no body`() {
        assertEquals("POST", parser.parse("curl https://x.io -d a=1")!!.method)
        val get = parser.parse("curl https://x.io")!!
        assertEquals("GET", get.method)
        assertEquals("None", get.bodyType)
        assertNull(get.body)
    }

    @Test
    fun `handles line continuations, attached -X and --url`() {
        val cmd = "curl -XDELETE \\\n  --url https://x.io/items/9 \\\n  -H 'Accept: */*'"
        val req = parser.parse(cmd)!!
        assertEquals("DELETE", req.method)
        assertEquals("https://x.io/items/9", req.url)
        assertEquals("*/*", req.headers["Accept"])
    }

    @Test
    fun `basic auth from -u`() {
        val req = parser.parse("curl -u alice:s3cr:et https://x.io")!!
        assertEquals("Basic Auth", req.authType)
        assertEquals("alice", req.authParams["username"])
        assertEquals("s3cr:et", req.authParams["password"])
    }

    @Test
    fun `multiple data flags are joined and options with values are skipped`() {
        val req = parser.parse("curl -o out.txt -A agent https://x.io -d a=1 -d b=2 --compressed")!!
        assertEquals("https://x.io", req.url)
        assertEquals("a=1&b=2", req.body)
    }

    @Test
    fun `head flag sets HEAD method`() {
        assertEquals("HEAD", parser.parse("curl -I https://x.io")!!.method)
    }

    @Test
    fun `tokenizer honours quotes and escapes`() {
        assertEquals(
            listOf("curl", "a b", "c\"d", "e f", "it's"),
            CurlParser.tokenize("""curl 'a b' "c\"d" e\ f "it's"""")
        )
    }
}
