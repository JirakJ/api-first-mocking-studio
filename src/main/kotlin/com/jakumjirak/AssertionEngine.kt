package com.jakumjirak

import com.jayway.jsonpath.JsonPath

data class AssertionResult(
    val assertion: ApiFirstSettings.Assertion,
    val success: Boolean,
    val actualValue: String
)

class AssertionEngine {

    fun verify(response: ApiResponse, assertions: List<ApiFirstSettings.Assertion>): List<AssertionResult> {
        return assertions.map { assertion ->
            when (assertion.type) {
                "Status Code" -> {
                    val actual = response.statusCode.toString()
                    AssertionResult(assertion, actual == assertion.expectedValue, actual)
                }
                "Body Contains" -> {
                    AssertionResult(assertion, response.body.contains(assertion.expectedValue), "Body excerpt: ${response.body.take(20)}...")
                }
                "JSON Match" -> {
                    // expectedValue format: path=value (e.g. $.id=1)
                    try {
                        val path = assertion.expectedValue.substringBefore("=")
                        val expected = assertion.expectedValue.substringAfter("=")
                        val actual: Any = JsonPath.read(response.body, path)
                        AssertionResult(assertion, actual.toString() == expected, actual.toString())
                    } catch (e: Exception) {
                        AssertionResult(assertion, false, "Error: ${e.message}")
                    }
                }
                else -> AssertionResult(assertion, false, "Unknown type")
            }
        }
    }
}
