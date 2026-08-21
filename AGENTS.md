# AGENTS.md

## Stack
- Java 25, Spring Boot 4.1.1 (Spring MVC + Data JPA + Mail + Actuator + Validation).
- Maven (wrapper: `mvnw` / `mvnw.cmd`).
- H2 file DB by default; JPA `ddl-auto: update` (no Flyway/Liquibase).
- `spring-dotenv` 4.0.0 — env vars auto-loaded from `.env` in repo root.

## Setup
1. `cp .env.example .env` (PowerShell: `Copy-Item .env.example .env`) and fill secrets. `.env` is gitignored.
2. Run: `.\mvnw.cmd spring-boot:run` (Linux/macOS: `./mvnw spring-boot:run`).
3. The wrapper targets a JDK 25. On Windows the README pins `$env:JAVA_HOME="C:\Users\jhonb\.jdks\corretto-25.0.1"` — match your local install or let `mvnw` auto-download.
4. Swagger UI: `http://localhost:<SERVER_PORT>/swagger-ui.html`. H2 console: `/h2-console` (default port `8080`, current local `.env` uses `8050`).

## Env / config gotchas
- `application.yml` reads env vars via `${VAR:default}`. Override either by editing `.env` or by setting the OS env var (dotenv is loaded at startup).
- `DB_URL` is whatever JDBC URL you set; `application.yml` does NOT pin the driver — when switching off H2 you must also override `spring.datasource.driver-class-name` (e.g. `org.postgresql.Driver`). The current local `.env` uses Postgres on `localhost:5433` without setting the driver.
- Email accounts are stored in DB (`email_accounts` table) keyed by `client_code`. On first boot, if the table is empty, a default SMTP account is seeded from `MAIL_*` env vars.
- Telegram bot token MUST match `<botId>:<secret>` (from `@BotFather`). `TelegramBotClient.isConfigured()` returns false otherwise and the channel reports disabled.
- Telegram webhook auto-register only runs when `NOTIFICATION_TELEGRAM_ENABLED=true`, `TELEGRAM_WEBHOOK_AUTO_REGISTER=true`, AND `TELEGRAM_WEBHOOK_URL` is non-empty (`TelegramWebhookRegistrar`). The URL must be HTTPS and reachable from Telegram.
- Telegram only delivers to active subscribers (those who sent `/start`); blocked/403 responses auto-flip the subscriber to inactive.

## Project layout (`com.jmcode.notification`)
- `channel/` — `NotificationChannel` impls (Email, WhatsApp, Telegram). New channels: implement `NotificationChannel`, register a `@Component`, add an entry to `ChannelType`.
- `email/` — `EmailAccount` JPA entity + repo + service. Multi-tenant SMTP per `clientCode`.
- `telegram/` — bot client, subscriber entity/repo/service, webhook payload, auto-registrar.
- `service/NotificationService` — routes by `ChannelType` via `EnumMap`; bulk uses `sendSafe` (no throw per item).
- `web/` — REST controllers (`/api/v1/notifications`, `/email-accounts`, `/telegram/*`), DTOs, `GlobalExceptionHandler`.
- `domain/` — `NotificationRequest`, `NotificationResult`, `NotificationStatus`, `ChannelType`.

## Commands
- Build: `.\mvnw.cmd clean package` (skip tests: `-DskipTests`).
- Run app: `.\mvnw.cmd spring-boot:run`.
- All tests: `.\mvnw.cmd test`.
- Single test class: `.\mvnw.cmd test -Dtest=NotificationServiceTest`.
- Single test method: `.\mvnw.cmd test -Dtest=NotificationServiceTest#sendSuccess`.
- No formatter/lint configured — don't invent one. Match existing code style (4-space indent, package-private JPA setters, record-based DTOs and config props).

## Test notes
- Context-load test (`JmNotificationApplicationTests`) disables email + telegram and uses in-memory H2, so it runs without real SMTP/Telegram creds.
- `NotificationServiceTest` uses an inline anonymous `NotificationChannel` — no Spring context needed.
- Telegram unit tests are in `src/test/java/.../telegram/`.
- Integration with external providers (SMTP, Telegram API) is not mocked at the HTTP layer; tests touching channels rely on the `isEnabled`/`isConfigured` gates or stubbed channels.

## REST surface (high-level)
- `POST /api/v1/notifications` — send one. `clientCode` selects SMTP account; if omitted, default (`is_default=true`) is used.
- `POST /api/v1/notifications/bulk` — array of `notifications`, returns per-item status (failures do not throw).
- `GET /api/v1/notifications/channels` — map of `channel -> enabled`.
- `GET/POST/PUT/DELETE /api/v1/email-accounts[/{id}]` — manage per-client SMTP accounts.
- `POST /api/v1/telegram/webhook-admin` `{publicBaseUrl}` — register webhook; `GET` info; `DELETE` remove.
- `POST /api/v1/telegram/webhook` — Telegram's inbound webhook (do not expose without `TELEGRAM_WEBHOOK_SECRET`).
- `GET /api/v1/telegram/subscribers[/{chatId}]`; `PUT /api/v1/telegram/subscribers/link` to bind `externalUserId`.

## Conventions
- No CI workflows, no pre-commit, no formatter/linter — keep it that way unless asked.
- Secrets never committed; `.env` only. Don't echo bot tokens / SMTP passwords in logs.
- DTOs are Java records; config properties are records under `config/`.
- Errors flow through `GlobalExceptionHandler` → `ApiError`. Throw `ChannelNotFoundException` or `NotificationSendException`; don't return ad-hoc responses.
