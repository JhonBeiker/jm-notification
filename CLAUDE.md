# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```powershell
.\mvnw.cmd spring-boot:run                                  # run (Linux/macOS: ./mvnw)
.\mvnw.cmd clean package                                    # build (-DskipTests to skip)
.\mvnw.cmd test                                             # all tests
.\mvnw.cmd test -Dtest=JwtServiceTest                       # one class
.\mvnw.cmd test -Dtest=JwtServiceTest#rejectsExpiredTokens  # one method
```

The wrapper only downloads Maven, not a JDK. The build targets **Java 25** (`java.version` in `pom.xml`), so `JAVA_HOME` must point at a JDK 25 — an older one fails with `release version 25 not supported`. No formatter, linter, or CI is configured; don't add one unasked.

Swagger UI at `/swagger-ui.html`, OpenAPI at `/v3/api-docs`, health at `/actuator/health`. Default port is `8050`.

## Configuration layering

1. **`.env`** (gitignored, loaded by `spring-dotenv`) → `application.yml` `${VAR:default}` placeholders → the `NotificationProperties` / `AuthProperties` records.
2. **`application.yml`** defaults to Postgres (`localhost:5433`). The JDBC driver is *derived from the URL*, so pointing `DB_URL` at H2 is enough to switch (this is what the tests do).
3. **Database rows** are the runtime config for SMTP accounts and Telegram bots. The `TELEGRAM_*` / `MAIL_*` env vars are legacy fallbacks.

Schema is managed by JPA `ddl-auto: update` — no Flyway/Liquibase, so entity changes migrate implicitly.

Two env vars have no persistent default and log a WARN when unset, because both silently invalidate stored data on restart: `EMAIL_PASSWORD_ENCRYPTION_KEY` (AES key for SMTP passwords) and `JWT_SECRET` (token signing key).

## Architecture

Package-by-feature. Each channel feature owns its entities, services, controllers and DTOs; `channel/` holds only the shared contract they all implement.

```
com.jmcode.notification
├── channel/      SPI + shared model: NotificationChannel, ChannelType,
│                 NotificationRequest/Result/Status, Attachment
├── common/       ApiError, GlobalExceptionHandler, shared exceptions
├── config/       NotificationProperties, OpenApiConfig, StartupConfigurationReport
├── dispatch/     NotificationService + NotificationController + dto
├── email/        EmailAccount*, EmailTemplate*, EmailNotificationChannel,
│                 PasswordEncryptor, admin controller, dto
├── telegram/     bot accounts, subscribers, webhook, bot client, channel, dto
├── whatsapp/     WhatsAppNotificationChannel
└── security/     JWT + API key auth, admin users, api clients, SecurityConfig
```

Exceptions live in `common/`, not in the web layer — services throw them without depending on web code.

### Channel dispatch
`NotificationChannel` (`supports()` / `isEnabled()` / `send()`) is the only extension point. `NotificationService` collects every `@Component` implementation into an `EnumMap` at construction and rejects duplicate `ChannelType` registrations. Adding a channel = implement the interface, annotate `@Component`, add a `ChannelType` constant.

`send()` throws `NotificationSendException` on a `FAILED` result (→ 502); `sendBulk()` catches it per item so one bad element never aborts the batch. Channels never throw out of `send()` — they return `NotificationResult.failed(...)` / `.skipped(...)`.

### Multi-tenancy by `clientCode`
Email and Telegram both resolve a per-client account row before sending. The resolution order lives in one place — `NotificationRequest.effectiveClientCode()`: explicit `clientCode`, else `metadata["clientCode"]`, else `is_default=true`, else first active row.

`EmailAccountManager` and `TelegramBotAccountManager` cache the built sender/client **keyed by `clientCode`**, storing the `updatedAt` they were built from. Editing an account replaces the entry; services also call `evict()` on save and delete. (An earlier version keyed the cache by `clientCode + updatedAt`, so every edit leaked a new entry.)

SMTP passwords are AES-GCM encrypted with an `enc:v1:` prefix (`PasswordEncryptor`); values without the prefix pass through as plaintext for backward compatibility. **Telegram bot tokens are stored in plaintext** — a known asymmetry, not an oversight to fix silently.

### Auth (`security/`)
Two filters run before `UsernamePasswordAuthenticationFilter`, each registered as a bean plus a disabled `FilterRegistrationBean` so the servlet container doesn't also apply them globally:

- `JwtAuthFilter` — `Authorization: Bearer <jwt>`, HS256 signed by `JwtService` (hand-rolled, no jjwt). A secret shorter than 32 chars is rejected at startup rather than zero-padded.
- `ApiKeyAuthFilter` — `X-Api-Key`, SHA-256 compared to `api_key_hash`, grants `ROLE_API_CLIENT`, updates `last_used_at` via a single `@Modifying` UPDATE.

`SecurityConfig` ends in `anyRequest().authenticated()`, so a new endpoint is closed by default; public paths are enumerated explicitly. The H2 console has its own `@Order(1)` chain that only exists when `spring.h2.console.enabled=true`.

### Telegram inbound
`POST /api/v1/telegram/webhook` identifies the bot by matching `X-Telegram-Bot-Api-Secret-Token` against active accounts. If *any* active account defines a secret, an unknown or missing secret is rejected with 401; the fallback to the default account only applies while no account has a secret configured.

`/start` creates or reactivates a `TelegramSubscriber` (binding the deep-link payload to `externalUserId`); `/stop` and any blocked/deactivated/forbidden/chat-not-found provider error flips `active=false`. Outbound `to` is a numeric `chat_id` or a linked `externalUserId`.

### Email rendering
`EmailTemplateRenderer` (a plain `@Component`, testable without SMTP) substitutes `{{var}}` placeholders — unknown placeholders are left intact rather than rendered as `null` — and detects HTML via one regex. `EmailNotificationChannel` resolves the account, renders, builds the MIME message and sends. Attachments arrive base64-encoded: email accepts many, Telegram takes only the first (`sendDocument`), WhatsApp none.

## Jackson: two majors on the classpath

Spring Boot 4.1 uses **Jackson 3** (`tools.jackson.*`) for the application `ObjectMapper`. Jackson 2 (`com.fasterxml.jackson.databind`) is present only as a springdoc transitive. Application code must use `tools.jackson.*` for databind; `com.fasterxml.jackson.annotation` annotations (`@JsonProperty`, `@JsonIgnoreProperties`) are still correct — Jackson 3 keeps those in place.

`RestClient.Builder` needs `spring-boot-starter-restclient` explicitly in Boot 4; the web starter no longer brings it. HTTP timeouts come from `spring.http.clients.*` (plural), which only applies to the auto-configured builder — don't declare a `RestClient.Builder` bean by hand.

**Telegram uploads need their own timeout.** `sendDocument` streams a file, so it cannot share the general read-timeout meant for text calls — at 15s it failed with `HttpTimeoutException: Request cancelled`. `TelegramBotAccountManager` builds a second `RestClient` via `ClientHttpRequestFactoryBuilder.detect().build(HttpClientSettings...)` using `notification.telegram.upload-read-timeout` (default 2m), and `TelegramBotClient.sendDocument` uses it. Any future streaming/upload call needs the same treatment.

## Conventions

- DTOs and config properties are Java records; JPA entities use Lombok `@Getter/@Setter/@NoArgsConstructor` with `@PrePersist`/`@PreUpdate` timestamps. Beans use `@RequiredArgsConstructor` rather than hand-written constructors.
- Request DTOs own their mapping (`applyTo(entity)`, `toDomain()`); response DTOs own `from(entity)`.
- Errors flow through `GlobalExceptionHandler` → `ApiError`. The `Exception` catch-all logs the stacktrace and returns a generic message — never echo `ex.getMessage()` on a 500.
- `log.error` takes the exception as the last argument so the stacktrace is kept.
- Never log bot tokens or SMTP passwords.

## Tests

`JmNotificationApplicationTests` boots the full context against in-memory H2. The rest are plain unit tests with no Spring context: `PasswordEncryptorTest`, `JwtServiceTest`, `ApiKeyGeneratorTest`, `EmailTemplateRendererTest`, `NotificationRequestTest`, `NotificationServiceTest` (anonymous `NotificationChannel`), `TelegramSubscriberServiceTest` (Mockito).

Constructing a `NotificationRequestDto` requires all nine record components, so adding a field there breaks every existing test call site.
