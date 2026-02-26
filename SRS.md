# SRS - API First & Mocking Studio

## 2.1 Functional Requirements
* **REQ-1: OpenAPI/Swagger Parser:** Support for OpenAPI 3.0/3.1 and Swagger 2.0 (JSON/YAML).
* **REQ-2: API Explorer (GUI):** Tree structure of endpoints, details on parameters and responses.
* **REQ-3: HTTP Client Generator:** Generate `.http` files compatible with native JetBrains HTTP Client.
* **REQ-4: Embedded Mock Server:** Local Ktor-based server for instant endpoint mocking.
* **REQ-5: Dynamic Mocking:** Automatic data generation based on OpenAPI examples or types.
* **REQ-6: Environment Management:** Integration with `http-client.env.json` and secure credential storage.

## 2.2 Non-Functional Requirements
* **Performance:** Parsing 10MB OpenAPI files under 2 seconds (background process).
* **Native Integration:** Pure Kotlin UI DSL / Swing components for native look and feel.
* **Security:** Sensitive tokens stored via JetBrains Credential Store API.
