# jm-notification

Servicio de notificaciones multi-canal con **Java 25** y **Spring Boot 4.1.1**.

Canales soportados:

- **Email** (SMTP / Spring Mail)
- **WhatsApp** (Meta Cloud API)
- **Telegram** (Bot API + suscriptores activos)

## Requisitos

- JDK 25+
- Maven 3.9+

## Configuración (archivo `.env`)

Las variables viven en **`.env`** (cargado automáticamente con `spring-dotenv`).

```text
cp .env.example .env   # o copia manual en Windows
# edita .env con tus secretos
```

- `.env` — valores locales (**no se versiona**)
- `.env.example` — plantilla sin secretos (**sí se versiona**)

| Variable | Descripción | Default |
|---|---|---|
| `SERVER_PORT` | Puerto HTTP | `8050` |
| `DB_URL` | JDBC URL (el driver se deriva de ella) | `jdbc:postgresql://localhost:5433/jm_notification` |
| `DB_USER` | Usuario BD | `postgres` |
| `DB_PASSWORD` | Password BD | `postgres` |
| `JPA_DDL_AUTO` | Estrategia de esquema JPA | `update` |
| `H2_CONSOLE_ENABLED` | Consola H2 en `/h2-console` | `false` |
| `HTTP_CONNECT_TIMEOUT` | Timeout de conexión saliente | `5s` |
| `HTTP_READ_TIMEOUT` | Timeout de lectura saliente | `15s` |
| `MAIL_FROM` | Remitente por defecto | vacío |
| `EMAIL_PASSWORD_ENCRYPTION_KEY` | Clave AES Base64 (16/24/32 bytes) para cifrar passwords SMTP | aleatoria por arranque |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Semilla del primer SUPER_ADMIN | |
| `JWT_SECRET` | Secreto HS256, **mínimo 32 caracteres** | aleatorio por arranque |
| `JWT_TTL` | Duración del JWT | `12h` |
| `NOTIFICATION_EMAIL_ENABLED` | Habilitar email | `true` |
| `NOTIFICATION_WHATSAPP_ENABLED` | Habilitar WhatsApp | `false` |
| `WHATSAPP_API_URL` | Base URL Meta Graph | `https://graph.facebook.com/v21.0` |
| `WHATSAPP_PHONE_NUMBER_ID` | Phone Number ID | |
| `WHATSAPP_ACCESS_TOKEN` | Access token | |
| `NOTIFICATION_TELEGRAM_ENABLED` | Habilitar Telegram | `false` |
| `TELEGRAM_API_URL` | Base URL Telegram (fallback global) | `https://api.telegram.org` |
| `TELEGRAM_WEBHOOK_SECRET` | Secret del webhook global (fallback) | |
| `TELEGRAM_WEBHOOK_URL` | Base HTTPS pública o URL completa del webhook (fallback) | |
| `TELEGRAM_WEBHOOK_AUTO_REGISTER` | Registrar webhook al arrancar (fallback) | `false` |
| `TELEGRAM_REQUIRE_ACTIVE_SUBSCRIBER` | Solo enviar a suscriptores `/start` (fallback) | `true` |
| `TELEGRAM_WELCOME_MESSAGE` | Mensaje tras `/start` (fallback) | (ver `.env.example`) |
| `TELEGRAM_GOODBYE_MESSAGE` | Mensaje tras `/stop` (fallback) | (ver `.env.example`) |

> **Nota:** `TELEGRAM_BOT_TOKEN` ya no se usa desde `.env`. Los bots se gestionan en **Base de Datos** vía `/api/v1/admin/telegram-bot-accounts`. Las variables `TELEGRAM_*` son fallback global para compatibilidad.

> **Nota:** las cuentas SMTP también viven en Base de Datos (`/api/v1/admin/email-accounts`); `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME` y `MAIL_PASSWORD` ya no se leen.

> **Importante:** deja fijas `EMAIL_PASSWORD_ENCRYPTION_KEY` y `JWT_SECRET` en cualquier entorno persistente. Si están vacías se genera una clave aleatoria en cada arranque: los passwords SMTP guardados dejan de poder descifrarse y los JWT emitidos caducan al reiniciar.

## Ejecutar

```powershell
$env:JAVA_HOME="C:\Users\jhonb\.jdks\corretto-25.0.1"
# Configura secretos en .env y luego:
.\mvnw.cmd spring-boot:run
```

## Swagger

- UI: http://localhost:8050/swagger-ui.html
- OpenAPI: http://localhost:8050/v3/api-docs

Swagger declara los dos esquemas de autenticación (`adminJwt` con Bearer y `apiKey` con
`X-Api-Key`), así que se puede autenticar y probar los endpoints desde la propia UI.

## Configuración de Correo Dinámica (Base de Datos)

