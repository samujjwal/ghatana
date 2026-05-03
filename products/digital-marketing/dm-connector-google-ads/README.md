# dm-connector-google-ads

HTTP adapter implementations for the Google Ads platform integrations defined in `dm-application`.

## Purpose

Provides production-grade HTTP adapters for:

- **`HttpDmGoogleAdsOAuthClientAdapter`** — OAuth 2.0 authorization, token exchange, token refresh, and token revocation via `accounts.google.com`.
- **`HttpDmGoogleAdsCampaignApiClientAdapter`** — Campaign creation via the Google Ads REST API (`v14`).
- **`HttpDmGoogleAdsPerformanceApiClientAdapter`** — Campaign performance metrics retrieval via the Google Ads REST API (`v14`).

## Package

`com.ghatana.digitalmarketing.connector.googleads`

## Dependencies

- `dm-application` — application-layer ports (interfaces) that these adapters implement.
- `platform:java:testing` (test scope) — `EventloopTestBase` for ActiveJ-compatible test execution.
- OkHttp 4.12+ — HTTP client.
- Jackson 2.18+ — JSON serialization/deserialization.
- MockWebServer (test scope) — real HTTP server for adapter integration tests.

## Testing

All adapters are tested with real HTTP via `MockWebServer` (no Mockito). Tests extend `EventloopTestBase` and run inside the ActiveJ event loop.

```bash
./gradlew :products:digital-marketing:dm-connector-google-ads:check
```

Coverage gates: lines ≥ 85%, branches ≥ 75%.

## Configuration

Each adapter accepts a configurable base URL as the 5th constructor argument (defaults to the Google production endpoint). This enables test isolation without modifying production code.

| Adapter | Production Base URL |
|---|---|
| OAuth | `https://accounts.google.com` |
| Campaign API | `https://googleads.googleapis.com` |
| Performance API | `https://googleads.googleapis.com` |
