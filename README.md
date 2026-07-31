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
6. [Endpoints](#endpoints)
7. [Modelo de datos](#modelo-de-datos)
8. [Manejo de errores](#manejo-de-errores)
9. [Tests y cobertura](#tests-y-cobertura)
10. [Estructura del proyecto](#estructura-del-proyecto)
11. [Comandos de referencia](#comandos-de-referencia)

---

## Stack

| Pieza | Versión | Para qué |
|---|---|---|
| Java | 25 | Toolchain fijado en `build.gradle` |
| Spring Boot | 4.1.0 | Web MVC, Data JPA, Validation, Actuator |
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

Eso levanta Postgres y la app. La API queda en `http://localhost:8080`.

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
docker compose up -d db      # solo Postgres
./gradlew bootRun            # la app usa el perfil "dev" por defecto
```

### Variables de entorno

Las lee Compose del archivo `.env` (que no va al repositorio). La plantilla
`.env.template` sí está versionada y documenta cada una:

| Variable | Ejemplo | Nota |
|---|---|---|
| `DB_NAME` | `cowsvsclown_db` | Se crea al primer arranque |
| `DB_USER` | `postgres` | |
| `DB_PASSWORD` | `changeme` | Usa algo fuerte fuera de desarrollo |
| `DB_PORT` | `5432` | Puerto de **tu máquina**, no el del contenedor |

`DB_PORT` solo cambia por dónde entras tú (DBeaver, psql). Dentro de la red de
Docker los contenedores siempre se hablan por el 5432. Si ya tienes un Postgres
local ocupando el 5432, pon `5433` aquí.

---

## Perfiles de configuración

`application.yml` se carga siempre y encima se aplica el perfil activo.

| Perfil | Cuándo se activa | Base de datos |
|---|---|---|
| `dev` | Por defecto | Postgres en `localhost` |
| `docker` | `SPRING_PROFILES_ACTIVE=docker` (lo pone `compose.yml`) | Postgres en el host `db` |
| `test` | `@ActiveProfiles("test")` en los tests | H2 en memoria, `create-drop` |

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
del código.

De Actuator se expone únicamente `/health`: el resto de endpoints (`env`,
`beans`, `mappings`) muestran configuración interna y no tienen por qué estar
publicados.

---

## Endpoints

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
  -H "Content-Type: application/json" \
  -d '{
        "name": "Lola",
        "weight": 450,
        "milkperday": 12,
        "ownerId": 1,
        "clownIds": ["3fa85f64-5717-4562-b3fc-2c963f66afa6"]
      }'
```

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

---

## Tests y cobertura

**195 tests** en 20 archivos, con **95% de cobertura de instrucciones** y **87%
de ramas**.

### Correr los tests

```bash
./gradlew test                 # tests + genera los reportes
./gradlew cobertura            # lo mismo, e imprime dónde quedaron
./gradlew coberturaAbrir       # lo mismo, y los abre en el navegador
```

`coberturaAbrir` detecta el sistema operativo y usa `open` (Mac), `xdg-open`
(Linux) o `explorer` (Windows), así que funciona igual para todo el equipo.

Los reportes se generan siempre, incluso si solo corres `./gradlew test`:

| Reporte | Ruta |
|---|---|
| Resultado de los tests | `build/reports/tests/test/index.html` |
| Cobertura | `build/reports/jacoco/test/html/index.html` |
| Cobertura en XML (para CI/SonarQube) | `build/reports/jacoco/test/jacocoTestReport.xml` |

En la página de JaCoCo puedes navegar hasta el código fuente y ver línea por
línea: **verde** = cubierta, **amarillo** = rama parcialmente cubierta, **rojo**
= sin cubrir.

Para correr un subconjunto:

```bash
./gradlew test --tests "*ServiceTest"
./gradlew test --tests "com.sebasmalparqueado.cowsvsclown.common.exceptions.*"
```

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

### Desglose

| Archivo | Tests |
|---|---|
| `GlobalExceptionHandlerTest` | 22 |
| `CowServiceTest` | 16 |
| `GlobalExceptionHandlerIntegrationTest` | 14 |
| `ClownServiceTest` / `OwnerServiceTest` | 13 c/u |
| `ClownE2ETest` | 11 |
| `ClownControllerIntegrationTest` / `CowMapperTest` / `CowE2ETest` | 10 c/u |
| `CowControllerIntegrationTest` / `ErrorResponseTest` / `OwnerE2ETest` | 9 c/u |
| `BusinessExceptionsTest` | 9 |
| `OwnerMapperTest` | 8 |
| `ClownMapperTest` / `OwnerControllerIntegrationTest` | 7 c/u |
| `CowRepositoryIntegrationTest` / `OwnerRepositoryIntegrationTest` | 6 c/u |
| `ClownRepositoryIntegrationTest` | 5 |
| `CowsvsclownApplicationTests` | 1 |

Los tests usan H2 en memoria con `create-drop`, así que cada corrida arranca con
tablas limpias y no dependen de que haya un Postgres levantado.

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
    ├── config/          OpenApiConfig
    └── exceptions/      GlobalExceptionHandler, ErrorResponse
                         BadRequestException, ConflictException,
                         ResourceNotFoundException

src/test/java/...        misma estructura + e2e/
src/main/resources/      application.yml, application-dev.yml, application-docker.yml
src/test/resources/      application-test.yml
```

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
docker compose up -d          # levanta todo (reconstruye la imagen)
docker compose up -d db       # solo la base
docker compose logs -f app    # logs de la app
docker compose down           # apaga, conserva los datos
docker compose down -v        # apaga y borra el volumen
```

### Gradle

```bash
./gradlew bootRun                    # corre la app (perfil dev)
./gradlew build                      # compila, testea y verifica cobertura
./gradlew test                       # solo tests (+ reportes)
./gradlew cobertura                  # tests + reportes, imprime las rutas
./gradlew coberturaAbrir             # tests + reportes + los abre
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
