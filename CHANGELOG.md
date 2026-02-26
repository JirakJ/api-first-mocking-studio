# Changelog

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

### Changed
- Updated Marketplace Product Code to `PAPIFIRSTMOCKIN`.
- Confirmed FREEMIUM pricing model configuration.

## [2026.2.3] - 2026-02-26
### Changed
- Added internal performance note in OpenApiParser.

## [2026.2.2] - 2026-02-26
### Changed
- Updated Marketplace Product Code to `PAPIFIRSTMOCKIN`.
- Confirmed FREEMIUM pricing model configuration.

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
