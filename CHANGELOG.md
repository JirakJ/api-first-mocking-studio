# Changelog

## [2026.8.0] - 2026-11-10

### Changed

- The plugin is now completely free — all features unlocked, no license needed.
- Source code is now open source under Apache-2.0: https://github.com/JirakJ/api-first-mocking-studio

## [2026.4.1] - 2026-04-18

### Changed
- Raised minimum compatibility to IntelliJ 2025.1 (build 251). Dropped support for IntelliJ 2024.3 (build 243) to align with current Plugin Verifier targets (IC 2025.1.7, 2025.2.6.1, IU 2025.3.4, 2026.1.1).
- Migrated the plugin verifier DSL to the modern `create(type, version) { useInstaller = … }` form and updated Kotlin `compilerOptions` DSL, removing deprecated Gradle configuration APIs that produced deprecation warnings for the 2024.3 baseline.

## [2026.3.6] - 2026-04-18

### Changed
- Enabled freemium licensing via `<product-descriptor optional="true">`. Plugin remains fully functional without a license; premium features may be introduced in future minor releases.


## [2026.2.39] - 2026-02-27
### Added
- **OAuth 2.0 Support:** Added native support for the OAuth 2.0 Client Credentials flow directly within the Auth tab.
- Users can input their Access Token URL, Client ID, Client Secret, and Scope, and generate a token with a single click without leaving the IDE.

## [2026.2.38] - 2026-02-27
### Added
- **Syntax Highlighting & Native Editor:** Replaced standard text areas with native IntelliJ `LanguageTextField` components.
- Request Body, Response Body, Pre-scripts, Post-scripts, and GraphQL views now support rich syntax highlighting, making complex JSON and scripts significantly easier to read and edit.

## [2026.2.37] - 2026-02-27
### Added
- **Dynamic Data Mocking:** The embedded mock server is now fully intelligent. It automatically generates realistic dummy data (JSON payloads) based on the schemas and examples defined in your imported OpenAPI specifications.
- This provides instant, zero-config backend simulation for frontend and mobile development teams.

## [2026.2.36] - 2026-02-27
### Added
- **WebSocket Support:** The plugin now intelligently detects `ws://` and `wss://` URLs and switches to a dedicated WebSocket interface.
- Includes a real-time message log showing sent and received messages, and a connection status indicator.

## [2026.2.35] - 2026-02-27
### Added
- **GraphQL Support:** Introduced native support for GraphQL queries.
- A dedicated "GraphQL" option in the Body type dropdown provides separate text areas for the Query and JSON Variables.
- Variables within the GraphQL payload are automatically resolved against the active environment.
- Code snippets (cURL, Python, JS) now correctly format GraphQL payloads.

## [2026.2.34] - 2026-02-27
### Added
- **Cookie Manager:** Added a dedicated dialog to view and manage cookies accumulated during API sessions.
- Accessible via a new folder icon in the main toolbar. You can inspect cookie details (Domain, Name, Value, Secure flag) and clear all cookies to reset your session state.

## [2026.2.33] - 2026-02-27
### Added
- **Postman Collection Import:** You can now natively import Postman Collections (v2.1 JSON format) directly into your workspace.
- This allows for an instant migration of your existing Postman setups, perfectly preserving request methods, URLs, headers, and raw bodies.

## [2026.2.32] - 2026-02-27
### Added
- **Import Collections from JSON:** Added the ability to import a collection from a previously exported JSON file.
- This feature is available via a new "Import" icon in the Collections sidebar toolbar, allowing seamless sharing of collections between team members or different IDE instances.

## [2026.2.31] - 2026-02-27
### Added
- **Export Collections:** Added the ability to export an entire collection (including all its requests, assertions, and scripts) to a single JSON file.
- This feature is available via a new "Export to JSON" icon in the Collections sidebar toolbar, enabling easy sharing and backups.

## [2026.2.30] - 2026-02-26
### Fixed
- Replaced deprecated `Messages.showChooseDialog` with modern `JBPopupFactory` list popup for collection selection.

## [2026.2.29] - 2026-02-26
### Added
- **Multipart File Upload Support:** Form-data parameters now support a "File" type.
- Selecting "File" in the body table automatically opens a native file chooser.
- The execution engine now correctly builds multipart requests with binary file content.

## [2026.2.28] - 2026-02-26
### Added
- **OpenAPI/Swagger Import:** You can now import existing OpenAPI 3.0/3.1 or Swagger 2.0 specifications directly into a collection. 
- Automatically generates requests for all endpoints and methods defined in the spec.
- Accessible via the "Download" icon in the Collections sidebar tab.

## [2026.2.27] - 2026-02-26
### Added
- **Extended Body Support:** Introduced support for multiple body types in requests.
- **Multipart/Form-Data:** Added a dedicated table-based UI to send form parameters.
- **x-www-form-urlencoded:** Full support for form-encoded bodies with automatic URL encoding.
- **Body Type Switcher:** Easily switch between None, Raw, and Form types directly in the Body tab.

## [2026.2.26] - 2026-02-26
### Added
- **Code Snippet Generation:** Introduced a new "Snippets" tab in the request panel. Automatically generates code for various languages and tools based on the current request configuration.
- Supported snippets: cURL, Python (Requests), and JavaScript (Fetch).
- Snippets update in real-time as you modify the URL, headers, or authentication settings.

