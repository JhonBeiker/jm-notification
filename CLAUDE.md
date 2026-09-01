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

1. **`.env`** (gitignored) → `application.yml` `${VAR:default}` placeholders → the
   `NotificationProperties` / `AuthProperties` records. It is loaded by
   `spring.config.import: optional:file:.env[.properties]`, **not** by `spring-dotenv`: that
   library registers itself through `META-INF/spring.factories`, which Spring Boot 4 no longer
   reads, so the whole `.env` was being ignored in silence — every `${VAR:default}` quietly fell
   back to its default (including `DB_URL`, which is why the app still started). The dependency is
   gone. A `.env` value is read as a `.properties` value: `\` escapes, so avoid backslashes.
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
├── company/      Company + CompanyScope (tenant of everything below), admin controller, dto
├── config/       NotificationProperties, OpenApiConfig, StartupConfigurationReport
├── dispatch/     NotificationService + NotificationController + dto
├── email/        EmailAccount*, EmailTemplate*, EmailNotificationChannel,
│                 PasswordEncryptor, admin controllers, dto
├── telegram/     bot accounts, subscribers, webhook, bot client, channel, dto
├── whatsapp/     WhatsAppDevice* (dispositivos GOWA), GowaClient,
│                 WhatsAppNotificationChannel, admin controller, dto
└── security/     JWT + API key auth, admin users, api clients, SecurityConfig
```

Exceptions live in `common/`, not in the web layer — services throw them without depending on web code.

### Channel dispatch
`NotificationChannel` (`supports()` / `isEnabled()` / `send()`) is the only extension point. `NotificationService` collects every `@Component` implementation into an `EnumMap` at construction and rejects duplicate `ChannelType` registrations. Adding a channel = implement the interface, annotate `@Component`, add a `ChannelType` constant.

`send()` throws `NotificationSendException` on a `FAILED` result (→ 502); `sendBulk()` catches it per item so one bad element never aborts the batch. Channels never throw out of `send()` — they return `NotificationResult.failed(...)` / `.skipped(...)`.

### Companies (`company/`)
`Company` is the tenant row: `AdminUser`, `EmailAccount`, `EmailTemplate` and `TelegramBotAccount`
all point at it through a nullable `company_id` FK (EAGER — `open-in-view` is off and the DTOs read
the company after the transaction closes). It replaced the loose `tenant_id` column those tables
carried; that column stays in the database untouched but is no longer mapped, so pre-existing rows
come back with `company = null` until a SUPER_ADMIN assigns one.

Two levels of administrator, told apart by the FK, not by a third role:
- **SUPER_ADMIN** — `company = null`. Creates companies (`/api/v1/admin/companies`) and their admin
  users (`/api/v1/admin/users`) — those two endpoints are SUPER_ADMIN-only — and sees every row.
- **ADMIN** — belongs to one company and manages its SMTP accounts, Telegram bots, email templates
  and API keys.

`ApiClient` carries the same FK: a key belongs to one company and may only send through that
company's accounts. `resolveAccount(clientCode)` asserts it (another company's `clientCode` → 403)
and, with no `clientCode`, resolves that company's default instead of the global one. A key always
gets a company — a company ADMIN's own, or the `companyId` a SUPER_ADMIN passes; creating one with
neither is a 400, since an unscoped key could send through any account. Only pre-existing keys have
no company, and those stay unscoped for compatibility — `StartupConfigurationReport` WARNs about them
at boot. Key names are unique per company, not globally.

