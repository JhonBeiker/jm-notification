# AGENTS.md

## Stack
- Java 25 (`java.version=25` en el `pom.xml`): con un JDK anterior el build falla con `release version 25 not supported`. Spring Boot 4.1.1.
- Starters: webmvc, security, data-jpa, mail, validation, actuator, **restclient** (en Boot 4 el `RestClient.Builder` no lo trae el starter web).
- Maven (wrapper: `mvnw` / `mvnw.cmd`).
- Postgres por defecto; H2 sólo con scope `test`. JPA `ddl-auto: update` (sin Flyway/Liquibase).
- `spring-dotenv` 4.0.0 — env vars auto-cargadas desde `.env` en la raíz.
- **Jackson 3** (`tools.jackson.*`) es el mapper de la aplicación. Jackson 2 sólo entra como transitiva de springdoc: no lo uses para databind.

## Setup
1. `cp .env.example .env` (PowerShell: `Copy-Item .env.example .env`) y rellena secretos. `.env` está gitignoreado.
2. Apunta `JAVA_HOME` a un JDK 25 (p. ej. `C:\Users\jhonb\.jdks\corretto-25.0.1`).
3. Ejecutar: `.\mvnw.cmd spring-boot:run` (Linux/macOS: `./mvnw spring-boot:run`).
4. Swagger UI: `http://localhost:<SERVER_PORT>/swagger-ui.html` (puerto por defecto `8050`).

## Env / config gotchas
- El driver JDBC se deriva de `DB_URL`; no hay que fijarlo aparte.
- `EMAIL_PASSWORD_ENCRYPTION_KEY` y `JWT_SECRET`: si están vacíos se genera una clave aleatoria por arranque y se avisa con WARN. En el primer caso los passwords SMTP guardados dejan de ser descifrables; en el segundo los tokens mueren al reiniciar.
- `JWT_SECRET` debe tener ≥ 32 caracteres o el arranque falla (antes se rellenaba con ceros).
- Cuentas SMTP y bots de Telegram viven en BD (`email_accounts`, `telegram_bot_accounts`), no en `.env`. No se auto-crea ninguna fila placeholder: al arrancar, `StartupConfigurationReport` avisa si falta configuración.
- Telegram sólo entrega a suscriptores activos (los que enviaron `/start`); un 403/blocked marca al suscriptor como inactivo.
- Timeouts HTTP salientes: `spring.http.clients.connect-timeout` / `read-timeout` (nombre en **plural**), sólo aplican al `RestClient.Builder` autoconfigurado.

## Estructura (`com.jmcode.notification`) — package by feature
- `channel/` — contrato compartido: `NotificationChannel` (SPI) + modelo (`ChannelType`, `NotificationRequest/Result/Status`, `Attachment`).
- `common/` — `ApiError`, `GlobalExceptionHandler` y las excepciones. Los servicios no dependen de la capa web.
- `config/` — `NotificationProperties`, `OpenApiConfig`, `StartupConfigurationReport`.
- `dispatch/` — `NotificationService` + `NotificationController` + dto.
- `email/`, `telegram/`, `whatsapp/` — cada canal con sus entidades, servicios, controladores y dto.
- `security/` — filtros JWT y API key, `SecurityConfig`, admin users, api clients.

Canal nuevo: implementa `NotificationChannel`, anótalo `@Component` y añade la constante en `ChannelType`. `NotificationService` los descubre por inyección y rechaza duplicados del mismo tipo.

## Comandos
- Build: `.\mvnw.cmd clean package` (sin tests: `-DskipTests`).
- Ejecutar: `.\mvnw.cmd spring-boot:run`.
- Tests: `.\mvnw.cmd test`; una clase: `-Dtest=JwtServiceTest`; un método: `-Dtest=JwtServiceTest#rejectsExpiredTokens`.
- Sin formatter/linter configurado — no inventes uno. Sigue el estilo existente (indentación de 4, records para DTOs y properties, Lombok `@RequiredArgsConstructor` en beans, `@Getter/@Setter` en entidades).

## Tests
- `JmNotificationApplicationTests` levanta el contexto completo contra H2 en memoria.
- `SecurityContractIntegrationTest` (MockMvc) fija el contrato HTTP: 401 en credenciales inválidas, endpoints admin autenticados, API key, webhook de Telegram con secret, 400 en canal desconocido.
- Unitarios sin Spring: `PasswordEncryptorTest`, `JwtServiceTest`, `ApiKeyGeneratorTest`, `EmailTemplateRendererTest`, `NotificationRequestTest`, `NotificationServiceTest`, `TelegramSubscriberServiceTest`.
- No hay mocks a nivel HTTP de SMTP/Telegram: los canales se prueban por sus gates `isEnabled`/`isConfigured` o con dobles.

## Superficie REST
- `POST /api/v1/admin/auth/login` `{email, password}` → `{token, expiresInSeconds, role}`. Credenciales inválidas → **401**.
- `GET/POST/PUT/DELETE /api/v1/admin/email-accounts[/{id}]` — sólo SUPER_ADMIN.
- `GET/POST/PUT/DELETE /api/v1/admin/telegram-bot-accounts[/{id}]` — SUPER_ADMIN o ADMIN.
- `GET/POST /api/v1/admin/api-clients`, `POST /{id}/rotate-key` (clave en claro sólo una vez), `DELETE /{id}` (204 sin body).
- `POST /api/v1/notifications`, `POST /api/v1/notifications/bulk`, `GET /api/v1/notifications/channels` — requieren `X-Api-Key`.
- `POST /api/v1/telegram/webhook` — público para Telegram, autenticado por el header `X-Telegram-Bot-Api-Secret-Token`.
- `/api/v1/telegram/webhook-admin` y `/api/v1/telegram/subscribers` — SUPER_ADMIN o ADMIN.

## Modelo de auth
- `AdminUser` (BCrypt, rol SUPER_ADMIN/ADMIN). Se siembra desde `ADMIN_EMAIL`/`ADMIN_PASSWORD` si la tabla está vacía.
- `ApiClient`: clave `jmk_<43 url-safe>`, hash SHA-256; el valor en claro sólo se devuelve al crear o rotar.
- JWT HS256 firmado con `JWT_SECRET`. Claims: `sub`, `email`, `role`.
- `SecurityConfig` termina en `anyRequest().authenticated()`: un endpoint nuevo nace cerrado. Públicos: health/info, swagger, `/error` y `POST /api/v1/telegram/webhook`.
- La consola H2 tiene su propia cadena `@Order(1)`, activa sólo si `spring.h2.console.enabled=true`.

## Convenciones
- Sin CI, sin pre-commit, sin formatter — mantenlo así salvo que se pida.
- Secretos sólo en `.env`. Nunca loguear tokens de bot ni passwords SMTP.
- Los DTOs de request hacen su propio mapeo (`applyTo(entity)`, `toDomain()`); los de response, `from(entity)`.
- Los errores salen por `GlobalExceptionHandler` → `ApiError`. El catch-all de `Exception` loguea el stacktrace y devuelve un mensaje genérico: no expongas `ex.getMessage()` en un 500.
- `log.error` recibe la excepción como último argumento para conservar el stacktrace.
- Los canales nunca lanzan desde `send()`: devuelven `NotificationResult.failed(...)` o `.skipped(...)`.
