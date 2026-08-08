# Cows vs Clowns API

API REST de ejemplo construida con Spring Boot para practicar las dos relaciones
clásicas de JPA sobre un dominio deliberadamente absurdo: dueños que tienen vacas
y payasos que las cuidan.

* **1 a N** — un dueño (`Owner`) tiene muchas vacas (`Cow`). La llave foránea
  `owner_id` vive en la tabla `cows`.
* **N a M** — un payaso (`Clown`) cuida muchas vacas y una vaca puede ser cuidada
  por varios payasos. Se resuelve con la tabla intermedia `clown_cow`.

Ningún registro se borra de verdad: todo es baja lógica (`active = false`), salvo
las filas de `clown_cow`, que solo representan un vínculo.

---

## Tabla de contenidos

1. [Stack](#stack)
2. [Requisitos](#requisitos)
3. [Puesta en marcha](#puesta-en-marcha)
4. [Perfiles de configuración](#perfiles-de-configuración)
5. [Documentación de la API](#documentación-de-la-api)
6. [Seguridad y login con Keycloak](#seguridad-y-login-con-keycloak)
7. [Endpoints](#endpoints)
8. [Modelo de datos](#modelo-de-datos)
9. [Manejo de errores](#manejo-de-errores)
10. [Tests y cobertura](#tests-y-cobertura)
11. [CI/CD](#cicd)
12. [Estructura del proyecto](#estructura-del-proyecto)
13. [Comandos de referencia](#comandos-de-referencia)

---

## Stack

| Pieza | Versión | Para qué |
|---|---|---|
| Java | 25 | Toolchain fijado en `build.gradle` |
| Spring Boot | 4.1.0 | Web MVC, Data JPA, Validation, Actuator |
| Spring Security | 7.1 | OAuth2 Resource Server: valida los tokens JWT |
| Keycloak | 26.7 | Servidor de identidad: usuarios, contraseñas y roles |
| Gradle | 9.5.1 | Wrapper incluido, no hace falta instalarlo |
| PostgreSQL | 15-alpine | Base de datos en Docker |
| H2 | en memoria | Base de datos de los tests |
| springdoc-openapi | 3.0.3 | Swagger UI (la línea 3.x es la de Spring Boot 4) |
| JaCoCo | 0.8.14 | Reporte de cobertura |
| Lombok | — | Getters, builders y `@Slf4j` |

---

## Requisitos

* **Docker + Docker Compose** si vas a levantar todo con contenedores (la opción
  recomendada: no necesitas Java ni Postgres instalados).
* **Java 25** solo si vas a correr la app o los tests desde tu máquina.

No necesitas instalar Gradle: el proyecto trae el wrapper (`./gradlew`).

---

## Puesta en marcha

### Opción A — Todo con Docker (recomendada)

```bash
cp .env.template .env      # y completa los valores
docker compose up -d
```

Eso levanta tres contenedores:

| Servicio | URL | Qué es |
|---|---|---|
| `app` | http://localhost:8080 | La API |
| `keycloak` | http://localhost:8081 | Login y roles (consola: `admin` / `admin`) |
| `db` | `localhost:5432` | Postgres |

> **El puerto 8080 tiene que estar libre.** Si otro contenedor o proceso ya lo
> ocupa, la app falla al arrancar con `Bind for 0.0.0.0:8080 failed: port is
> already allocated`. Se ve con `lsof -nP -iTCP:8080 -sTCP:LISTEN`.

La primera vez Keycloak tarda entre 20 y 40 segundos: además de arrancar tiene
que importar el realm. La app **no** lo espera (arranca igual y responde los
`GET`), pero pedir un token antes de que termine da error de conexión. Se ve
cuándo está listo con:

```bash
docker compose logs -f keycloak    # termina con "Realm 'cowsvsclown' imported"
```

No hace falta `--build`: el `compose.yml` tiene `pull_policy: build`, así que
reconstruye la imagen en cada `up` sin que tengas que borrar nada a mano.

```bash
docker compose logs -f app   # ver los logs
docker compose down          # apagar (conserva los datos)
docker compose down -v       # apagar y borrar la base
```

Los datos viven en el volumen `postgres_data`, que **no** se borra con `down`.

### Opción B — Base en Docker, app desde el IDE

Útil para depurar con breakpoints:

```bash
docker compose up -d db keycloak   # Postgres y Keycloak
./gradlew bootRun                  # la app usa el perfil "dev" por defecto
```

Keycloak hace falta también acá: sin él la app arranca, pero no hay forma de
conseguir un token y todo lo que escribe responde `401`. En el perfil `dev` la
app lo busca en `http://localhost:8081`, que es donde lo publica el compose.

### Variables de entorno

Las lee Compose del archivo `.env` (que no va al repositorio). La plantilla
`.env.template` sí está versionada y documenta cada una:

| Variable | Ejemplo | Nota |
|---|---|---|
| `DB_NAME` | `cowsvsclown_db` | Se crea al primer arranque |
| `DB_USER` | `postgres` | |
| `DB_PASSWORD` | `changeme` | Usa algo fuerte fuera de desarrollo |
| `DB_PORT` | `5432` | Puerto de **tu máquina**, no el del contenedor |
| `KEYCLOAK_PORT` | `8081` | Puerto de **tu máquina** para Keycloak |
| `KEYCLOAK_ADMIN_USER` | `admin` | Administrador de la consola de Keycloak |
| `KEYCLOAK_ADMIN_PASSWORD` | `admin` | Ídem. Nunca así fuera de desarrollo |
| `KEYCLOAK_REALM` | `cowsvsclown` | Debe coincidir con el `realm` del JSON |
| `KEYCLOAK_CLIENT_ID` | `cowsvsclown-api` | Debe coincidir con el `clientId` del JSON |

`DB_PORT` solo cambia por dónde entras tú (DBeaver, psql). Dentro de la red de
Docker los contenedores siempre se hablan por el 5432. Si ya tienes un Postgres
local ocupando el 5432, pon `5433` aquí.

`KEYCLOAK_PORT` sí es distinto: cambiarlo cambia el emisor (`iss`) que Keycloak
escribe dentro de cada token, porque `compose.yml` construye con él la URL
pública del realm. Es coherente —la app valida contra esa misma URL— pero los
tokens pedidos antes del cambio dejan de servir.

`KEYCLOAK_REALM` y `KEYCLOAK_CLIENT_ID` no crean nada: nombran lo que ya está
dentro de `keycloak/realm-cowsvsclown.json`. Si no coinciden letra por letra, la
app busca las llaves de un realm que no existe y **todo lo protegido responde
401**.

---

## Perfiles de configuración

`application.yml` se carga siempre y encima se aplica el perfil activo.

| Perfil | Cuándo se activa | Base de datos | Keycloak |
|---|---|---|---|
| `dev` | Por defecto | Postgres en `localhost` | `localhost:8081` |
| `docker` | `SPRING_PROFILES_ACTIVE=docker` (lo pone `compose.yml`) | Postgres en el host `db` | llaves por `keycloak:8080`, emisor `localhost:8081` |
| `test` | `@ActiveProfiles("test")` en los tests | H2 en memoria, `create-drop` | ninguno: se reemplaza el decodificador |

En `dev` y `docker` se usa `ddl-auto: update`, que ajusta las tablas a las
entidades en cada arranque sin borrar datos.

> **Lo único que `ddl-auto: update` no sabe hacer** es agregar una columna
> `NOT NULL` a una tabla que ya tiene filas: Postgres la rechaza, Hibernate lo
> deja pasar como un `WARN` y la app arranca con la tabla a medio actualizar. Si
> ves un error de `GenerationTarget encountered exception accepting command` en
> los logs, o empiezas de cero con `docker compose down -v`, o arreglas la tabla
> a mano. En un proyecto real esto se resuelve con Flyway o Liquibase.

---

## Documentación de la API

Con la app corriendo:

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Contrato OpenAPI (JSON) | http://localhost:8080/v3/api-docs |
| Health check | http://localhost:8080/actuator/health |

Swagger se genera solo, leyendo los controllers y los DTO. `OpenApiConfig` solo
agrega los datos generales (título, versión, contacto) que no se pueden deducir
del código, más los dos esquemas de autenticación del botón **Authorize** 🔓:
entrar por Keycloak, o pegar un token a mano.

De Actuator se expone únicamente `/health`: el resto de endpoints (`env`,
`beans`, `mappings`) muestran configuración interna y no tienen por qué estar
publicados.

---

## Seguridad y login con Keycloak

**Consultar es público. Crear, modificar y borrar exige un token.**

### El modelo en una frase

La API **no tiene login**: ni formulario, ni tabla de usuarios, ni contraseñas.
De eso se encarga Keycloak. La API solo recibe un token (un JWT), comprueba que
sea auténtico y mira qué roles trae adentro para decidir si deja pasar la
operación. En la jerga de OAuth2, la API es un *resource server* y Keycloak es
el *authorization server*.

```
 ┌──────────┐   1. usuario y contraseña   ┌────────────┐
 │ Vos      │ ──────────────────────────► │  Keycloak  │
 │ (Swagger │ ◄────────────────────────── │   :8081    │
 │  o curl) │   2. te devuelve un JWT     └────────────┘
 └────┬─────┘                                    ▲
      │                                          │ 4. baja las llaves
      │ 3. Authorization: Bearer <JWT>           │    públicas UNA vez
      ▼                                          │    y las cachea
 ┌────────────┐                                  │
 │ Cows API   │ ─────────────────────────────────┘
 │   :8080    │  5. valida firma + vencimiento + emisor
 └────────────┘  6. lee los roles y aplica las reglas
```

El paso 4 es lo importante: la API se baja las llaves públicas del realm **una
sola vez** y después valida todos los tokens sola, sin una llamada de red por
petición y sin depender de que Keycloak esté disponible. El precio es que un
token vale hasta que vence aunque al usuario lo deshabiliten mientras tanto; por
eso duran 5 minutos.

### Quién puede hacer qué

| Método | Ruta | Rol necesario |
|---|---|---|
| `GET` | `/api/**` | ninguno, ni siquiera token |
| `POST` | `/api/**` | `USER` o `ADMIN` |
| `PATCH` / `PUT` | `/api/**` | `USER` o `ADMIN` |
| `DELETE` | `/api/clowns/{id}/cows/{cowId}` | `USER` o `ADMIN` |
| `DELETE` | `/api/**` (el resto) | solo `ADMIN` |
| `GET` | `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health` | ninguno |

Los dos `DELETE` están separados porque no son la misma operación: quitarle una
vaca a un payaso solo rompe un vínculo de la tabla intermedia y se deshace
volviendo a asignarla, mientras que borrar una vaca da de baja el registro
entero.

Las reglas están todas en un único archivo, `common/config/SecurityConfig.java`,
y terminan con `anyRequest().authenticated()`: cualquier ruta nueva queda
protegida por omisión, no abierta.

### Usuarios de prueba

Vienen creados en el realm, no hay que registrarlos:

| Usuario | Contraseña | Roles | Para probar |
|---|---|---|---|
| `admin` | `admin123` | `ADMIN`, `USER` | Que todo funcione |
| `user` | `user123` | `USER` | Que un `DELETE` dé **403** |
| `curioso` | `curioso123` | ninguno | Que hasta un `POST` dé **403** |

`curioso` es el caso que aclara la diferencia entre los dos códigos: su token es
válido, así que **no** da 401. Da 403.

| Código | Significa | Cuándo |
|---|---|---|
| `401` | "No sé quién sos" | Falta el token, o está vencido, mal firmado o es de otro realm |
| `403` | "Sé quién sos y no podés" | El token es válido pero al usuario le falta el rol |

### Probarlo desde Swagger

<http://localhost:8080/swagger-ui.html> → botón **Authorize** 🔓 → te lleva a la
pantalla de Keycloak → entrás con `admin` / `admin123`. A partir de ahí Swagger
adjunta el token en cada petición.

### Probarlo desde la terminal

```bash
# 1) Pedir el token
TOKEN=$(curl -s -X POST \
  "http://localhost:8081/realms/cowsvsclown/protocol/openid-connect/token" \
  -d "client_id=cowsvsclown-api" \
  -d "username=admin" \
  -d "password=admin123" \
  -d "grant_type=password" | jq -r .access_token)

# 2) Usarlo
curl -X POST http://localhost:8080/api/owners \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"firstName":"Ada","lastName":"Lovelace"}'
```

Comprobaciones rápidas de que la protección está viva:

```bash
curl -i http://localhost:8080/api/owners                      # 200, sin token
curl -i -X POST http://localhost:8080/api/owners \
     -H 'Content-Type: application/json' -d '{}'              # 401
```

### Qué hay dentro del token

Un JWT son tres partes separadas por puntos: `cabecera.contenido.firma`, en
Base64. **No está cifrado**: cualquiera lo puede leer. Lo que no puede es
modificarlo sin romper la firma.

```bash
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq
```

```jsonc
{
  "iss": "http://localhost:8081/realms/cowsvsclown",  // quién lo emitió
  "aud": "cowsvsclown-api",                           // para quién es
  "exp": 1735689600,                                  // cuándo vence
  "preferred_username": "admin",
  "realm_access": { "roles": ["ADMIN", "USER"] }      // <-- lo que lee la API
}
```

### Las dos piezas de código que hubo que escribir

Casi todo lo hace Spring Boot solo a partir de dos propiedades en
`application.yml`. Solo dos cosas necesitaron código:

1. **`common/security/KeycloakRoleConverter.java`** — Spring Security **no sabe
   leer los roles de Keycloak**. Su conversor de fábrica solo mira el claim
   `scope`, y los roles de Keycloak están en `realm_access.roles`. Sin esta
   clase, un usuario con rol `ADMIN` llega autenticado pero *sin ninguna
   autoridad*, y cualquier regla lo rechaza. El síntoma es el clásico "el token
   es válido pero siempre me da 403".

2. **`common/security/RestAuthenticationEntryPoint.java`** y
   **`RestAccessDeniedHandler.java`** — los rechazos ocurren en la cadena de
   filtros, *antes* del `DispatcherServlet`, así que el `GlobalExceptionHandler`
   ni se entera y los 401/403 saldrían con un formato distinto al resto de la
   API. Estas dos clases reinyectan la excepción en Spring MVC para que el
   cuerpo lo arme el mismo sitio que todos los demás errores:

   ```json
   {
     "status": 401,
     "error": "Unauthorized",
     "message": "A valid access token is required to use this endpoint",
     "path": "/api/owners",
     "timestamp": "2026-08-07T20:27:19.501"
   }
   ```

### El error clásico: dos URLs, no una

En `application.yml` hay **dos** direcciones del mismo realm, y no es un
copy-paste mal hecho:

| Propiedad | Valor en Docker | Quién la usa |
|---|---|---|
| `issuer-uri` | `http://localhost:8081/realms/cowsvsclown` | Nadie la llama: solo se **compara** con el claim `iss` del token |
| `jwk-set-uri` | `http://keycloak:8080/realms/.../certs` | La **llama la app** para bajarse las llaves públicas |

El token lo pedís vos desde el navegador, así que dentro dice
`localhost:8081`. Pero el contenedor de la app no tiene ningún Keycloak en su
`localhost`: para él está en `keycloak:8080`. Poner las dos iguales rompe de una
de estas dos formas:

* **las dos internas** → `The iss claim is not valid`, un 401 con un token bueno
* **las dos públicas** → `Connection refused` al bajar las llaves

### Cambiar la configuración de Keycloak

Todo el realm (cliente, roles, usuarios) está en
[`keycloak/realm-cowsvsclown.json`](keycloak/realm-cowsvsclown.json) y se importa
solo al levantar. Para que un cambio quede versionado, editás el JSON y:

```bash
docker compose down
docker compose up -d
```

No hace falta `-v`: en modo `start-dev` Keycloak guarda todo en una base H2 que
vive **dentro del contenedor**, no en un volumen, así que al recrearlo nace
vacía y el realm se importa de nuevo. La base de Postgres sí es un volumen, o
sea que las vacas siguen ahí.

El reverso de esa moneda: **lo que toques por la consola web se pierde en el
siguiente `down`**. Si querés que un usuario o un rol sobreviva, tiene que estar
en el JSON.

La explicación bloque por bloque del realm —y por qué cada opción está como
está— vive en [`keycloak/README.md`](keycloak/README.md), porque JSON no admite
comentarios.

---

## Endpoints

Los `GET` son públicos; todo lo demás pide token. Qué rol hace falta en cada
caso está en [Seguridad y login con Keycloak](#seguridad-y-login-con-keycloak).

### Vacas — `/api/cows`

| Verbo | Ruta | Qué hace |
|---|---|---|
| `GET` | `/api/cows` | Lista las vacas activas con su dueño y sus payasos |
| `GET` | `/api/cows/{id}` | Busca una vaca por id |
| `GET` | `/api/cows/search?name=Lola` | Busca por nombre exacto (case-insensitive) |
| `GET` | `/api/cows/owner/{ownerId}` | Vacas de un dueño (lado N de la relación 1 a N) |
| `POST` | `/api/cows` | Crea una vaca y resuelve ambas relaciones de una vez |
| `PATCH` | `/api/cows/{id}` | Modifica solo los campos que se envían |
| `PATCH` | `/api/cows/{id}/owner/{ownerId}` | Cambia la vaca de dueño |
| `DELETE` | `/api/cows/{id}` | Baja lógica |

### Payasos — `/api/clowns`

| Verbo | Ruta | Qué hace |
|---|---|---|
| `GET` | `/api/clowns` | Lista los payasos activos |
| `GET` | `/api/clowns/{id}` | Busca un payaso por id |
| `GET` | `/api/clowns/cow/{cowId}` | Payasos que cuidan una vaca |
| `POST` | `/api/clowns` | Crea un payaso |
| `PATCH` | `/api/clowns/{id}` | Modifica un payaso |
| `POST` | `/api/clowns/{clownId}/cows/{cowId}` | Asigna una vaca (crea el vínculo N a M) |
| `DELETE` | `/api/clowns/{clownId}/cows/{cowId}` | Desasigna una vaca (borra el vínculo) |
| `DELETE` | `/api/clowns/{id}` | Baja lógica |

### Dueños — `/api/owners`

| Verbo | Ruta | Qué hace |
|---|---|---|
| `GET` | `/api/owners` | Lista los dueños activos |
| `GET` | `/api/owners/{id}` | Busca un dueño por id |
| `POST` | `/api/owners` | Crea un dueño |
| `PATCH` | `/api/owners/{id}` | Modifica un dueño |
| `DELETE` | `/api/owners/{id}` | Baja lógica |

### Ejemplo

Crear una vaca resolviendo las dos relaciones en la misma llamada:

```bash
curl -X POST http://localhost:8080/api/cows \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "name": "Lola",
        "weight": 450,
        "milkperday": 12,
        "ownerId": 1,
        "clownIds": ["3fa85f64-5717-4562-b3fc-2c963f66afa6"]
      }'
```

`$TOKEN` sale del `curl` a Keycloak que está en
[Seguridad](#probarlo-desde-la-terminal). Sin esa cabecera, la respuesta es
`401`.

`ownerId` es obligatorio (la columna `owner_id` es `NOT NULL`); `clownIds` es
opcional y cada id crea una fila en `clown_cow`.

---

## Modelo de datos

```
owners                cows                     clowns
------                ----                     ------
id      (Long)   1──N id        (UUID)   N──M  id          (UUID)
firstName            name       unique         name        unique
lastName             weight                    description
active               milkperday                active
                     active
                     owner_id  ──> owners.id
                                        │
                                  clown_cow
                                  (clown_id, cow_id)
```

| Entidad | Id | Notas |
|---|---|---|
| `Owner` | `Long`, autoincremental | Dueño de las vacas |
| `Cow` | `UUID` | `name` único; dueño obligatorio |
| `Clown` | `UUID` | `name` único |

El lado dueño de la relación N a M es `Clown`: ahí se declara el `@JoinTable`,
y `Cow` lo refleja con `mappedBy = "cows"`. La llave foránea de la relación
1 a N siempre vive en el lado `@ManyToOne`, es decir en `cows`.

---

## Manejo de errores

Los controllers no tienen un solo `try/catch`: lanzan la excepción de negocio y
`GlobalExceptionHandler` (`@RestControllerAdvice`) decide el código y el cuerpo.
Todas las respuestas de error salen con la misma forma (`ErrorResponse`), así el
cliente las puede manejar de manera predecible.

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "There is no Cow with id 3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "path": "/api/cows/3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "timestamp": "2026-07-31T10:15:30.123"
}
```

En los errores de validación se agrega un mapa `validationErrors` con el detalle
por campo. En los demás errores el campo **no aparece** en el JSON, gracias a
`@JsonInclude(NON_NULL)`:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "There are invalid fields in the request",
  "path": "/api/cows",
  "timestamp": "2026-07-31T10:15:30.123",
  "validationErrors": {
    "name": "name is mandatory",
    "weight": "weight must be greater than 0"
  }
}
```

### Qué produce cada código

| Código | Se dispara cuando | Excepción |
|---|---|---|
| `400` | Regla de negocio violada | `BadRequestException` |
| `400` | Body inválido (`@Valid`) | `MethodArgumentNotValidException` |
| `400` | Parámetro inválido (`@Min`, `@NotBlank`) | `ConstraintViolationException` |
| `400` | JSON malformado o campo con tipo raro | `HttpMessageNotReadableException` |
| `400` | UUID o número malformado en la URL | `MethodArgumentTypeMismatchException` |
| `400` | Falta un query param obligatorio | `MissingServletRequestParameterException` |
| `401` | Falta el token, o está vencido, mal firmado o es de otro realm | `AuthenticationException` |
| `403` | El token es válido pero al usuario le falta el rol | `AccessDeniedException` |
| `404` | El recurso no existe o fue dado de baja | `ResourceNotFoundException` |
| `404` | La ruta no corresponde a ningún endpoint | `NoResourceFoundException` |
| `405` | La ruta existe pero no acepta ese verbo | `HttpRequestMethodNotSupportedException` |
| `409` | Conflicto con el estado actual (duplicados) | `ConflictException` |
| `409` | La base rechazó la operación por un constraint | `DataIntegrityViolationException` |
| `500` | Cualquier cosa imprevista | `Exception` |

Los dos últimos casos son deliberadamente genéricos hacia afuera: la excepción
completa se registra en el log, pero al cliente se le manda un mensaje neutro.
El texto de Postgres expone nombres de tablas y constraints, y una
`NullPointerException` expondría rutas de clases internas.

El `401` y el `403` son un caso aparte: los produce la cadena de filtros de
Spring Security, *antes* de que la petición llegue al `DispatcherServlet`, así
que el `GlobalExceptionHandler` nunca se enteraría por su cuenta.
`RestAuthenticationEntryPoint` y `RestAccessDeniedHandler` reinyectan la
excepción en Spring MVC justamente para que el cuerpo lo arme el mismo sitio que
todos los demás errores y el cliente reciba siempre la misma forma de JSON.

El mensaje del `401` tampoco dice si el token venció, si está mal firmado o si
es de otro emisor: esa distinción solo le sirve a quien está tanteando la API.
El motivo real queda en el log.

---

## Tests y cobertura

**241 tests** en 23 archivos, todos en verde.

| Tipo | Tests | Archivos | Qué prueban |
|---|---:|---:|---|
| Unitarias | 121 | 10 | Servicios, mappers, excepciones y roles, sin Spring |
| Integración | 75 | 8 | Controllers con MockMvc, repositorios con H2 y las reglas de seguridad |
| End to end | 44 | 4 | La app completa por HTTP real |
| Contexto | 1 | 1 | Que todos los beans se puedan construir |

Cobertura: **98.3% de líneas**, 95.8% de instrucciones, 87.2% de ramas, 100% de
clases.

Los 45 tests de seguridad viven en tres archivos y se reparten así:

| Archivo | Qué prueba |
|---|---|
| `KeycloakRoleConverterTest` | Que los roles del token se traduzcan bien, incluso si el claim llega roto |
| `SecurityConfigIntegrationTest` | Las reglas: quién puede llamar a qué, con la cadena de filtros real |
| `SecurityE2ETest` | Lo mismo por HTTP real: cabeceras, cuerpo del error, y que un rechazo no escriba en la base |

Ninguno necesita Keycloak levantado: se reemplaza solo el `JwtDecoder`, que es
la pieza que verifica la firma. Todo lo demás —los filtros, la conversión de
roles, las reglas, los 401 y 403— corre de verdad. Por eso la suite sigue
funcionando en CI sin un contenedor extra.

### Correr los tests

```bash
./gradlew test                 # todos + genera los reportes
```

O solo una familia, que es lo útil mientras trabajas:

```bash
./gradlew pruebasUnitarias     # 121 tests, menos de 1s
./gradlew pruebasIntegracion   # 75 tests, ~2s
./gradlew pruebasE2E           # 44 tests, ~5s (levantan la app entera)
```

La clasificación sale del nombre de la clase, que es la convención que ya seguía
el proyecto: `*E2ETest`, `*IntegrationTest`, y el resto son unitarias. Una clase
nueva entra sola en su grupo sin tocar el `build.gradle`.

> Las tres tareas por tipo **no** generan reporte de cobertura, a propósito: el
> porcentaje solo tiene sentido con la suite completa. Una clase cubierta por las
> e2e aparecería como no cubierta al correr solo las unitarias. Para cobertura,
> `./gradlew test`.

Y para un subconjunto arbitrario:

```bash
./gradlew test --tests "*ServiceTest"
./gradlew test --tests "com.sebasmalparqueado.cowsvsclown.common.exceptions.*"
```

### Reportes

Hay dos formas de verlos.

**1. Los que están versionados en el repositorio** (`docs/reportes/`). Se abren
sin clonar ni compilar nada:

| Reporte | Ruta |
|---|---|
| Resultado de las pruebas | `docs/reportes/pruebas/index.html` |
| Cobertura | `docs/reportes/cobertura/index.html` |

Son una **foto** del momento en que se corrieron, no se actualizan solos. Después
de tocar código hay que regenerarlos:

```bash
./gradlew publicarReportes
```

Esa tarea corre las pruebas, genera los HTML y los copia a `docs/reportes/`.
Borra el contenido anterior antes de copiar, porque los nombres de las páginas de
JaCoCo dependen de las clases y al renombrar una quedarían archivos huérfanos.

**2. Los que genera Gradle en cada corrida** (siempre frescos, en `build/`, que
no va al repositorio):

| Reporte | Ruta |
|---|---|
| Resultado de las pruebas | `build/reports/tests/test/index.html` |
| Cobertura | `build/reports/jacoco/test/html/index.html` |
| Cobertura en XML (para CI/SonarQube) | `build/reports/jacoco/test/jacocoTestReport.xml` |

```bash
./gradlew cobertura            # corre las pruebas e imprime dónde quedaron
./gradlew coberturaAbrir       # lo mismo, y los abre en el navegador
```

`coberturaAbrir` detecta el sistema operativo y usa `open` (Mac), `xdg-open`
(Linux) o `explorer` (Windows), así que funciona igual para todo el equipo.

En la página de JaCoCo puedes navegar hasta el código fuente y ver línea por
línea: **verde** = cubierta, **amarillo** = rama parcialmente cubierta, **rojo**
= sin cubrir.

### El gate de cobertura

`./gradlew build` falla si la cobertura baja del **70% de líneas** o del **50%
de ramas por clase**. La idea es que si alguien mete lógica nueva sin tests, el
build lo diga en vez de pasar en silencio.

Si te estorba mientras estás iterando:

```bash
./gradlew build -x jacocoTestCoverageVerification
```

### Qué se excluye del cálculo

`dto/`, `entity/`, `config/` y la clase `CowsvsclownApplication`. Son datos y
arranque, no lógica: contarlos solo hunde el porcentaje sin que haya nada real
que testear.

Además, `lombok.config` activa `addLombokGeneratedAnnotation`, que marca todo lo
que Lombok genera con `@lombok.Generated`. JaCoCo respeta esa anotación, así que
los getters y setters generados no cuentan como código sin cubrir. Sin eso el
porcentaje estaría mintiendo.

### Tipos de test

| Tipo | Cómo | Qué verifica |
|---|---|---|
| Unitario de servicio | Mockito, repositorios mockeados | La lógica de negocio aislada |
| Unitario de mapper | Sin Spring | Entidad ↔ DTO |
| Integración de controller | `@WebMvcTest`, servicio mockeado | Rutas, status y JSON de la capa web |
| Integración de repositorio | `@DataJpaTest` + H2 | Las queries JPQL y nativas |
| End to end | `@SpringBootTest(RANDOM_PORT)` + H2 | Un flujo real por HTTP, de punta a punta |

Por qué los de controller cuentan como integración aunque el servicio esté
mockeado: la petición atraviesa el stack real de Spring MVC (ruteo, binding del
JSON, `@Valid`, `GlobalExceptionHandler`, serialización de la respuesta). Eso es
justo la parte que un test unitario de la clase controller se saltaría.

### Desglose por archivo

| Archivo | Tipo | Tests |
|---|---|---:|
| `GlobalExceptionHandlerTest` | Unitario | 22 |
| `SecurityConfigIntegrationTest` | Integración | 18 |
| `CowServiceTest` | Unitario | 16 |
| `GlobalExceptionHandlerIntegrationTest` | Integración | 14 |
| `OwnerServiceTest` | Unitario | 14 |
| `SecurityE2ETest` | E2E | 14 |
| `ClownServiceTest` | Unitario | 13 |
| `KeycloakRoleConverterTest` | Unitario | 13 |
| `ClownE2ETest` | E2E | 11 |
| `ClownControllerIntegrationTest` | Integración | 10 |
| `CowMapperTest` | Unitario | 10 |
| `CowE2ETest` | E2E | 10 |
| `CowControllerIntegrationTest` | Integración | 9 |
| `ErrorResponseTest` | Unitario | 9 |
| `OwnerE2ETest` | E2E | 9 |
| `BusinessExceptionsTest` | Unitario | 9 |
| `OwnerMapperTest` | Unitario | 8 |
| `ClownMapperTest` | Unitario | 7 |
| `OwnerControllerIntegrationTest` | Integración | 7 |
| `CowRepositoryIntegrationTest` | Integración | 6 |
| `OwnerRepositoryIntegrationTest` | Integración | 6 |
| `ClownRepositoryIntegrationTest` | Integración | 5 |
| `CowsvsclownApplicationTests` | Contexto | 1 |

Los tests usan H2 en memoria con `create-drop`, así que cada corrida arranca con
tablas limpias y no dependen de que haya un Postgres levantado.

### Un detalle que vale la pena saber

Los tests de repositorio y los e2e limpian el estado de formas distintas, y no es
un descuido:

* Los `@DataJpaTest` corren cada método dentro de una transacción que se
  **revierte** al terminar. Por eso no se pisan entre sí.
* Los e2e escriben de verdad: la petición viaja por HTTP y la transacción
  **confirma**. No hay rollback posible, así que usan
  `@DirtiesContext(AFTER_EACH_TEST_METHOD)` para reconstruir el contexto entre
  métodos. Sin eso, cada test heredaría las filas del anterior y las validaciones
  de duplicados empezarían a fallar según el orden de ejecución.

Es también la razón de que los e2e sean los lentos: reconstruir el contexto
cuesta. Por eso hay 30 y no 200 — cubren el camino feliz y los errores que vale
la pena ver de punta a punta, y los casos exhaustivos viven en las unitarias.

---

## CI/CD

La cadena completa es esta:

```
git push a main
      │
      ▼
GitHub Actions  →  compila, corre las 241 pruebas, verifica cobertura
      │
      ├── rojo  →  Render NO despliega. Producción sigue con la versión anterior.
      │
      └── verde →  Render construye la imagen y despliega
```

### Los dos workflows

| Archivo | Cuándo corre | Para qué |
|---|---|---|
| `ci.yml` | Push a `main` y PRs | El gate: decide si el código pasa |
| `reportes.yml` | Solo a mano | Descargar los HTML de pruebas y cobertura |

Están separados a propósito. `ci.yml` corre en cada push, así que hace una sola
cosa y la hace rápido. Guardar los reportes en cada ejecución sumaría tiempo y
llenaría la cuota de almacenamiento del repositorio, y casi nunca se descargan.

### `ci.yml` — el gate

Un solo trabajo en cuatro pasos comentados uno por uno: descargar el código,
instalar Java 25, dar permiso a `gradlew` y correr `./gradlew build`.

Se dispara en dos momentos:

| Evento | Para qué |
|---|---|
| Push a `main` | Es lo que Render mira para decidir si despliega |
| Pull Request hacia `main` | Ver si el cambio rompe algo **antes** de mezclarlo |

El paso que decide todo es `./gradlew build`, porque hace las tres cosas de una:
compila, corre las pruebas y ejecuta el gate de cobertura. Si cualquiera falla,
Gradle devuelve un código de error y el workflow queda en rojo. No hay más
lógica que esa.

Las pruebas usan H2 en memoria, así que en CI **no hace falta** levantar un
Postgres ni configurar variables de base de datos.

### Qué se ve al terminar

El check aparece en verde o en rojo sobre el commit y sobre el PR. Si algo
falló, el detalle de qué prueba fue está en el log del paso *Compilar y correr
las pruebas*.

### `reportes.yml` — cuando el resumen no alcanza

Para ver **qué línea** quedó sin cubrir, o el detalle completo de una prueba que
falló, hay que abrir los HTML. Ese workflow no se ejecuta solo:

**Actions → Reportes (en la lista de la izquierda) → Run workflow**

Se puede elegir la rama, así que sirve para revisar una rama de trabajo y no
solo `main`. Al terminar, los reportes quedan en la sección **Artifacts** al pie
de la página del run: se descarga un `.zip` y adentro se abre el `index.html` de
cada carpeta con el navegador.

Corre `./gradlew test` con `continue-on-error`, así que **sube los reportes
aunque las pruebas fallen** — que es cuando más se necesitan.

### Hacer que las pruebas sean obligatorias

El workflow por sí solo **marca** el rojo, pero no impide mezclar un PR roto.
Para que sea obligatorio hay que activarlo en GitHub una sola vez:

**Settings → Branches → Add branch protection rule**

1. En *Branch name pattern*: `main`
2. Marcar **Require status checks to pass before merging**
3. Buscar y seleccionar el check **`Pruebas y cobertura`**
4. Guardar

Ese nombre sale del campo `name:` del job en `ci.yml`. Si lo cambias ahí, hay
que actualizar también esta regla o dejará de aplicar.

### Configurar Render

Render se conecta al repositorio y espera a que GitHub Actions termine en verde.

**1. Crear la base de datos**

En Render: **New → Postgres**. Al crearla, guarda los datos de la sección
*Connections*.

**2. Crear el servicio web**

**New → Web Service**, conecta el repositorio. Render detecta el `Dockerfile`
solo y elige *Runtime: Docker*.

**3. Activar la espera por CI**

En **Settings → Build & Deploy → Auto-Deploy**, elegir:

```
After CI Checks Pass
```

Esta es la línea que conecta las dos mitades. Con la opción por defecto (*On
Commit*), Render desplegaría apenas llega el push, sin esperar a las pruebas.

**4. Variables de entorno**

En **Environment**, agregar:

| Variable | Valor |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `docker` |
| `DB_URL` | `jdbc:postgresql://HOST/BASE` |
| `DB_USERNAME` | El usuario de la base de Render |
| `DB_PASSWORD` | La contraseña de la base de Render |
| `DDL_AUTO` | `update` |

> **El error más común aquí:** Render entrega la URL de la base en formato
> `postgresql://usuario:clave@host/base`, y Spring **no** la entiende así.
> Hay que reescribirla como `jdbc:postgresql://host/base` (con el prefijo
> `jdbc:` y **sin** el usuario ni la contraseña adentro) y pasar esas dos
> credenciales por separado en `DB_USERNAME` y `DB_PASSWORD`.

No hay que configurar el puerto: `application.yml` lee `${PORT:8080}`, así que
usa el que Render asigne y el 8080 de siempre en local.

---

## Estructura del proyecto

El código está organizado **por funcionalidad**, no por capa técnica: todo lo de
vacas vive junto, en vez de tener un paquete `controllers` con los tres
controllers dentro. Así, para tocar una funcionalidad, no hay que saltar entre
carpetas lejanas.

```
src/main/java/com/sebasmalparqueado/cowsvsclown/
├── cows/
│   ├── controller/      CowController
│   ├── dto/             Request, Response, SummaryResponse, UpdateRequest
│   ├── entity/          Cow
│   ├── mapper/          CowMapper
│   ├── repository/      ICowRepository
│   └── service/         CowService
├── clowns/              (misma estructura)
├── owner/               (misma estructura)
└── common/
    ├── config/          OpenApiConfig, SecurityConfig
    ├── security/        KeycloakRoleConverter
    │                    RestAuthenticationEntryPoint (401)
    │                    RestAccessDeniedHandler (403)
    └── exceptions/      GlobalExceptionHandler, ErrorResponse
                         BadRequestException, ConflictException,
                         ResourceNotFoundException

src/test/java/...        misma estructura + e2e/ (incluye E2EAuth y SecurityE2ETest)
src/main/resources/      application.yml, application-dev.yml, application-docker.yml
src/test/resources/      application-test.yml
keycloak/                realm-cowsvsclown.json (el realm que se importa solo)
                         README.md (qué hace cada bloque del realm)
```

`config/` guarda lo declarativo —qué reglas hay, qué muestra Swagger— y
`security/` la lógica que sí tiene comportamiento y sí se testea. Por eso
`SecurityConfig` está en `config/`, junto a `OpenApiConfig`, y no con las otras
tres: `build.gradle` excluye `**/config/**` del cálculo de cobertura, porque
medir configuración no dice nada útil.

Cada recurso tiene cuatro DTO en vez de exponer la entidad directamente:

| DTO | Para qué |
|---|---|
| `XRequest` | Lo que llega al crear |
| `XUpdateRequest` | Lo que llega al modificar (campos opcionales) |
| `XResponse` | Lo que sale, con sus relaciones |
| `XSummaryResponse` | Versión reducida, para no anidar infinito |

`XSummaryResponse` es lo que evita el ciclo: una vaca trae el resumen de su
dueño, no el dueño completo con todas sus vacas dentro.

---

## Comandos de referencia

### Docker

```bash
docker compose up -d              # levanta todo (reconstruye la imagen)
docker compose up -d db keycloak  # solo la base y el login
docker compose logs -f app        # logs de la app
docker compose logs -f keycloak   # ver si el realm ya se importó
docker compose down               # apaga, conserva los datos
docker compose down -v            # apaga y borra el volumen de Postgres
```

### Keycloak

```bash
# Pedir un token (admin / user / curioso)
TOKEN=$(curl -s -X POST \
  "http://localhost:8081/realms/cowsvsclown/protocol/openid-connect/token" \
  -d "client_id=cowsvsclown-api" -d "username=admin" \
  -d "password=admin123" -d "grant_type=password" | jq -r .access_token)

# Ver qué trae adentro
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq

# Usarlo
curl -X POST http://localhost:8080/api/owners \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"firstName":"Ada","lastName":"Lovelace"}'

# Configuración pública del realm (útil para depurar URLs)
curl -s http://localhost:8081/realms/cowsvsclown/.well-known/openid-configuration | jq
```

### Gradle

```bash
./gradlew bootRun                    # corre la app (perfil dev)
./gradlew build                      # compila, testea y verifica cobertura
./gradlew test                       # todos los tests (+ reportes)
./gradlew pruebasUnitarias           # solo unitarias (121)
./gradlew pruebasIntegracion         # solo integración (75)
./gradlew pruebasE2E                 # solo end to end (44)
./gradlew cobertura                  # tests + reportes, imprime las rutas
./gradlew coberturaAbrir             # tests + reportes + los abre
./gradlew publicarReportes           # copia los reportes a docs/reportes/
./gradlew jacocoTestCoverageVerification   # solo el gate de cobertura
./gradlew clean                      # borra build/
./gradlew tasks --group verification # lista las tareas de verificación
```

---

## Notas de diseño

Algunas decisiones que no se deducen del código a simple vista:

* **Baja lógica en todo.** Nada se borra de verdad: se marca `active = false` y
  deja de aparecer en las consultas. La excepción es `clown_cow`, donde una fila
  solo representa un vínculo y sí se elimina.
* **`PATCH` y no `PUT`** para modificar, porque solo se cambian los campos
  enviados. La respuesta es `200` y no `206`: el `206 Partial Content` es para
  descargas por rangos, no tiene nada que ver con una actualización parcial.
* **`POST` devuelve `201` con header `Location`** apuntando a la URL del recurso
  nuevo, que es la forma correcta en REST.
* **`DELETE` devuelve `204 No Content`**: la operación salió bien y no hay cuerpo
  que devolver.
* **El jar `-plain` está desactivado.** El plugin de Spring Boot genera dos jars
  y el `COPY build/libs/*.jar` del Dockerfile fallaba al encontrar dos archivos
  para un destino único. Ahora el ejecutable se llama siempre `app.jar`.
* **`FetchType.LAZY` en todas las relaciones**, con queries específicas en los
  repositorios (`findAllActiveWithRelations`) para traer lo que hace falta en
  cada caso sin caer en el problema N+1.
* **La autenticación está fuera de la API.** No hay tabla de usuarios ni
  contraseñas hasheadas: eso es un problema resuelto, y resolverlo otra vez mal
  es la forma más fácil de tener un agujero. Keycloak trae bloqueo por fuerza
  bruta, expiración, refresh y consola de administración sin escribir una línea.
* **Sin sesión (`STATELESS`).** No se crea `HttpSession`: cada petición se
  autentica sola con su token. Es lo que permite correr varias instancias de la
  API detrás de un balanceador sin compartir sesiones entre ellas.
* **CSRF desactivado, y no es un atajo.** El ataque CSRF depende de una cookie
  que el navegador manda sola. Acá la credencial es una cabecera que el cliente
  pone a mano y ningún sitio ajeno puede leer: sin ese vector, la protección no
  aporta nada y solo rompería los `POST`.
* **Las reglas viven en un solo archivo.** `SecurityConfig` es la única fuente de
  verdad de quién puede llamar a qué, y termina en
  `anyRequest().authenticated()`: un endpoint nuevo queda protegido por omisión,
  no abierto. `@EnableMethodSecurity` está activado por si algún día hace falta
  una regla que dependa de los datos ("solo el dueño puede editar sus vacas"),
  algo que una regla por URL no puede expresar.