`CompanyScope` is the single place that decides what a request may touch: `filterCompanyId()` for
listings, `assertCanAccess(company)` for reads/sends (403 on another company's row), and
`resolveOwner(requestedCompanyId)` for writes — a company admin's rows are always stamped with their
own company, and asking for another one is rejected. It reads the scope off whichever principal is
in the context: an `AuthPrincipal` (JWT) or the `ApiClient` entity (API key). With no principal at
all (startup runners, the inbound Telegram webhook) nothing is restricted. An ADMIN with no company
is denied instead of inheriting global access.

A cross-company `clientCode` surfaces as **403**: the channels re-throw `AccessDeniedException`
instead of turning it into a `FAILED` result (a 502 would blame the provider), while
`NotificationService.sendSafe` catches it per item so one bad element never aborts a bulk.

`isDefault` on an email account or bot stays **global** (it is what a send without `clientCode`
resolves to), so only a SUPER_ADMIN may set it.

Sending is deliberately unscoped: `resolveAccount(clientCode)` and the Telegram webhook lookup keep
working off `clientCode` / the webhook secret, not off any admin's company.

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

### WhatsApp via GOWA (`whatsapp/`)
WhatsApp goes through a self-hosted
[go-whatsapp-web-multidevice](https://github.com/aldinokemal/go-whatsapp-web-multidevice) (GOWA)
instance. Two earlier implementations were replaced: the Meta Cloud API, and then Waxum (its server
kept falling over). `WhatsAppDevice` is the per-client row — the WhatsApp twin of
`TelegramBotAccount`: `clientCode`, the `deviceId` *inside GOWA*, an optional
`apiUrl`/`basicAuthUser`/`basicAuthPassword` override, and `company_id`. Resolution, caching and the
default rule are the Telegram ones (`resolveDevice(clientCode)`, `WhatsAppDeviceManager` keyed by
`clientCode` and versioned by `updatedAt`, global `isDefault` only settable by a SUPER_ADMIN).

GOWA guards the whole instance with **Basic Auth** (`APP_BASIC_AUTH`) and picks the account with the
`X-Device-Id` header; every response is wrapped in `{code, message, results}` and `GowaClient`
returns `results` already unwrapped. `GOWA_API_URL` / `GOWA_BASIC_AUTH_USER` / `GOWA_BASIC_AUTH_PASSWORD`
are the fallback instance; a row's own password is AES-GCM encrypted through the shared
`PasswordEncryptor` (unlike Telegram tokens, which stay plaintext). An instance with no Basic Auth is
valid — the header is simply omitted.

**GOWA assigns the `deviceId`**, so creating a row calls `POST /devices` and stores the UUID it
returns, rolling the row back on a 502. A request *may* carry a `deviceId` to **adopt** a device
that already exists there (one already paired comes in as `logged_in`, no QR needed): that path
verifies it with `GET /devices/{id}` and copies its state. GOWA answers `500 device X not found`
for an unknown id — translated to a 404, since it is not an outage — and a `deviceId` already
linked to another row is a 400, because two clients on one device would cross messages. Pairing lives in GOWA, so
`/api/v1/admin/whatsapp-devices/{id}` exposes `qr` (`GET /app/login` → a `qr_link` PNG the instance
serves *without* Basic Auth, valid ~30s), `pair` (`GET /app/login-with-code`, an 8-char code),
`status` / `reconnect` (`GET /devices/{id}`, `GET /app/reconnect`), `logout` and `unregister`;
`status` and `reconnect` write the reported `state`/`jid` back onto the row (the phone number is the
JID's local part). `login-with-code` only accepts the device through the header, not as a path
segment, so every per-device call goes through `X-Device-Id`.

Until a device is paired GOWA answers sends with `401 you are not logged in`; that reaches the
caller as a FAILED result carrying the provider's own message. A Waxum-era detail that still holds:
a provider HTTP error becomes `UpstreamServiceException` (which carries the provider status code) →
**502**, while a cross-company `clientCode` still surfaces as 403. Sending is text-only for now —
GOWA has `/send/file`, `/send/image` etc., they just aren't wired to `Attachment` yet.

### Email rendering
Templates are managed through `/api/v1/admin/email-templates` (`EmailTemplateService`), unique by
name **per company**; an empty `contentType` is inferred from the body. `EmailNotificationChannel`
resolves a template inside the company that owns the SMTP account (and among the company-less
templates when the account has no company).

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

`JmNotificationApplicationTests` boots the full context against in-memory H2.
`SecurityContractIntegrationTest` and `CompanyScopeIntegrationTest` are MockMvc tests over the same
H2 context — the latter pins the tenant contract (a company admin only sees its own rows, 403 across
companies, 409 deleting a company that still has data). Its seed data is `static` because JUnit
builds one instance per test while the database is shared.
Request DTOs with primitive `boolean` components need every flag present in the JSON: Jackson refuses
to map an absent value onto a primitive. The rest are plain unit tests with no Spring context: `PasswordEncryptorTest`, `JwtServiceTest`, `ApiKeyGeneratorTest`, `EmailTemplateRendererTest`, `NotificationRequestTest`, `NotificationServiceTest` (anonymous `NotificationChannel`), `TelegramSubscriberServiceTest` (Mockito).

Constructing a `NotificationRequestDto` requires all nine record components, so adding a field there breaks every existing test call site.
