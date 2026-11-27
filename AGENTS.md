# AGENTS.md

## Stack
- Java 25 (`java.version=25` en el `pom.xml`): con un JDK anterior el build falla con `release version 25 not supported`. Spring Boot 4.1.1.
- Starters: webmvc, security, data-jpa, mail, validation, actuator, **restclient** (en Boot 4 el `RestClient.Builder` no lo trae el starter web).
- Maven (wrapper: `mvnw` / `mvnw.cmd`).
- Postgres por defecto; H2 sólo con scope `test`. JPA `ddl-auto: update` (sin Flyway/Liquibase).
- `.env` en la raíz, cargado con `spring.config.import: optional:file:.env[.properties]` (no con `spring-dotenv`: se registra por `META-INF/spring.factories`, que Spring Boot 4 ya no lee).
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
- Empresas, cuentas SMTP, bots de Telegram y plantillas viven en BD (`companies`, `email_accounts`, `telegram_bot_accounts`, `email_templates`), no en `.env`. La antigua columna `tenant_id` la sustituye el FK `company_id`; la columna sigue en la BD pero ya no se mapea, así que las filas antiguas quedan sin empresa hasta que un SUPER_ADMIN se la asigne. No se auto-crea ninguna fila placeholder: al arrancar, `StartupConfigurationReport` avisa si falta configuración.
- Telegram sólo entrega a suscriptores activos (los que enviaron `/start`); un 403/blocked marca al suscriptor como inactivo.
- Timeouts HTTP salientes: `spring.http.clients.connect-timeout` / `read-timeout` (nombre en **plural**), sólo aplican al `RestClient.Builder` autoconfigurado.

## Estructura (`com.jmcode.notification`) — package by feature (con subpaquetes por capa)
- `channel/` — contrato compartido (plano): `NotificationChannel` (SPI) + modelo (`ChannelType`, `NotificationRequest/Result/Status`, `Attachment`).
- `common/` — `exception/` (excepciones de dominio/aplicación) y `web/` (`ApiError`, `GlobalExceptionHandler`). Los servicios no dependen de la capa web.
- `company/` — `controller/`, `dto/`, `entity/` (`Company`), `repository/`, `service/` (`CompanyService`, `CompanyScope`).
- `config/` — configuración global (plano): `AppConfig`, `NotificationProperties`, `OpenApiConfig`, `StartupConfigurationReport`.
- `dispatch/` — `controller/` (`NotificationController`, `NotificationAdminController`), `dto/`, `entity/` (`NotificationLog`), `repository/`, `service/` (`NotificationService`, `NotificationLogService`, `NotificationQueueService`, `NotificationQueueWorker`).
- `email/`, `telegram/`, `whatsapp/` — cada canal organizado en `channel/`, `controller/`, `dto/`, `entity/`, `repository/`, `service/` (y `client/` en `telegram` y `whatsapp`).
- `security/` — `config/` (`SecurityConfig`, `AuthProperties`, `AdminUserSeeder`), `controller/`, `dto/`, `entity/` (`AdminUser`, `ApiClient`), `filter/` (JWT y API key), `model/` (`AuthPrincipal`, `Role`), `repository/`, `service/`.
- `src/test/java` — los tests unitarios espejan los subpaquetes de `src/main/java`; los tests de integración (`*IntegrationTest`) viven en la raíz de su módulo.

Canal nuevo: implementa `NotificationChannel`, anótalo `@Component` y añade la constante en `ChannelType`. `NotificationService` los descubre por inyección y rechaza duplicados del mismo tipo.

## Comandos
- Build: `.\mvnw.cmd clean package` (sin tests: `-DskipTests`).
- Ejecutar: `.\mvnw.cmd spring-boot:run`.
- Tests: `.\mvnw.cmd test`; una clase: `-Dtest=JwtServiceTest`; un método: `-Dtest=JwtServiceTest#rejectsExpiredTokens`.
- Sin formatter/linter configurado — no inventes uno. Sigue el estilo existente (indentación de 4, records para DTOs y properties, Lombok `@RequiredArgsConstructor` en beans, `@Getter/@Setter` en entidades).

