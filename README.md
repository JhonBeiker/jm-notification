# jm-notification

Servicio de notificaciones multi-canal con **Java 25** y **Spring Boot 4.1.1**.

Canales soportados:

- **Email** (SMTP / Spring Mail)
- **WhatsApp** (instancia [GOWA](https://github.com/aldinokemal/go-whatsapp-web-multidevice), dispositivo por cliente)
- **Telegram** (Bot API + suscriptores activos)

## Requisitos

- JDK 25+
- Maven 3.9+

## Configuración (archivo `.env`)

Las variables viven en **`.env`**, que carga `spring.config.import: optional:file:.env[.properties]` en `application.yml`. Se parsea como un `.properties`, así que el `\` escapa: evita barras invertidas en los valores.

```text
cp .env.example .env   # o copia manual en Windows
# edita .env con tus secretos
```

- `.env` — valores locales (**no se versiona**)
- `.env.example` — plantilla sin secretos (**sí se versiona**)

| Variable | Descripción | Default |
|---|---|---|
| `SERVER_PORT` | Puerto HTTP | `8050` |
| `DB_URL` | JDBC URL (el driver se deriva de ella) | **obligatoria** |
| `DB_USER` | Usuario BD | **obligatoria** |
| `DB_PASSWORD` | Password BD | **obligatoria** |
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
| `GOWA_API_URL` | Base URL de la instancia GOWA (fallback global) | |
| `GOWA_BASIC_AUTH_USER` | Usuario de Basic Auth de GOWA (`APP_BASIC_AUTH`) | |
| `GOWA_BASIC_AUTH_PASSWORD` | Password de Basic Auth de GOWA | |
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

## Empresas y administradores

Cada empresa cliente es una fila en `companies`. Las cuentas SMTP, los bots de Telegram y las
plantillas de correo cuelgan de ella, y cada empresa tiene su propio usuario administrador, que es
quien carga esos datos.

- **SUPER_ADMIN** (el que se siembra con `ADMIN_EMAIL` / `ADMIN_PASSWORD`): crea empresas y sus
  administradores, y ve todas las filas.
- **ADMIN**: pertenece a una empresa y sólo ve y edita las cuentas, bots y plantillas de la suya.
  Intentar leer o asignar datos de otra empresa devuelve **403**.

### 1. Crear la empresa (SUPER_ADMIN)

```http
POST http://localhost:8050/api/v1/admin/companies
Content-Type: application/json
Authorization: Bearer <jwt-super-admin>

{
  "code": "cliente-a",
  "name": "Cliente A S.A.S.",
  "taxId": "900123456-7",
  "contactEmail": "soporte@clientea.com",
  "active": true
}
```

Borrar una empresa que todavía tiene usuarios, cuentas o plantillas devuelve **409** con el detalle
de lo que falta desvincular. Desactivarla (`active: false`) deja fuera del login a sus administradores.

### 2. Crear el administrador de la empresa (SUPER_ADMIN)

```http
POST http://localhost:8050/api/v1/admin/users
Content-Type: application/json
Authorization: Bearer <jwt-super-admin>

{
  "email": "admin@clientea.com",
  "password": "una-password-larga",
  "role": "ADMIN",
  "companyId": 1
}
```

`role: "ADMIN"` exige `companyId`; `role: "SUPER_ADMIN"` no admite ninguno. Otros endpoints:
`GET /api/v1/admin/users?companyId=1`, `PUT /{id}` (rol, empresa y estado),
`PUT /{id}/password` y `DELETE /{id}`.

### 3. El administrador entra y carga sus datos

`POST /api/v1/admin/auth/login` devuelve `{token, expiresInSeconds, role, companyId, companyCode}`.
Con ese token, el administrador de la empresa crea sus cuentas SMTP, sus bots de Telegram y sus
plantillas **sin enviar `companyId`**: se asigna sola la suya.

| Método | Endpoint | Quién |
|---|---|---|
| GET/POST/PUT/DELETE | `/api/v1/admin/companies[/{id}]` | SUPER_ADMIN |
| GET/POST/PUT/DELETE | `/api/v1/admin/users[/{id}]` | SUPER_ADMIN |
| GET/POST/PUT/DELETE | `/api/v1/admin/email-accounts[/{id}]` | SUPER_ADMIN o el ADMIN de la empresa |
| GET/POST/PUT/DELETE | `/api/v1/admin/email-templates[/{id}]` | SUPER_ADMIN o el ADMIN de la empresa |
| GET/POST/PUT/DELETE | `/api/v1/admin/telegram-bot-accounts[/{id}]` | SUPER_ADMIN o el ADMIN de la empresa |

> `isDefault` marca la cuenta o el bot que se usa cuando el envío no trae `clientCode`: es global,
> así que sólo lo puede marcar un SUPER_ADMIN.

## Plantillas de correo

```http
POST http://localhost:8050/api/v1/admin/email-templates
Content-Type: application/json
Authorization: Bearer <jwt-admin-empresa>

{
  "name": "bienvenida",
  "subject": "Hola {{nombre}}",
  "content": "<p>Hola {{nombre}}, tu pedido {{pedido}} va en camino.</p>",
  "contentType": "html",
  "variables": "[\"nombre\",\"pedido\"]",
  "active": true
}
```

El nombre es único dentro de la empresa. Si se omite `contentType` se deduce del contenido. En el
envío se referencia con `templateName` y los valores van en `variables`; un placeholder sin valor se
deja tal cual en vez de imprimir `null`.

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

Un SUPER_ADMIN debe añadir `"companyId": 1` para indicar de qué empresa es la cuenta; el
administrador de una empresa no lo envía (se asigna la suya).

Otros endpoints (todos bajo `/api/v1/admin`, SUPER_ADMIN o el ADMIN de la empresa):
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

## WhatsApp con GOWA (dispositivos en base de datos)

WhatsApp se envía a través de una instancia propia de
[go-whatsapp-web-multidevice](https://github.com/aldinokemal/go-whatsapp-web-multidevice) (GOWA):
cada cliente tiene su **dispositivo** (una cuenta de WhatsApp emparejada), igual que cada cliente
tiene su bot de Telegram. La fila guarda a qué `deviceId` de GOWA apunta cada `clientCode`; el
emparejamiento vive en GOWA.

`GOWA_API_URL`, `GOWA_BASIC_AUTH_USER` y `GOWA_BASIC_AUTH_PASSWORD` son la instancia por defecto.
Un dispositivo puede traer su propia `apiUrl`/`basicAuthUser`/`basicAuthPassword` (la password se
guarda cifrada con AES-GCM, igual que los passwords SMTP).

### 1. Crear el dispositivo (ADMIN de la empresa)

```bash
curl -X POST http://localhost:8050/api/v1/admin/whatsapp-devices   -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -d '{
    "clientCode": "acme",
    "name": "ACME Ventas",
    "active": true,
    "isDefault": false
  }'
```

El alta da de alta también el dispositivo en GOWA y guarda el `deviceId` que este devuelve (un UUID
que elige GOWA). Si GOWA no responde, el alta se deshace y devuelve **502**.

**Adoptar un dispositivo que ya existe en GOWA** (por ejemplo uno ya emparejado): manda su id en
`deviceId` y no se crea ninguno nuevo.

```bash
curl -X POST http://localhost:8050/api/v1/admin/whatsapp-devices \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{ "clientCode": "jmcode-main", "deviceId": "prueba", "active": true, "isDefault": false }'
```

```json
{ "clientCode": "jmcode-main", "deviceId": "prueba", "status": "logged_in",
  "phoneNumber": "584263073306" }
```

Si ya estaba emparejado entra directamente como `logged_in` y no hace falta QR. Un `deviceId` que
GOWA no conoce da **404**, y uno ya enlazado a otro `clientCode` da **400** (dos clientes sobre el
mismo dispositivo se pisarían los mensajes).

### 2a. Emparejar por QR

```bash
curl http://localhost:8050/api/v1/admin/whatsapp-devices/1/qr -H "Authorization: Bearer $TOKEN"
```

```json
{
  "deviceId": "c9bf3acf-1096-4b46-aeae-06e578fa05d4",
  "qrLink": "http://tu-gowa/statics/qrcode/scan-qr-xxxx.png",
  "qrDuration": 30
}
```

`qrLink` es un PNG que sirve la propia instancia GOWA **sin Basic Auth**, así que vale directamente
en un `<img src="...">`. Caduca en `qrDuration` segundos: se pide justo antes de pintarlo y se
vuelve a pedir si expira.

### 2b. Emparejar por código (sin escanear)

```bash
curl -X POST http://localhost:8050/api/v1/admin/whatsapp-devices/1/pair   -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json"   -d '{ "phoneNumber": "+584263073306" }'
```

```json
{ "deviceId": "c9bf3acf-...", "pairCode": "DLSR-R19N" }
```

Ese código se teclea en el móvil: *WhatsApp → Dispositivos vinculados → Vincular con número*.

### 3. Comprobar el emparejamiento

```bash
curl http://localhost:8050/api/v1/admin/whatsapp-devices/1/status -H "Authorization: Bearer $TOKEN"
```

```json
{ "deviceId": "c9bf3acf-...", "state": "logged_in", "loggedIn": true,
  "jid": "584263073306@s.whatsapp.net", "phoneNumber": "584263073306", "displayName": "Jhon Moran" }
```

`loggedIn: true` es la condición para poder enviar. Mientras no lo esté, GOWA responde a los envíos
con `401 you are not logged in`, que llega como **502** con ese mismo detalle.

### 4. Enviar

`to` es un número internacional (con o sin `+`, se normaliza) o un JID completo
(`...@s.whatsapp.net`, `...@g.us` para grupos).

```json
{
  "channel": "WHATSAPP",
  "to": "+584263073306",
  "clientCode": "acme",
  "subject": "Alerta",
  "message": "Tu pedido fue enviado"
}
```

Sin `clientCode` se usa el dispositivo por defecto de la empresa de la API Key. Un `clientCode` de
otra empresa devuelve **403**; un fallo de la instancia GOWA devuelve **502**.

### Resto de endpoints

| Método | Ruta | Qué hace |
|--------|------|----------|
| `GET` | `/api/v1/admin/whatsapp-devices` | Listar (el ADMIN sólo ve los de su empresa) |
| `PUT` | `/api/v1/admin/whatsapp-devices/{id}` | Actualizar (password vacía = conserva la guardada) |
| `POST` | `/api/v1/admin/whatsapp-devices/{id}/reconnect` | Reabrir la sesión de un dispositivo ya emparejado |
| `POST` | `/api/v1/admin/whatsapp-devices/{id}/logout` | Cerrar sesión de WhatsApp (hay que reemparejar) |
| `POST` | `/api/v1/admin/whatsapp-devices/{id}/unregister` | Borrar el dispositivo en GOWA |
| `DELETE` | `/api/v1/admin/whatsapp-devices/{id}` | Borrar sólo la fila local |

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
- **WHATSAPP**: No soportado aún (solo texto)  <!-- GOWA sí tiene /send/file; falta conectarlo -->

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

Cada API Key **pertenece a una empresa** y sólo puede enviar por las cuentas de esa empresa:
un `clientCode` de otra empresa devuelve **403**, y si el envío no trae `clientCode` se usa la
cuenta por defecto **de su empresa**, no la global.

### Crear API Client (SUPER_ADMIN o el ADMIN de la empresa)

```http
POST http://localhost:8050/api/v1/admin/api-clients
Content-Type: application/json
Authorization: Bearer <jwt-admin-token>

{
  "name": "Mi Aplicación",
  "contactEmail": "dev@miapp.com",
  "companyId": 1
}
```

El administrador de una empresa **omite `companyId`**: la clave se crea en la suya, y sólo ve,
rota y borra las de su empresa (las de otra dan 403). El SUPER_ADMIN sí debe indicarlo; crear una
clave sin empresa devuelve 400. El nombre es único dentro de la empresa.

Las claves creadas antes de existir las empresas quedan sin `company_id` y siguen pudiendo usar
cualquier cuenta; al arrancar se avisa con un WARN cuántas hay pendientes de asignar.

**Respuesta** (la key completa **solo se muestra una vez**):

```json
{
  "client": {
    "id": 1,
    "companyId": 1,
    "companyCode": "cliente-a",
    "name": "Mi Aplicación",
    "contactEmail": "dev@miapp.com",
    "apiKeyPrefix": "jmk_abc123...",
    "active": true
  },
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
| GET | `/api/v1/admin/api-clients[?companyId=1]` | Listar (el ADMIN sólo ve las suyas) |
| POST | `/api/v1/admin/api-clients` | Crear para una empresa (devuelve key una vez) |
| POST | `/api/v1/admin/api-clients/{id}/rotate-key` | Rotar key (devuelve nueva) |
| DELETE | `/api/v1/admin/api-clients/{id}` | Eliminar |