## [2026.2.25] - 2026-02-26
### Added
- **Interactive JSON Tree View:** Added a "Tree" tab to the response panel that provides a hierarchical, navigable tree representation of JSON responses.
- Easy inspection of nested objects and arrays in large API payloads.

## [2026.2.24] - 2026-02-26
### Added
- **Test & Assertion Engine:** Introduced a new "Tests" tab to define verification rules for responses.
- Supports checking Status Code, Body Content (substring), and JSON Matching (using JSONPath syntax like `$.user.id=1`).
- A "Test Results" tab has been added to the response panel to show visual pass/fail feedback for each rule.
- Detailed summary of test results is now displayed in the status bar after each request.

## [2026.2.23] - 2026-02-26
### Added
- **Pre-request Scripts:** Introduced a "Pre-Script" tab to execute logic before a request is sent. Useful for setting dynamic environment variables.
- **Dynamic Macros:** Pre-scripts now support special macros like `{{$timestamp}}` and `{{$guid}}` for generating real-time data.
- Full persistence of Pre-scripts in History and Collections.

## [2026.2.22] - 2026-02-26
### Added
- **Authentication Tab:** Introduced a dedicated UI for managing request authentication. Supports Bearer Token, Basic Auth (Username/Password), and API Keys.
- Authentication parameters now support variable resolution, allowing for dynamic tokens from environments.

## [2026.2.21] - 2026-02-26
### Added
- **Request Collections:** Introduced a new "Collections" tab in the sidebar. Users can now group and organize their saved requests into custom-named collections.
- **Save to Collection:** Added a new button to the request panel to persist the current request configuration into a selected collection.

## [2026.2.20] - 2026-02-26
### Added
- **Query Parameters Synchronization:** Introduced a new "Params" tab in the request panel. It provides a dedicated table for URL query parameters that automatically syncs bi-directionally with the URL text field.

## [2026.2.19] - 2026-02-26
### Added
- **Request History Sidebar:** Implemented a new left sidebar that automatically tracks and displays previously sent requests.
- Users can click on any request in the history to instantly reload its method, URL, headers, and body into the current editor.

## [2026.2.18] - 2026-02-26
### Added
- **Manage Environments Dialog:** Added a dedicated settings window to create, rename, and delete environments, as well as manually edit their specific key-value variables. Accessible via the gear icon next to the environment selector.

## [2026.2.17] - 2026-02-26
### Added
- **Environments & Variables Management:** Introduced the ability to define distinct environments (e.g., Dev, Prod) with scoped variables.
- An environment selector has been added to the main toolbar.
- Variable resolution now correctly merges the active environment context with global defaults.

## [2026.2.16] - 2026-02-26
### Added
- JSON responses are now automatically pretty-printed in the response body tab.
- Request headers are now entered via a dynamic Key-Value table for better UX, matching industry standard API clients.

## [2026.2.15] - 2026-02-26
### Changed
- Removed all explicit references to competing products from plugin descriptors and UI placeholders to comply with branding guidelines.

## [2026.2.14] - 2026-02-26
### Changed
- Complete UI Rewrite: Transformed the Tool Window into a full-fledged, advanced REST client interface.
- Added explicit method dropdown, URL bar, Request Tabs (Body, Headers, Scripts), and Response Tabs (Body, Headers).
- Improved request execution feedback and error handling.

## [2026.2.13] - 2026-02-26
### Fixed
- Changed `<release-version>` in `plugin.xml` to `20262` to properly match the `2026.2.*` plugin versioning scheme for the JetBrains Marketplace.

## [2026.2.12] - 2026-02-26
### Fixed
- Correctly configured `pluginIcon` directly in `plugin.xml` and removed deprecated `instrumentationTools()`.

## [2026.2.11] - 2026-02-26
### Fixed
- Correctly configured `pluginIcon` in `build.gradle.kts`.

## [2026.2.10] - 2026-02-26
### Fixed
- Updated Gradle version to 8.13 to support IntelliJ Platform Gradle Plugin 2.11.0.

## [2026.2.9] - 2026-02-26
### Fixed
- Ensured correct versioning in build artifacts by updating build.gradle.kts and IntelliJ Platform Gradle Plugin.

## [2026.2.8] - 2026-02-26
### Fixed
- Ensured correct versioning in build artifacts (fixed reference to old sandbox version).

## [2026.2.7] - 2026-02-26
### Changed
- Re-attempting build and publish with new Marketplace token.

## [2026.2.6] - 2026-02-26
### Changed
- Re-attempting publish with new Marketplace token.

## [2026.2.5] - 2026-02-26
### Changed
- Attempting publish with updated Marketplace token.

## [2026.2.4] - 2026-02-26
### Added
- Default plugin icon (`pluginIcon.svg`).

### Added
- Default plugin icon (`pluginIcon.svg`).

## [2026.2.3] - 2026-02-26
### Changed
- Added internal performance note in OpenApiParser.

## [2026.2.1] - 2026-02-26
### Added
- **API Client Engine:** Real HTTP request execution directly from the IDE.
- **Response Viewer:** Dedicated panel with status, timing, and response body.
- **Persistence Layer:** Project-level storage for history and collections.
- Split-view UI for better API exploration and testing.

## [1.0.1] - 2026-02-26
### Added
- Embedded Mock Server (Ktor-based).
- HTTP Client Generator.
- Tool Window toolbar.

## [1.0.0] - 2026-02-26
### Added
- Initial project structure.
- OpenAPI parsing and Tree visualization.