## Tests
- `JmNotificationApplicationTests` levanta el contexto completo contra H2 en memoria.
- `CompanyScopeIntegrationTest` (MockMvc) fija el contrato multi-empresa: alta de empresa y de su ADMIN, filtrado de listados, 403 cruzado entre empresas, 409 al borrar una empresa con datos. Sus datos de partida son `static` porque JUnit instancia la clase por test y la BD H2 se comparte.
- Un DTO de request con `boolean` primitivos exige todos los flags en el JSON: Jackson no mapea un valor ausente sobre un primitivo.
- `SecurityContractIntegrationTest` (MockMvc) fija el contrato HTTP: 401 en credenciales inválidas, endpoints admin autenticados, API key, webhook de Telegram con secret, 400 en canal desconocido.
- `NotificationAdminIntegrationTest` (MockMvc) fija el historial de notificaciones: paginación, filtros combinables, detalle por ID con aislamiento por empresa, estadísticas agregadas (`/stats`) y validación de parámetros (400).
- `NotificationQueueIntegrationTest` (MockMvc, worker deshabilitado para determinismo) fija el encolado (`?async=true`), polling `GET /api/v1/notifications/{id}` por API Key, drenado por el worker y `POST /api/v1/admin/notifications/{id}/retry` (202/403/404/409).
- Unitarios sin Spring: `PasswordEncryptorTest`, `JwtServiceTest`, `ApiKeyGeneratorTest`, `EmailTemplateRendererTest`, `NotificationRequestTest`, `NotificationServiceTest`, `NotificationQueueServiceTest`, `TelegramSubscriberServiceTest`, `WhatsAppNotificationChannelTest`, `GowaClientTest`, `WhatsAppDeviceServiceTest`.
- No hay mocks a nivel HTTP de SMTP/Telegram: los canales se prueban por sus gates `isEnabled`/`isConfigured` o con dobles.

## Superficie REST
- `POST /api/v1/admin/auth/login` `{email, password}` → `{token, expiresInSeconds, role, companyId, companyCode}`. Credenciales inválidas → **401**.
- `GET/POST/PUT/DELETE /api/v1/admin/companies[/{id}]` — sólo SUPER_ADMIN. Borrar una empresa con usuarios, cuentas o plantillas → **409**.
- `GET/POST/PUT/DELETE /api/v1/admin/users[/{id}]`, `PUT /{id}/password` — sólo SUPER_ADMIN. Un ADMIN exige `companyId`; un SUPER_ADMIN no admite ninguno.
- `GET/POST/PUT/DELETE /api/v1/admin/email-accounts[/{id}]` — SUPER_ADMIN o ADMIN (cada ADMIN sólo ve las de su empresa).
- `GET/POST/PUT/DELETE /api/v1/admin/email-templates[/{id}]` — igual, por empresa. Placeholders `{{variable}}`.
- `GET/POST/PUT/DELETE /api/v1/admin/telegram-bot-accounts[/{id}]` — igual, por empresa.
- `GET/POST/PUT/DELETE /api/v1/admin/whatsapp-devices[/{id}]`, `GET /{id}/qr`, `POST /{id}/pair`, `GET /{id}/status`, `POST /{id}/reconnect`, `POST /{id}/logout`, `POST /{id}/unregister` — igual, por empresa (dispositivos GOWA).
- `GET /api/v1/admin/notifications` (paginado; filtros `companyId`, `channel`, `status`, `recipient`, `clientCode`, `from`, `until`), `GET /api/v1/admin/notifications/{id}` (403 cruzado, 404 si no existe), `GET /api/v1/admin/notifications/stats` (`?companyId=&from=&until=`), `POST /api/v1/admin/notifications/{id}/retry` (reencola `FAILED` con payload; 403/404/409) — SUPER_ADMIN o ADMIN (acotado a su empresa).
- `GET/POST /api/v1/admin/api-clients` (`?companyId=` filtra), `POST /{id}/rotate-key` (clave en claro sólo una vez), `DELETE /{id}` (204 sin body) — SUPER_ADMIN o el ADMIN de la empresa, que gestiona sólo las suyas. Toda clave nace con empresa: el ADMIN usa la suya, el SUPER_ADMIN manda `companyId` (si no, 400). El nombre es único por empresa.
- `POST /api/v1/notifications[?async=true]`, `POST /api/v1/notifications/bulk[?async=true]`, `GET /api/v1/notifications/{id}`, `GET /api/v1/notifications/channels` — requieren `X-Api-Key`.
- `POST /api/v1/telegram/webhook` — público para Telegram, autenticado por el header `X-Telegram-Bot-Api-Secret-Token`.
- `/api/v1/telegram/webhook-admin` y `/api/v1/telegram/subscribers` — SUPER_ADMIN o ADMIN.

