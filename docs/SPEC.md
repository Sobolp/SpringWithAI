# SpringWithAI Repository Specification

_As of 2026-10-31: `master` at `fbdbb3b`, plus web log access (branch `feature/log-access`)._

## Overview

SpringWithAI is a Spring Boot 4.1 REST service at `/api/aliens` with CRUD and filtered search over alien records (name, origin, color, type), stored in in-memory H2. In the `dev` profile it seeds 8 aliens and serves Swagger UI plus an OpenAPI 3.1 document that describes every response, including error bodies. In every profile it writes a rolling log file that can be read, tailed live at `/logs`, and re-leveled at runtime from a browser on the same machine. This spec describes `master` on [GitHub](https://github.com/Sobolp/SpringWithAI), which includes the merged Swagger work from [PR #1](https://github.com/Sobolp/SpringWithAI/pull/1), plus the web log access from the [Web Log Access Spec](https://claude.ai/artifact/XgB27Gj9KbPWUumvzfpmxy).

## Goals

- Full CRUD on aliens over HTTP/JSON: list, get by id, create, full replace (PUT), delete.
- Filter the list by `type` and/or `origin`: exact match, case-insensitive, whitespace-trimmed.
- Reject bad input with 400 and unknown ids with 404.
- Keep the JSON contract (`AlienRequest` / `AlienResponse` records) separate from the JPA entity.
- Publish accurate, test-verified OpenAPI docs and Swagger UI for local development.
- Start with zero setup: in-memory database, 8 seeded aliens in `dev`.
- Read and tail the running app's logs, and change log levels, from a browser, without shell access.

## Non-Goals

- Authentication, authorization or rate limiting. Until authentication exists, log access is limited to localhost.
- Central log aggregation, log search, or downloading rolled log files.
- Durable storage: H2 runs in memory and loses data on restart.
- PATCH, bulk operations, optimistic locking.
- Pagination, sorting, partial-text or fuzzy search.
- A UI beyond Swagger UI and the H2 console.
- CI, containers or deployment config. The repo has none.

## Background & Context

| Concern | Choice |
| --- | --- |
| Framework | Spring Boot 4.1.2: `starter-webmvc`, `starter-data-jpa`, `starter-validation`, `h2console` |
| API docs | springdoc-openapi 3.2.0 (`starter-webmvc-ui`), built against Boot 4.1.2 |
| Operations | `starter-actuator`: only `health`, `logfile` and `loggers` exposed; Logback rolling file `logs/springwithai.log` |
| Language | Java 27 toolchain, Lombok |
| Database | H2 in memory |
| Build | Gradle 9.8.2 wrapper; all versions in `gradle/libs.versions.toml`; `build-info` without build time |
| Tests | JUnit 6.0.3, AssertJ, Mockito 5.23 loaded as a `-javaagent`, `MockMvcTester` |
| Dev tooling | `spring-boot-devtools` (`developmentOnly`): hot restart, full error details under `bootRun` |
| Code style | 4-space indentation, no tab characters in any file (`CLAUDE.md`) |

**History.** Four commits on `master`, newest first:

| Commit | Change |
| --- | --- |
| `fbdbb3b` | This specification, `docs/SPEC.md` (squash of PR #2) |
| `971f3de` | Swagger UI and OpenAPI docs, `ApiError` schema, version catalog, `MockMvcTester` tests (squash of PR #1) |
| `b3e88b6` | Request/response records in the Alien API |
| `de2740e` | Alien service with dummy data and tests |

**Branches.** `master` is `fbdbb3b`. `feature/log-access` adds web log access. `feature/swagger` (`956a282`) and `docs/spec` are merged and can be deleted.

## Requirements

### Functional Requirements

**Alien API**

1. `GET /api/aliens` MUST return 200 and every alien when no filter is given.
2. `GET /api/aliens?type=&origin=` MUST return only aliens matching every non-blank filter, ignoring case and surrounding whitespace. Blank filters MUST be ignored. No match MUST return 200 with `[]`.
3. `GET /api/aliens/{id}` MUST return 200 and the alien, or 404 if the id does not exist.
4. `POST /api/aliens` MUST store a new alien, generate its id and return 201. A client-sent `id` MUST be ignored.
5. `PUT /api/aliens/{id}` MUST replace all four fields and return 200, or 404 if the id does not exist. It MUST NOT create a missing alien.
6. `DELETE /api/aliens/{id}` MUST return 204 with no body, or 404 if the id does not exist.
7. `name`, `origin`, `color` and `type` MUST be present, non-blank and at most 255 characters after stripping; otherwise the API MUST return 400 and store nothing.
8. Request values MUST be stripped of surrounding whitespace before validation and storage.
9. Responses MUST have exactly the fields `id`, `name`, `origin`, `color`, `type`.

**Profiles and seed data**

10. With no profile set, the app MUST run as `dev` (`spring.profiles.default=dev`).
11. In `dev`, the app MUST seed 8 aliens on startup when the table is empty. Other profiles MUST NOT seed.

**API docs**

12. In `dev`, the app MUST serve Swagger UI at `/swagger-ui.html` (redirecting to `/swagger-ui/index.html`) and the OpenAPI document at `/v3/api-docs`.
13. Outside `dev` (e.g. `prod`), all three docs URLs MUST return 404.
14. The document MUST give title `SpringWithAI API` and the project version from Gradle `build-info`, or `unknown` if build-info or its version is missing or blank.
15. Each operation MUST list its real success code (200, 201 or 204) and its error codes: 404 on `GET`/`PUT`/`DELETE /{id}`, 400 on `POST` and `PUT`.
16. Every 400 and 404 MUST be documented as `application/json` with the `ApiError` schema: required `timestamp`, `status`, `error`, `path`; optional `message`, `trace` and `errors` (items: `ApiBindingError`).
17. Real error bodies MUST contain only fields documented in `ApiError`, and always its 4 required fields.

**Log access**

18. The app MUST log to `logs/springwithai.log` (`logging.file.name`) as well as the console, in every profile. The file MUST roll daily and at 10 MB, keep rolled files for 7 days, and cap all log files at 100 MB.
19. `GET /actuator/logfile` MUST return the active log file as `text/plain;charset=UTF-8`. A `Range` header MUST return only those bytes with 206, and a range past the end MUST return 416. Without a log file it MUST return 404.
20. `GET /logs` MUST serve a page that shows the last 64 KB, appends new lines every 2 seconds, and reloads the tail when the file rolls. It MUST get its text only through a `LogStream` interface (today `RangePollingLogStream`), so a push implementation can replace polling.
21. `GET /actuator/loggers[/{name}]` MUST list levels, and `POST /actuator/loggers/{name}` MUST change one at once. Logger groups (`web`, `sql`, `logging.group.*`) MUST be audited and reverted as groups. A changed level MUST revert to the level before its first change after `app.logs.level-ttl` (30 minutes). Every change and revert MUST be logged at `WARN`, a change with who made it: the user once authentication exists, the client address until then.
22. Only `health`, `logfile` and `loggers` MUST be exposed. `GET /actuator/health` MUST be public and return exactly `{"status":"UP"}` once the app is ready, and 503 `{"status":"OUT_OF_SERVICE"}` before that (startup, including seeding, not finished).
23. While `app.logs.localhost-only=true` (the default), `/actuator/logfile`, `/actuator/loggers/**`, `/logs` and `/logs.html` MUST answer only requests from a loopback socket address whose `Host` is `localhost` or a loopback address (this stops DNS rebinding). Others MUST get 403 with the `ApiError` body.

### Non-Functional Requirements

- **Security:** no authentication on the API or the H2 console. Docs are hidden only outside `dev`, and `dev` is the default, so a run with no profile exposes them. Local use only. Logs can hold exception messages and, at `DEBUG`, request details, so log access is localhost-only until authentication exists, with a `Host` check against DNS rebinding; `server.forward-headers-strategy` must stay unset, or `X-Forwarded-For` could fake a local client. Behind a reverse proxy on the same host every request looks local, so the log endpoints must not be published that way.
- **Log disk use:** at most 100 MB under `logs/` (git-ignored). Level changes revert after 30 minutes, so a forgotten `DEBUG` cannot fill it.
- **Error detail:** a packaged jar returns only the 4 required error fields. Under `bootRun`, devtools turns on `spring.web.error.include-*`, which adds `message`, a full stack `trace` and `errors`.
- **Persistence:** in memory; a restart loses all changes.
- **Consistency:** one transaction per service call; reads run as read-only transactions.
- **Performance:** no targets set. The list endpoint returns all rows in one response. Startup measured at 5.5 to 7 s on a developer laptop.
- **Build reproducibility:** every dependency and plugin version lives in `gradle/libs.versions.toml`; Boot-managed libraries take their versions from the Spring Boot BOM.
- **Test speed:** the two full-app integration test classes share one cached context through `@AppIntegrationTest`.
- **Code style:** 4 spaces, no tabs, in every file.

## Design / Approach

A three-layer Spring MVC app (controller, service, repository) over one JPA entity, with springdoc documenting it in `dev`.

```
                 Requests flow down four layers; springdoc documents them in dev

  +------------------------------------------+
  | HTTP client                              |
  | curl, Swagger UI or tests -> /api/aliens |
  +------------------------------------------+
                      | HTTP request
                      v
  +------------------------------------------+  scans   +------------------------------+
  | AlienController                          | <- - - - | springdoc (dev profile only) |
  | Validates AlienRequest (@Valid -> 400)   |          | /swagger-ui.html,            |
  | Returns AlienResponse; 201/204 via       |          | /v3/api-docs; reads          |
  | @ResponseStatus                          |          | annotations, OpenApiConfig,  |
  +------------------------------------------+          | ApiError                     |
                      | AlienRequest                    +------------------------------+
                      v
  +------------------------------------------+  throws  +------------------------------+
  | AlienService (@Transactional)            | -------> | Spring Boot error handling   |
  | search(): picks a derived query          |          | Writes the 400 / 404 JSON    |
  | findById() throws AlienNotFoundException |          | body, documented as ApiError |
  +------------------------------------------+          +------------------------------+
                      | Alien entity
                      v
  +------------------------------------------+  saveAll +------------------------------+
  | AlienRepository (JpaRepository)          | <------- | AlienDataLoader              |
  | CRUD + findByTypeIgnoreCase,             |          | dev profile only (default);  |
  | findByOriginIgnoreCase,                  |          | saves 8 aliens on startup    |
  | findByTypeIgnoreCaseAndOriginIgnoreCase  |          | when the table is empty      |
  +------------------------------------------+          +------------------------------+
                      | SQL via Hibernate
                      v
  +------------------------------------------+
  | H2 in-memory database                    |
  | Table ALIEN; console at /h2-console      |
  +------------------------------------------+
```

The controller only translates HTTP and JSON. The service owns transactions, filter choice and the 404 rule. Error bodies come from Spring Boot, not from app code; `ApiError` only describes them.

### Endpoints

| Method | Path | Body | Success | Documented errors |
| --- | --- | --- | --- | --- |
| GET | `/api/aliens?type=&origin=` | — | 200, `AlienResponse[]` | — |
| GET | `/api/aliens/{id}` | — | 200, `AlienResponse` | 404 |
| POST | `/api/aliens` | `AlienRequest` | 201, `AlienResponse` | 400 |
| PUT | `/api/aliens/{id}` | `AlienRequest` | 200, `AlienResponse` | 400, 404 |
| DELETE | `/api/aliens/{id}` | — | 204 | 404 |
| GET | `/swagger-ui.html`, `/v3/api-docs` | — | `dev` only | 404 elsewhere |
| GET | `/actuator/health` | — | 200, `{"status":"UP"}`; public | — |
| GET | `/actuator/logfile` (optional `Range`) | — | 200 or 206, `text/plain` | 404 no file, 416 bad range, 403 not localhost |
| GET, POST | `/actuator/loggers`, `/actuator/loggers/{name}` | POST: `{"configuredLevel": "DEBUG"}` | 200 / 204 | 400 bad level, 403 not localhost |
| GET | `/logs` | — | 200, live log page (forwards to `/logs.html`) | 403 not localhost |

### Profiles

| Setting | No profile / `dev` | `prod` (or any other) |
| --- | --- | --- |
| Seed 8 aliens | Yes, if the table is empty | No |
| Swagger UI and `/v3/api-docs` | Served | 404 |
| Error detail fields | Off in a jar; on under `bootRun` (devtools) | Same |

### Code layout

| Path | Contents |
| --- | --- |
| `org.example.springwithai` | `SpringWithAiApplication`, `OpenApiConfig` (title, version), `ApiError` (doc-only error schema) |
| `org.example.springwithai.alien` | Entity, request/response records, repository, service, controller, `AlienDataLoader`, `AlienNotFoundException`, `@NotFoundResponse`, `@BadRequestResponse` |
| `org.example.springwithai.logs` | `LocalhostOnlyLogAccessFilter` (stopgap guard), `LoggerChangeAuditFilter` (WARN audit), `LoggerLevelExpiry` (reverts levels and groups, one lock per change), `LogAccessConfig` (scheduling; registers both filters for their URL patterns), `LogsPageConfig` (`/logs`), `LogPaths` |
| `src/main/resources` | `application.properties` (default profile, docs off, log file, Actuator exposure, `app.logs.*`), `application-dev.properties` (docs on), `static/logs.html` (live log page) |
| `gradle/libs.versions.toml` | All dependency and plugin versions |
| `src/test/...` | Tests for the records, repository, service, controller and `OpenApiConfig`, plus `OpenApiDocsTest`, `LogAccessTest`, unit tests for the `logs` package, and the `@AppIntegrationTest` meta-annotation |
| `docs/SPEC.md` | This document |

### Build and test

- `./gradlew bootRun`: starts on port 8080 in `dev`, with devtools.
- `./gradlew check`: unit, slice (`@WebMvcTest`, `@DataJpaTest`) and full-app tests. Tests write their log file to `build/test-logs/`, not `logs/`.
- `./gradlew bootJar`: `build/libs/SpringWithAI-0.0.1-SNAPSHOT.jar`, without devtools.
- Mockito runs as a `-javaagent`. `build-info` leaves out the build time, so it changes only when the version does.

## Key Decisions & Trade-offs

| Decision | Why | Alternative | Cost |
| --- | --- | --- | --- |
| Request/response records separate from the entity | JSON contract cannot leak persistence changes; clients cannot set `id` | Expose the `Alien` entity | A mapping step; the 255-char limit declared twice (shared via `Alien.MAX_LENGTH`) |
| Strip whitespace in the `AlienRequest` constructor | Stored values match stripped filters; `"  "` fails `@NotBlank` | Strip in the service | No intentional edge spaces |
| PUT is a full replace; no PATCH | One update path, all fields validated | PATCH merge semantics | Clients send all four fields to change one |
| Exact, case-insensitive filters via derived queries | No query code | `LIKE` search, JPA Specifications | Each new filter doubles the branches in `AlienService.search` |
| `dev` is the default profile, and it seeds data and serves docs | Zero-setup local runs | Require an explicit profile | A run with no profile seeds dummy data and exposes docs (kept on purpose) |
| springdoc, docs gated by `springdoc.*.enabled` per profile | Standard for Spring MVC; no code to hide endpoints | Exclude the dependency outside dev | The library ships in every jar |
| Document Spring Boot's own error body (`ApiError`) | Accurate docs without changing API behavior | Switch to RFC 9457 `ProblemDetail` | The schema is doc-only and must track Boot's format; tests guard it |
| `@NotFoundResponse` / `@BadRequestResponse` meta-annotations | One definition per error response | Repeat `@ApiResponse` per method | `get` and `update` must declare 200 explicitly, because springdoc stops inferring it once any response is declared |
| API version from Gradle `build-info` | Never drifts from `build.gradle` | Hardcoded string | Falls back to `unknown` in builds without Gradle |
| Version catalog for all dependencies | One place for versions; Boot and springdoc side by side | Inline strings in `build.gradle` | One more file to read |
| Logs through Actuator `logfile` + `loggers`, plus a static page that polls with `Range` | Built-in endpoints; the server keeps no per-client state | Custom SSE endpoint with an in-memory appender; Spring Boot Admin; Loki/ELK | Up to 2 s delay; behind a `LogStream` interface, so SSE can be added later |
| Roll detection by re-reading the last 64 bytes shown | Catches a roll even when the new file has already grown past the old offset | Only watch for a smaller file | One extra 64-byte overlap per poll |
| Localhost-only filter (socket address + `Host`) until authentication exists, registered by URL pattern | Log access now, without building authentication first; the container matches the decoded, normalized path | Wait for authentication; hand-written path matching | No protection behind a same-host reverse proxy |
| Audit and expiry as a filter around `POST /actuator/loggers/{name}` | Small; sees the client and the real outcome | Hook the loggers operation or `LoggingSystem` | Misses changes that bypass this HTTP path (a separate `management.server.port`, JMX); revisit if either is used |
| Level changes expire after 30 minutes | A forgotten `DEBUG` cannot fill the disk | Manual reset | Long debugging sessions must re-apply the level |
| Health probe groups off, readiness indicator on | Public health shows only the status, and still says 503 until the app is ready | Keep liveness/readiness groups | Re-enable `management.endpoint.health.probes.enabled` for Kubernetes |
| `MockMvcTester` + AssertJ, shared `@AppIntegrationTest` context on a random port | Fluent assertions; real server needed to see Boot's error bodies | `MockMvc` + Hamcrest; MOCK environment | A real port per integration context; the `prod` and all-error-details groups each start their own context |

## Open Questions

- [ ] Before any shared deployment: should the default profile stop being `dev`? Today a run with no profile seeds dummy data and serves the docs.
- [ ] Keep Spring Boot's error body, or move to RFC 9457 `ProblemDetail`? Either way, `ApiError` must follow.
- [ ] Document the 400 returned for a non-numeric id (e.g. `GET /api/aliens/abc`)?
- [ ] Are duplicate aliens allowed? Nothing prevents two identical records.
- [ ] Should `name` or `color` be filterable, and should filters allow partial matches?
- [ ] At what row count does the list endpoint need pagination?
- [ ] Is a durable database planned? If so, which one, and with Flyway or Liquibase?
- [ ] Set `spring.jpa.open-in-view=false` explicitly? Startup logs a warning about it.
- [ ] `AlienService.findAll()` has no caller. Keep or remove?
- [ ] Add CI (e.g. GitHub Actions running `./gradlew check` on pull requests)? The repo has none, so PRs get no checks.
- [ ] Delete the merged `feature/swagger` and `docs/spec` branches?
- [ ] When authentication ships: set `app.logs.localhost-only=false` and protect the log paths with the `ADMIN` rules from the Authentication spec.

## Success Metrics

`./gradlew check` passes with 191 test executions: 116 test methods, plus extra runs of the parameterized ones. As measured on `feature/log-access`:

| Test class | Methods | What it covers |
| --- | --- | --- |
| `LogAccessTest` | 25 | Log file and `Range`, `/logs` page, level and group change audit and expiry, exposure, health readiness, localhost-only guard incl. `Host` (DNS rebinding) and a real-socket 403 `ApiError` |
| `AlienControllerTest` | 17 | HTTP layer with a mocked service (`@WebMvcTest`, `MockMvcTester`) |
| `AlienServiceTest` | 15 | Search branching, CRUD, 404 rule |
| `OpenApiDocsTest` | 14 | Documented responses and schemas, Swagger UI redirect, `prod` hides docs, real error bodies vs `ApiError` (default and all-details settings) |
| `AlienRepositoryTest` | 8 | Derived queries on H2 (`@DataJpaTest`) |
| `AlienRequestTest` | 8 | Stripping and Bean Validation limits |
| `LogPathsTest` | 3 | Path joining, control characters |
| `OpenApiConfigTest` | 4 | Version from build-info, `unknown` fallback |
| `LoggerChangeAuditFilterTest` | 7 | Audit line with address or user, nothing on rejected changes, no revert when back at the original, control characters, name decoding (`%3B`, `;params`), untouched other requests |
| `LoggerLevelExpiryTest` | 7 | Revert after the TTL, timer restart, stale timers, cancel on manual reset, failed change, groups, a revert waiting for a change in progress |
| `LocalhostOnlyLogAccessFilterTest` | 5 | Loopback detection without DNS; accepted and refused `Host` names |
| `AlienResponseTest` | 2 | Entity-to-response mapping |
| `SpringWithAiApplicationTests` | 1 | Full context starts |

Manual smoke check, last run 2026-10-31:

| Run | Request | Expected |
| --- | --- | --- |
| `./gradlew bootRun` (dev) | `GET /api/aliens` | 200, 8 aliens |
| | `GET /swagger-ui.html` | 302 to `/swagger-ui/index.html`, which returns 200 |
| | `GET /v3/api-docs` | every operation with the documented codes; version `0.0.1-SNAPSHOT` |
| | `GET /api/aliens/999999`, `POST {}` | 404 / 400; body includes `message`, `trace` (and `errors` for 400), all documented |
| Jar, `--spring.profiles.active=prod` | docs URLs | 404 |
| | `GET /api/aliens` | 200, `[]` (no seeding) |
| | 404 / 400 error bodies | exactly `timestamp`, `status`, `error`, `path` |
| `./gradlew bootRun` (dev) | `/actuator/health` during and after startup | 503 `OUT_OF_SERVICE`, then 200 `UP` with 8 aliens seeded |
| Jar, 40 KB roll limit | `/actuator/health`; `Range: bytes=-300` on `/actuator/logfile`; `/actuator/env` | `{"status":"UP"}`; 206 with `Content-Range`; 404 |
| | `/actuator/logfile` from the machine's LAN address, also as `%6Cogfile`, `logfile;x=1`, `./logfile` | 403 `ApiError`, plus a `WARN` "Refused" line |
| | `/actuator/logfile` from localhost with `Host: attacker.example` | 403 |
| | `POST /actuator/loggers/web` `WARN` | 204; audited as the `web` group, with a revert time |
| | `POST /actuator/loggers/org.example.springwithai` `DEBUG` | 204; `WARN` audit line with the client address and revert time |
| | `/logs` in headless Chrome while the log rolled 106 times | stays "Live"; all 163 shown lines match the log files verbatim |

Running the jar needs a JDK 27 on the machine. Gradle uses an installed JDK 27 for its own tasks; no toolchain auto-download is configured.