El sistema soporta múltiples cuentas SMTP por cliente almacenadas en Base de Datos.

### 1. Registrar cuenta de correo para un cliente

```http
POST http://localhost:8050/api/v1/admin/email-accounts
Content-Type: application/json

{
  "clientCode": "cliente-a",
  "name": "SMTP Cliente A",
  "host": "smtp.mailgun.org",
  "port": 587,
  "username": "postmaster@mg.clientea.com",
  "password": "secret-password",
  "fromAddress": "noreply@clientea.com",
  "fromName": "Cliente A Soporte",
  "auth": true,
  "starttlsEnable": true,
  "active": true,
  "isDefault": false
}
```

Otros endpoints (todos bajo `/api/v1/admin`, sólo SUPER_ADMIN):
- `GET /api/v1/admin/email-accounts`
- `GET /api/v1/admin/email-accounts/{id}`
- `PUT /api/v1/admin/email-accounts/{id}`
- `DELETE /api/v1/admin/email-accounts/{id}`

### 2. Enviar correo indicando el cliente en la petición

En el JSON de envío de notificación, incluye `clientCode`:

```http
POST http://localhost:8050/api/v1/notifications
Content-Type: application/json

{
  "channel": "EMAIL",
  "to": "destinatario@example.com",
  "subject": "Factura #1234",
  "message": "<p>Hola, adjunto tu factura.</p>",
  "clientCode": "cliente-a"
}
```

Si no envías `clientCode`, el sistema usará la cuenta marcada como `isDefault = true` en la base de datos (o la primera activa si no hay default). No se crea ninguna cuenta automáticamente: si no hay ninguna, el arranque lo avisa por log y el envío falla indicando que falta configurarla.

## Configuración de Bots de Telegram (Base de Datos)

El sistema soporta **múltiples bots de Telegram por cliente** almacenados en Base de Datos.

### 1. Registrar cuenta de bot para un cliente

```http
POST http://localhost:8050/api/v1/admin/telegram-bot-accounts
Content-Type: application/json

{
  "clientCode": "cliente-a",
  "name": "Bot Cliente A",
  "botToken": "123456789:AAHxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "apiUrl": "https://api.telegram.org",
  "webhookSecret": "secret-para-cliente-a",
  "webhookUrl": "https://tunel-cliente-a.loca.lt",
  "webhookAutoRegister": false,
  "requireActiveSubscriber": true,
  "welcomeMessage": "Bienvenido al bot de Cliente A",
  "goodbyeMessage": "Has salido del bot de Cliente A",
  "active": true,
  "isDefault": false
}
```

Otros endpoints:
- `GET /api/v1/admin/telegram-bot-accounts`
- `GET /api/v1/admin/telegram-bot-accounts/{id}`
- `PUT /api/v1/admin/telegram-bot-accounts/{id}`
- `DELETE /api/v1/admin/telegram-bot-accounts/{id}`

### 2. Registrar webhook por cuenta de bot

```http
POST http://localhost:8050/api/v1/telegram/webhook-admin
Content-Type: application/json

{
  "publicBaseUrl": "https://tunel-cliente-a.loca.lt",
  "clientCode": "cliente-a"
}
```

Otros (requieren `clientCode` como query param):
- `GET /api/v1/telegram/webhook-admin?clientCode=cliente-a` — estado
- `GET /api/v1/telegram/webhook-admin/bot?clientCode=cliente-a` — `getMe`
- `DELETE /api/v1/telegram/webhook-admin?clientCode=cliente-a` — borrar webhook

Auto-registro al arrancar (configurado en la cuenta del bot):
```json
{
  "webhookAutoRegister": true,
  "webhookUrl": "https://tunel-cliente-a.loca.lt"
}
```

### 3. Enviar notificación por Telegram indicando el bot

En el JSON de envío, incluye `clientCode` en `metadata`:

```http
POST http://localhost:8050/api/v1/notifications
Content-Type: application/json

{
  "channel": "TELEGRAM",
  "to": "user-99",
  "subject": "Alerta",
  "message": "Tu pedido fue enviado",
  "metadata": { "clientCode": "cliente-a" }
}
```

- `to` puede ser `chat_id` o `externalUserId` vinculado
- Si no envías `clientCode`, se usa la cuenta marcada `isDefault = true` (o la primera activa)

## Telegram: suscriptores

Un bot **solo** puede escribir a quien abrió el chat y envió `/start`.

### Flujo

1. Usuario abre el bot y envía `/start` (o deep link `https://t.me/TuBot?start=user-99`)
2. Telegram llama al webhook `POST /api/v1/telegram/webhook`
3. Se guarda `chat_id` como suscriptor **activo**
4. Tus sistemas envían notificaciones con ese `chat_id` o con `externalUserId` (`user-99`)
5. `/stop` o bloqueo del bot → `active=false`

