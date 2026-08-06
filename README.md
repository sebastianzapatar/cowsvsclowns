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
10. [CI/CD](#cicd)
11. [Estructura del proyecto](#estructura-del-proyecto)
12. [Comandos de referencia](#comandos-de-referencia)

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

**196 tests** en 20 archivos, todos en verde.

| Tipo | Tests | Archivos | Qué prueban |
|---|---:|---:|---|
| Unitarias | 108 | 9 | Servicios, mappers y excepciones, sin Spring |
| Integración | 57 | 7 | Controllers con MockMvc y repositorios con H2 |
| End to end | 30 | 3 | La app completa por HTTP real |
| Contexto | 1 | 1 | Que todos los beans se puedan construir |

Cobertura: **98.7% de líneas**, 95.8% de instrucciones, 87.5% de ramas, 100% de
clases.

### Correr los tests

```bash
./gradlew test                 # todos + genera los reportes
```

O solo una familia, que es lo útil mientras trabajas:

```bash
./gradlew pruebasUnitarias     # 108 tests, menos de 1s
./gradlew pruebasIntegracion   # 57 tests, ~1s
./gradlew pruebasE2E           # 30 tests, ~2s (levantan la app entera)
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
| `CowServiceTest` | Unitario | 16 |
| `GlobalExceptionHandlerIntegrationTest` | Integración | 14 |
| `OwnerServiceTest` | Unitario | 14 |
| `ClownServiceTest` | Unitario | 13 |
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
GitHub Actions  →  compila, corre las 196 pruebas, verifica cobertura
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
./gradlew test                       # todos los tests (+ reportes)
./gradlew pruebasUnitarias           # solo unitarias (108)
./gradlew pruebasIntegracion         # solo integración (57)
./gradlew pruebasE2E                 # solo end to end (30)
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
