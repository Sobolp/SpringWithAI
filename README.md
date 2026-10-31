# SpringWithAI

A Spring Boot 4.1 REST service for alien records: CRUD and filtered search at `/api/aliens`, stored in an in-memory H2
database. In development it seeds 8 aliens and serves Swagger UI. In every profile it writes a rolling log file that
you can read, tail live and re-level from a browser on the same machine.

The full specification, with requirements, design decisions and test coverage, is in [docs/SPEC.md](docs/SPEC.md).

## Requirements

- **JDK 27.** The build uses a Java 27 toolchain, and Gradle does not download one, so a JDK 27 must be installed.
    Gradle itself needs JDK 17 or newer to start. If the default `java` is older, point `JAVA_HOME` at a newer JDK
    first, for example on macOS:

    ```bash
    export JAVA_HOME=$(/usr/libexec/java_home -v 27)
    ```

- Nothing else. The Gradle wrapper (9.8.2) and all dependencies are downloaded on the first build.

## Run

```bash
./gradlew bootRun
```

The app starts on port 8080 in the `dev` profile. Startup takes about 7 seconds. Until the web server is up,
connections to port 8080 are refused; after that, `/actuator/health` returns 503 until startup has finished, then
200 `{"status":"UP"}`.

| URL | What |
| --- | --- |
| http://localhost:8080/api/aliens | The Alien API |
| http://localhost:8080/swagger-ui.html | Swagger UI (`dev` only) |
| http://localhost:8080/v3/api-docs | OpenAPI document (`dev` only) |
| http://localhost:8080/logs | Live log page (localhost only) |
| http://localhost:8080/actuator/health | Health, public |
| http://localhost:8080/h2-console | H2 console (`bootRun` only; the JDBC URL is in the startup log) |

To run the packaged jar instead, start it with a Java 27 runtime. `JAVA_HOME` does not change which `java` is on
your `PATH`, so call it through `JAVA_HOME`:

```bash
./gradlew bootJar
"$JAVA_HOME/bin/java" -jar build/libs/SpringWithAI-0.0.1-SNAPSHOT.jar
```

### Profiles

| | No profile / `dev` | `prod` (or any other) |
| --- | --- | --- |
| Seed 8 aliens on startup | Yes, if the table is empty | No |
| Swagger UI and `/v3/api-docs` | Served | 404 |

`dev` is the default profile (`spring.profiles.default=dev`): it applies only when no profile is active. Activating
any profile replaces it, for example `--spring.profiles.active=prod`, or
`./gradlew bootRun --args='--spring.profiles.active=prod'`. To add a profile and keep `dev`, activate both:
`--spring.profiles.active=dev,local`.

## API

| Method | Path | Success | Errors |
| --- | --- | --- | --- |
| GET | `/api/aliens?type=&origin=` | 200, list | — |
| GET | `/api/aliens/{id}` | 200 | 400, 404 |
| POST | `/api/aliens` | 201 | 400, 415 |
| PUT | `/api/aliens/{id}` | 200 (full replace) | 400, 404, 415 |
| DELETE | `/api/aliens/{id}` | 204 | 400, 404 |

An alien has `name`, `origin`, `color` and `type`: all required, non-blank and at most 255 characters. Responses add
the generated `id`. Filters are exact matches that ignore case and surrounding whitespace. An `{id}` that is not a
number returns 400, and a `POST` or `PUT` body that is not `application/json` returns 415.

```bash
curl 'localhost:8080/api/aliens?type=humanoid'
curl -X POST localhost:8080/api/aliens -H 'Content-Type: application/json' \
     -d '{"name": "Zorg", "origin": "Mars", "color": "Green", "type": "Humanoid"}'
```

Errors use Spring Boot's error body (`timestamp`, `status`, `error`, `path`), documented as `ApiError` in the
OpenAPI document. Under `bootRun`, devtools adds `message`, `trace` and validation `errors`.

## Logs

The app logs to the console and to `logs/springwithai.log`. The file rolls daily and at 10 MB, rolled files are kept
for 7 days, and all log files together are capped at 100 MB.

- **Live view:** open http://localhost:8080/logs. It shows the last 64 KB and adds new lines every 2 seconds, with
    pause/resume, a level filter and auto-scroll.
- **Read the file:**

    ```bash
    curl localhost:8080/actuator/logfile                          # the whole active file
    curl -H 'Range: bytes=-2000' localhost:8080/actuator/logfile  # only the last 2000 bytes
    ```

- **Change a log level at runtime:**

    ```bash
    curl -X POST localhost:8080/actuator/loggers/org.example.springwithai \
         -H 'Content-Type: application/json' -d '{"configuredLevel": "DEBUG"}'
    ```

    Every change is logged at `WARN` with who made it, and reverts after 30 minutes. Logger groups such as `web`
    and `sql` work too. Set `{"configuredLevel": null}` to reset a logger; the root logger needs a level (e.g.
    `INFO`).

**Access.** There is no authentication yet, so the log file, the loggers endpoint and the log page answer only
requests from a loopback address (`127.0.0.0/8` or `::1`) whose `Host` is `localhost` or a loopback address, such as
`localhost`, `127.0.0.1` or `[::1]`. The `Host` check stops DNS rebinding. Everything else gets 403. To read logs from
another machine, use an SSH tunnel: `ssh -L 8080:localhost:8080 <host>`, then open http://localhost:8080/logs.
Do not put the app behind a reverse proxy on the same host while this is the only protection: every proxied request
would look local.

## Configuration

Set these in `application.properties`, or as `--name=value` arguments or environment variables.

| Property | Default | Meaning |
| --- | --- | --- |
| `spring.profiles.default` | `dev` | Profile used when none is active; see [Profiles](#profiles) |
| `spring.profiles.active` | none | Profiles to use instead of the default |
| `server.port` | `8080` | HTTP port |
| `logging.file.name` | `logs/springwithai.log` | Log file; the log page and `/actuator/logfile` read it |
| `logging.logback.rollingpolicy.max-file-size` | `10MB` | Roll size of the log file |
| `app.logs.level-ttl` | `30m` | How long a runtime log level change lasts |
| `app.logs.localhost-only` | `true` | Limit log access to localhost; set to `false` only once authentication protects it |

Leave `server.forward-headers-strategy` unset: with it, an `X-Forwarded-For` header could make a remote client look
local to the log access check.

## Test

```bash
./gradlew check
```

This runs unit tests, slice tests (`@WebMvcTest`, `@DataJpaTest`) and full-app tests on a random port.
[docs/SPEC.md](docs/SPEC.md#success-metrics) lists the test classes and what each covers.
Tests write their log file to `build/test-logs/`, not `logs/`.

## Project layout

| Path | Contents |
| --- | --- |
| `src/main/java/.../alien` | Entity, request/response records, repository, service, controller, seed data |
| `src/main/java/.../logs` | Log access: localhost-only guard, level-change audit and expiry |
| `src/main/java/...` | Application class, OpenAPI configuration, `ApiError` schema |
| `src/main/resources` | `application.properties`, `application-dev.properties`, `static/logs.html` |
| `src/test/java/...` | Tests, beside the code they cover |
| `gradle/libs.versions.toml` | All dependency and plugin versions |
| `docs/SPEC.md` | The specification |

## Status

For local use. No authentication, rate limiting or durable storage: data lives in memory and is lost on restart.
There is no CI; run `./gradlew check` before you push. Open questions are listed in
[docs/SPEC.md](docs/SPEC.md#open-questions).