## Modelo de auth
- `AdminUser` (BCrypt, rol SUPER_ADMIN/ADMIN). Se siembra desde `ADMIN_EMAIL`/`ADMIN_PASSWORD` si la tabla está vacía.
- El ámbito lo da la empresa, no un tercer rol: SUPER_ADMIN va sin `company` y lo ve todo; ADMIN pertenece a una empresa y sólo administra sus cuentas, bots y plantillas. Un ADMIN sin empresa queda bloqueado (403) en vez de heredar acceso global.
- `CompanyScope` centraliza el filtro: `filterCompanyId()` en listados, `assertCanAccess()` en lecturas/envíos y `resolveOwner()` en escrituras. Lee el ámbito del principal que haya: `AuthPrincipal` (JWT) o la entidad `ApiClient` (API key). Sin principal (arranque, webhook entrante de Telegram) no restringe nada.
- Un `clientCode` de otra empresa sale como **403**: los canales relanzan `AccessDeniedException` en vez de convertirla en `FAILED` (un 502 culparía al proveedor), y `sendBulk` la captura por elemento para no tumbar el lote.
- `isDefault` de una cuenta o un bot es **global** (es lo que resuelve un envío sin `clientCode`): sólo lo marca el SUPER_ADMIN.
- Desactivar la empresa deja fuera del login a sus administradores sin tocar cada usuario.
- `ApiClient`: clave `jmk_<43 url-safe>`, hash SHA-256; el valor en claro sólo se devuelve al crear o rotar. Pertenece a una empresa y sólo puede enviar por sus cuentas: un `clientCode` ajeno da **403**, y sin `clientCode` se resuelve la cuenta por defecto **de esa empresa**. Las claves anteriores a las empresas (sin `company_id`) siguen sin acotar y se avisan con WARN al arrancar. WhatsApp también se acota: cada dispositivo GOWA pertenece a una empresa.
- JWT HS256 firmado con `JWT_SECRET`. Claims: `sub`, `email`, `role` y `cid` (id de empresa, ausente para SUPER_ADMIN).
- `SecurityConfig` termina en `anyRequest().authenticated()`: un endpoint nuevo nace cerrado. Públicos: health/info, swagger, `/error` y `POST /api/v1/telegram/webhook`.
- La consola H2 tiene su propia cadena `@Order(1)`, activa sólo si `spring.h2.console.enabled=true`.

## Convenciones
- Sin CI, sin pre-commit, sin formatter — mantenlo así salvo que se pida.
- Secretos sólo en `.env`. Nunca loguear tokens de bot ni passwords SMTP.
- Los DTOs de request hacen su propio mapeo (`applyTo(entity)`, `toDomain()`); los de response, `from(entity)`.
- Los errores salen por `GlobalExceptionHandler` → `ApiError`. El catch-all de `Exception` loguea el stacktrace y devuelve un mensaje genérico: no expongas `ex.getMessage()` en un 500.
- `log.error` recibe la excepción como último argumento para conservar el stacktrace.
- Los canales nunca lanzan desde `send()`: devuelven `NotificationResult.failed(...)` o `.skipped(...)`.
