package com.jakumjirak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.tree.DefaultMutableTreeNode

class AssertionAndCodegenTest {

    private val response = ApiResponse(201, emptyMap(), """{"id": 5, "tags": ["a"]}""", 3)

    @Test
    fun `assertions evaluate status, body and json path`() {
        val results = AssertionEngine().verify(
            response,
            listOf(
                ApiFirstSettings.Assertion("Status Code", expectedValue = "201"),
                ApiFirstSettings.Assertion("Status Code", expectedValue = "200"),
                ApiFirstSettings.Assertion("Body Contains", expectedValue = "\"tags\""),
                ApiFirstSettings.Assertion("JSON Match", expectedValue = "$.id=5"),
                ApiFirstSettings.Assertion("JSON Match", expectedValue = "$.missing=1"),
                ApiFirstSettings.Assertion("Nope", expectedValue = "x"),
            )
        )
        assertEquals(listOf(true, false, true, true, false, false), results.map { it.success })
        assertEquals("201", results[0].actualValue)
        assertTrue(results[4].actualValue.startsWith("Error"))
    }

    private val request = ApiFirstSettings.SavedRequest(
        method = "post",
        url = "{{base}}/it's",
        headers = mutableMapOf("X-Q" to "say \"hi\""),
        bodyType = "Raw",
        body = "{\"n\": \"{{name}}\"}",
        authType = "Bearer Token",
        authParams = mutableMapOf("token" to "T"),
    )
    private val vars = mapOf("base" to "https://h", "name" to "O'Neil")

    @Test
    fun `curl snippet shell-quotes every value`() {
        val curl = CodeGenerator().generateCurl(request, vars)
        assertTrue(curl.startsWith("curl --location --request POST 'https://h/it'\\''s'"))
        assertTrue(curl.contains("--header 'X-Q: say \"hi\"'"))
        assertTrue(curl.contains("--header 'Authorization: Bearer T'"))
        assertTrue(curl.contains("--data-raw '{\"n\": \"O'\\''Neil\"}'"))
        // round-trips through our own shell tokenizer
        val back = CurlParser().parse(curl)!!
        assertEquals("https://h/it's", back.url)
        assertEquals("{\"n\": \"O'Neil\"}", back.body)
    }

    @Test
    fun `python and js snippets use escaped literals`() {
        val py = CodeGenerator().generatePython(request, vars)
        assertTrue(py.contains("url = \"https://h/it's\""))
        assertTrue(py.contains("\"X-Q\": \"say \\\"hi\\\"\""))
        assertTrue(py.contains("payload = \"{\\\"n\\\": \\\"O'Neil\\\"}\""))
        assertTrue(py.contains("requests.request(\"POST\""))

        val js = CodeGenerator().generateJsFetch(request, vars)
        assertTrue(js.contains("myHeaders.append(\"Authorization\", \"Bearer T\");"))
        assertTrue(js.contains("body: \"{\\\"n\\\": \\\"O'Neil\\\"}\","))
        assertTrue(js.contains("fetch(\"https://h/it's\", requestOptions)"))
    }

    @Test
    fun `graphql snippets carry a json body and content type, invalid variables degrade to empty`() {
        val gql = ApiFirstSettings.SavedRequest(
            method = "POST", url = "https://h/graphql", bodyType = "GraphQL",
            graphqlQuery = "{ me { id } }", graphqlVariables = "{broken",
        )
        val curl = CodeGenerator().generateCurl(gql, emptyMap())
        assertTrue(curl.contains("--header 'Content-Type: application/json'"))
        assertTrue(curl.contains("""--data-raw '{"query":"{ me { id } }","variables":{}}'"""))
        assertFalse(CodeGenerator().generatePython(gql.copy(bodyType = "None"), emptyMap()).contains("Content-Type"))
    }

    @Test
    fun `json tree builder nests objects and arrays`() {
        val root = JsonTreeModelBuilder().build("""{"a": {"b": [1, null]}, "c": "x"}""").root as DefaultMutableTreeNode
        assertEquals("Response", root.userObject)
        assertEquals(2, root.childCount)
        val a = root.getChildAt(0) as DefaultMutableTreeNode
        assertEquals("a", a.userObject)
        val b = a.getChildAt(0) as DefaultMutableTreeNode
        assertEquals("b []", b.userObject)
        assertEquals("[0]: 1", (b.getChildAt(0) as DefaultMutableTreeNode).userObject)
        assertEquals("c: \"x\"", (root.getChildAt(1) as DefaultMutableTreeNode).userObject)
    }

    @Test
    fun `json tree builder reports invalid json`() {
        val root = JsonTreeModelBuilder().build("{oops").root as DefaultMutableTreeNode
        assertTrue((root.getChildAt(0) as DefaultMutableTreeNode).userObject.toString().startsWith("Error parsing JSON"))
    }
}
