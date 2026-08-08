# Configuración de Keycloak

Todo lo que hay en esta carpeta se importa **solo** al levantar el proyecto.
No hace falta entrar a la consola web a crear nada a mano.

El `compose.yml` monta esta carpeta en `/opt/keycloak/data/import` y arranca
Keycloak con `--import-realm`, así que al iniciar lee
`realm-cowsvsclown.json` y crea el realm completo: cliente, roles y usuarios.

> **Por qué este archivo existe:** JSON no admite comentarios, así que la
> explicación de cada bloque del realm vive acá.

---

## Índice

1. [Qué crea el realm](#1-qué-crea-el-realm)
2. [El archivo, bloque por bloque](#2-el-archivo-bloque-por-bloque)
3. [Cómo pedir un token](#3-cómo-pedir-un-token)
4. [Qué hay dentro de un token](#4-qué-hay-dentro-de-un-token)
5. [Cambiar el realm](#5-cambiar-el-realm)
6. [Problemas típicos](#6-problemas-típicos)

---

## 1. Qué crea el realm

### Roles

| Rol     | Qué habilita                                            |
|---------|---------------------------------------------------------|
| `USER`  | Crear y modificar (`POST`, `PATCH`, `PUT`)               |
| `ADMIN` | Todo lo de `USER` **más** dar de baja (`DELETE`)         |

`ADMIN` es un rol **compuesto**: contiene a `USER`. Eso significa que a quien
tenga `ADMIN` no hay que darle `USER` aparte, Keycloak se lo agrega solo al
token. (En el JSON los usuarios `admin` tienen los dos listados igual, por
claridad, pero es redundante.)

### Usuarios de prueba

| Usuario   | Contraseña   | Roles          | Sirve para probar                     |
|-----------|--------------|----------------|---------------------------------------|
| `admin`   | `admin123`   | `ADMIN`, `USER`| Que todo funcione                     |
| `user`    | `user123`    | `USER`         | Que un `DELETE` devuelva **403**      |
| `curioso` | `curioso123` | *(ninguno)*    | Que hasta un `POST` devuelva **403**  |

`curioso` es el caso interesante: su token es perfectamente válido, así que
**no** da 401. Da 403, que es la diferencia entre "no sé quién sos" y "sé quién
sos y no podés".

### Cliente

Uno solo, `cowsvsclown-api`, **público** (sin *client secret*). Lo usan tanto
Swagger UI como los `curl` de prueba.

---

## 2. El archivo, bloque por bloque

### Ajustes generales del realm

```json
{
  "sslRequired": "none"
}
```
Permite entrar por HTTP. Por defecto Keycloak exige HTTPS salvo desde
localhost, y eso rompería la llamada de la API a `http://keycloak:8080/...`.
**En producción esto va en `external` o `all`**: un token viaja en claro y
quien lo intercepte puede usarlo hasta que venza.

```json
{
  "registrationAllowed": false,
  "resetPasswordAllowed": false
}
```
Sin auto-registro ni "olvidé mi contraseña". Los usuarios de este proyecto son
los tres del archivo y nadie más.

```json
{
  "bruteForceProtected": true,
  "failureFactor": 10
}
```
Después de 10 contraseñas erradas seguidas, Keycloak bloquea temporalmente esa
cuenta. Es lo que evita que alguien pruebe contraseñas a lo bruto contra el
endpoint de token.

```json
{
  "accessTokenLifespan": 300
}
```
El token de acceso dura **5 minutos**. Parece poco, y es a propósito: la API
valida los tokens *sin preguntarle nada a Keycloak*, así que si deshabilitás un
usuario, su token sigue sirviendo hasta que venza. Cuanto más corto, más chica
esa ventana. Para seguir trabajando se usa el `refresh_token`, que dura más.

### El cliente

```json
{
  "publicClient": true
}
```
Sin contraseña de cliente. Es lo correcto para algo que corre **en el
navegador** (Swagger, una SPA de React): cualquier secreto que le pongas queda
a la vista en el código que baja el navegador, así que no sería secreto. La
seguridad la aporta PKCE, no un secreto.

```json
{
  "standardFlowEnabled": true
}
```
Habilita *Authorization Code*, el flujo que usa el botón **Authorize** de
Swagger: te redirige a la pantalla de login de Keycloak y vuelve con el token.

```json
{
  "directAccessGrantsEnabled": true
}
```
Habilita el *password grant*: pedir el token mandando usuario y contraseña
directo al endpoint `/token`, sin navegador. Es lo que hace posible el `curl`
de la sección 3.

> Este flujo está **desaconsejado** fuera de desarrollo (OAuth 2.1 lo elimina),
> porque obliga a que el cliente vea la contraseña del usuario. Acá está
> encendido porque hace mucho más fácil probar la API desde la terminal.

```json
{
  "attributes": { "pkce.code.challenge.method": "S256" }
}
```
Hace PKCE **obligatorio** en el flujo del navegador. El cliente inventa un
secreto de un solo uso, manda su hash SHA-256 al pedir el código y el original
al canjearlo; sin el original, un código robado de la URL no sirve de nada.

Esto tiene que estar en sintonía con
`springdoc.swagger-ui.oauth.use-pkce-with-authorization-code-grant: true` en
`application.yml`. Si acá se exige y allá no se manda, Keycloak responde
`Missing parameter: code_challenge`.

```json
{
  "redirectUris": ["http://localhost:8080/*", "http://127.0.0.1:8080/*"]
}
```
Las únicas direcciones a las que Keycloak acepta devolver un token. Es lo que
impide que un sitio ajeno inicie el login y se quede con el resultado. La que
usa Swagger es `http://localhost:8080/swagger-ui/oauth2-redirect.html`, cubierta
por el comodín.

```json
{
  "webOrigins": ["http://localhost:8080"]
}
```
CORS. Swagger corre en `localhost:8080` y tiene que hacer una petición AJAX a
Keycloak en `localhost:8081` para canjear el código por el token; sin este
permiso, el navegador la bloquea.

```json
{
  "protocolMappers": [{ "protocolMapper": "oidc-audience-mapper" }]
}
```
Agrega `"aud": "cowsvsclown-api"` al token: para quién fue emitido. Hoy la API
no lo comprueba (está comentado en `application.yml`), pero el mapper ya está
para poder activarlo con una línea. Sirve cuando un mismo realm atiende varias
APIs y no querés que un token de una valga en la otra.

---

## 3. Cómo pedir un token

### Desde Swagger (lo cómodo)

<http://localhost:8080/swagger-ui.html> → botón **Authorize** 🔓 → login con
`admin` / `admin123`. Swagger guarda el token y lo manda en cada petición.

### Desde la terminal

```bash
curl -s -X POST \
  "http://localhost:8081/realms/cowsvsclown/protocol/openid-connect/token" \
  -d "client_id=cowsvsclown-api" \
  -d "username=admin" \
  -d "password=admin123" \
  -d "grant_type=password"
```

La respuesta trae `access_token`, `refresh_token` y `expires_in`. Para usarlo
directamente en una llamada:

```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8081/realms/cowsvsclown/protocol/openid-connect/token" \
  -d "client_id=cowsvsclown-api" -d "username=admin" \
  -d "password=admin123" -d "grant_type=password" | jq -r .access_token)

curl -X POST http://localhost:8080/api/owners \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Ada","lastName":"Lovelace"}'
```

---

## 4. Qué hay dentro de un token

Un JWT son tres partes separadas por puntos: `cabecera.contenido.firma`, cada
una en Base64. **No está cifrado**: cualquiera puede leerlo. Lo que no puede
es modificarlo sin romper la firma.

Para verlo:

```bash
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq
```

o pegándolo en <https://jwt.io>.

```jsonc
{
  "iss": "http://localhost:8081/realms/cowsvsclown",  // quién lo emitió
  "aud": ["cowsvsclown-api", "account"],              // para quién es
  "exp": 1735689600,                                  // cuándo vence
  "sub": "8f14e45f-ceea-...",                         // id interno del usuario
  "preferred_username": "admin",                      // el nombre legible
  "realm_access": {
    "roles": ["ADMIN", "USER"]                        // <-- lo que lee la API
  },
  "resource_access": {
    "cowsvsclown-api": { "roles": [] }
  },
  "scope": "openid profile email"
}
```

De todo eso, la API usa:

- `iss` y la firma → para decidir si el token es de fiar
- `exp` → para rechazarlo si venció
- `realm_access.roles` → para saber qué puede hacer
  (`common/security/KeycloakRoleConverter.java`)
- `preferred_username` → para que los logs digan `admin` y no un UUID

---

## 5. Cambiar el realm

Los cambios hechos por la consola web **no se guardan acá**: viven en la base
H2 del contenedor y desaparecen con el siguiente `docker compose down`.

Para que un cambio quede versionado hay dos caminos:

**a) Editar el JSON a mano** (para cambios chicos: un usuario, un rol):

```bash
# editar keycloak/realm-cowsvsclown.json, y después:
docker compose down
docker compose up -d
```

Con eso alcanza, y **no** hace falta `-v`: en modo `start-dev` Keycloak guarda
todo en una base H2 que vive dentro del propio contenedor, no en un volumen. Al
recrear el contenedor esa base nace vacía y el realm se importa otra vez desde
el JSON.

Ese mismo detalle tiene una consecuencia que conviene tener presente: **todo lo
que hagas por la consola web se pierde en el siguiente `down`**. Un usuario
nuevo, un rol, un cambio de contraseña: si no está en el JSON, no sobrevive.

> Lo que sí es un volumen es la base de Postgres, así que `down` conserva las
> vacas y los payasos. `down -v` sí los borra, pero para el realm no aporta nada.

Con `docker compose restart keycloak` o `stop` + `start` el contenedor **no** se
recrea, así que la base sigue ahí y el import se saltea. En los logs se ve
`Realm 'cowsvsclown' already exists`. Si aparece eso, va `down` y `up`.

**b) Exportar desde el contenedor** (para cambios grandes hechos por la web):

```bash
docker compose exec keycloak /opt/keycloak/bin/kc.sh export \
  --realm cowsvsclown --users realm_file \
  --file /tmp/realm.json

docker compose cp keycloak:/tmp/realm.json ./keycloak/realm-cowsvsclown.json
```

El export sale enorme (unas 2000 líneas, con todo lo que Keycloak trae por
defecto) y sin `--users realm_file` no incluye los usuarios.

---

## 6. Problemas típicos

| Síntoma | Causa | Solución |
|---|---|---|
| `401` con un token recién pedido | `iss` no coincide con `issuer-uri` | Ver que `KEYCLOAK_PORT` del `.env` sea el mismo con el que pediste el token |
| `401` en todo, sin haber tocado nada | El token venció (dura 5 min) | Pedir otro |
| `403` en `DELETE` con `user` | Es lo esperado: falta `ADMIN` | Usar `admin` |
| `Connection refused` en los logs de la app | `jwk-set-uri` apunta a `localhost` desde el contenedor | Tiene que ser `http://keycloak:8080/...` |
| Los cambios del JSON no se aplican | El contenedor no se recreó (`restart` en vez de `down`) | `docker compose down && docker compose up -d` |
| `Missing parameter: code_challenge` | PKCE exigido y Swagger sin configurar | `use-pkce-with-authorization-code-grant: true` en `application.yml` |
| No entro a la consola | Variables viejas `KEYCLOAK_ADMIN` | Desde la v26 son `KC_BOOTSTRAP_ADMIN_*` |