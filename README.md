# API First & Mocking Studio

[![Version](https://img.shields.io/badge/version-2026.9.0-blue)](https://plugins.jetbrains.com/plugin/30376)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

Native JetBrains IDE plugin for API development, testing, and mocking.

## Features
- **API Explorer tool window** — compose requests (method, URL, query params, headers, Raw / form-urlencoded / multipart / GraphQL bodies), Bearer / Basic / API-key / OAuth 2.0 auth, request history, collections and a JSON tree view of responses.
- **Environments & variables** — `{{variable}}` placeholders resolved from global variables and the active environment (Dev/Prod by default). Values are stored in the project's `.idea` settings as plain text, so keep real secrets out of them.
- **Scripts & tests** — pre-request scripts (`name=value`, with `{{$timestamp}}` / `{{$guid}}`), post-response extraction (`name=$.json.path`) and assertions (status code, body contains, JSONPath match).
- **Import** — OpenAPI 3.0/3.1 and Swagger 2.0 specs (JSON or YAML), Postman v2.x collections (folders are flattened), `curl` commands, and the plugin's own collection JSON export.
- **Code snippets** — cURL, Python `requests` and JavaScript `fetch` for the current request.
- **Mock server** — *Tools → API First → Start Mock Server…* serves every operation of an OpenAPI/Swagger file on `127.0.0.1` (port configurable, default 8085) with the spec's examples or schema-generated sample JSON.
- **HTTP Client file** — *Tools → API First → Generate HTTP Client File…* creates a JetBrains HTTP Client (`.http`) file with one request per operation.

Settings live under *Settings → Tools → API First & Mocking Studio*: request timeout, redirect following, history size and mock server port.

## Installation
- Download from JetBrains Marketplace.
- Build from source (JDK 21): `./gradlew buildPlugin`; run checks with `./gradlew detekt test`.

## Free & open source

As of version 2026.8.0 this plugin is completely free — every feature is unlocked and no license is needed. The source code is available at https://github.com/JirakJ/api-first-mocking-studio under the Apache License 2.0.

## Contributing

Issues and pull requests are welcome — please open them on GitHub.

## License

Copyright 2026 Jakub Jirák. Licensed under the [Apache License, Version 2.0](LICENSE).