### Registrar webhook (por código)

Con la app corriendo y un túnel HTTPS:

```http
POST http://localhost:8050/api/v1/telegram/webhook-admin
Content-Type: application/json

{
  "publicBaseUrl": "https://open-shirts-see.loca.lt"
}
```

Otros:

- `GET /api/v1/telegram/webhook-admin` — estado (`getWebhookInfo`)
- `GET /api/v1/telegram/webhook-admin/bot` — `getMe`
- `DELETE /api/v1/telegram/webhook-admin` — borrar webhook

Auto-registro al arrancar: se configura en la cuenta del bot (`webhookAutoRegister` +
`webhookUrl`) y requiere `NOTIFICATION_TELEGRAM_ENABLED=true`.

> **Seguridad:** en cuanto una cuenta activa define `webhookSecret`, el endpoint
> `POST /api/v1/telegram/webhook` responde `401` a cualquier petición cuyo header
> `X-Telegram-Bot-Api-Secret-Token` falte o no coincida.

### Endpoints de suscriptores

- `GET /api/v1/telegram/subscribers` — activos (`?activeOnly=false` para todos)
- `GET /api/v1/telegram/subscribers/{chatId}`
- `PUT /api/v1/telegram/subscribers/link` — vincular id de tu sistema

```json
{
  "chatId": 123456789,
  "externalUserId": "user-99"
}
```

### Enviar por Telegram

`to` puede ser:

- `chat_id` de un suscriptor activo
- `externalUserId` vinculado (ej. `user-99`)

```json
{
  "channel": "TELEGRAM",
  "to": "user-99",
  "subject": "Alerta",
  "message": "Tu pedido fue enviado"
}
```

## API notificaciones

### Enviar

`POST /api/v1/notifications`

**Con adjuntos (base64):**

```json
{
  "channel": "EMAIL",
  "to": "destinatario@example.com",
  "subject": "Factura #1234",
  "message": "<p>Hola, adjunto tu factura.</p>",
  "clientCode": "cliente-a",
  "attachments": [
    {
      "name": "factura-1234.pdf",
      "contentType": "application/pdf",
      "base64Content": "JVBERi0xLjQKJcfsj6IKNSAwIG9iagooW1..."
    }
  ]
}
```

Los adjuntos se envían como **base64** en el JSON. Canales soportados:
- **EMAIL**: Múltiples adjuntos (todos se agregan al correo)
- **TELEGRAM**: Un adjunto (primer elemento, vía `sendDocument`)
- **WHATSAPP**: No soportado aún (solo texto)

### Bulk

`POST /api/v1/notifications/bulk`

### Canales

`GET /api/v1/notifications/channels`

### Health

`GET /actuator/health`

## Estructura

```
com.jmcode.notification
├── channel/     # Email / WhatsApp / Telegram
├── config/
├── domain/
├── service/
├── telegram/    # Suscriptores, webhook payload, bot client, bot accounts
└── web/
```

Datos locales H2: carpeta `./data/` (consola en `/h2-console`).

## Autenticación API (API Clients)

Las notificaciones requieren autenticación mediante **API Key** (header `X-Api-Key`).

### Crear API Client (solo SUPER_ADMIN)

```http
POST http://localhost:8050/api/v1/admin/api-clients
Content-Type: application/json
Authorization: Bearer <jwt-admin-token>

{
  "name": "Mi Aplicación",
  "contactEmail": "dev@miapp.com"
}
```

**Respuesta** (la key completa **solo se muestra una vez**):

```json
{
  "id": 1,
  "name": "Mi Aplicación",
  "contactEmail": "dev@miapp.com",
  "apiKeyPrefix": "jmk_abc123...",
  "apiKey": "jmk_abc123def456ghi789jkl012mno345pqr678stu901"
}
```

> **Guarda la `apiKey` completa**. No se vuelve a mostrar.

### Usar API Client en notificaciones

```bash
curl -X POST http://localhost:8050/api/v1/notifications \
  -H "X-Api-Key: jmk_abc123def456ghi789jkl012mno345pqr678stu901" \
  -H "Content-Type: application/json" \
  -d '{
    "channel": "TELEGRAM",
    "to": "user-99",
    "subject": "Alerta",
    "message": "Tu pedido fue enviado",
    "metadata": { "clientCode": "cliente-a" }
  }'
```

### Gestionar API Clients

| Método | Endpoint | Descripción |
|---|---|---|
| GET | `/api/v1/admin/api-clients` | Listar |
| POST | `/api/v1/admin/api-clients` | Crear (devuelve key una vez) |
| POST | `/api/v1/admin/api-clients/{id}/rotate-key` | Rotar key (devuelve nueva) |
| DELETE | `/api/v1/admin/api-clients/{id}` | Eliminar |
