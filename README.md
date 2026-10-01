# TaskFlow API (Java + Spring Boot)

API REST de gestion de tareas y proyectos tipo Trello/Jira reducido. Es parte de un portafolio
comparativo: el mismo dominio y el mismo contrato de API implementados con distintas tecnologias
backend; esta es la version en **Java 17 + Spring Boot 3**.

## Que hace

Permite:

- Registrar usuarios y autenticarlos con JWT.
- Crear proyectos y asignarles un dueño (`owner`).
- Crear tareas dentro de un proyecto, asignarlas a un usuario, ponerles fecha limite y moverlas
  entre los estados `TODO`, `IN_PROGRESS` y `DONE`.
- Listar y filtrar tareas por proyecto y/o estado.

Las lecturas (`GET`) son publicas; las escrituras (`POST`/`PUT`/`PATCH`/`DELETE`) requieren un
JWT valido en el header `Authorization: Bearer <token>`.

## Arquitectura

Monolito en capas:

```
controller/   -> expone los endpoints REST, valida el request (Bean Validation) y delega al service
service/      -> logica de negocio y transacciones, orquesta repositorios
repository/   -> Spring Data JPA, acceso a PostgreSQL
entity/       -> entidades JPA (User, Project, Task)
dto/          -> records de entrada/salida, nunca se exponen las entidades directamente
security/     -> JwtService (genera/valida tokens) y JwtAuthFilter (filtro que autentica cada request)
config/       -> SecurityConfig (reglas de autorizacion) y OpenApiConfig (Swagger)
exception/    -> excepciones de dominio + @RestControllerAdvice centralizado (GlobalExceptionHandler)
```

Puntos de diseño:

- **JWT stateless**: no hay sesiones ni cookies; cada request trae su propio token y
  `JwtAuthFilter` reconstruye la autenticacion en el `SecurityContext`.
- **Passwords con BCrypt**, nunca en texto plano ni en las respuestas.
- **Flyway** controla el esquema (`src/main/resources/db/migration`); en produccion/docker
  `ddl-auto` esta en `validate`, nunca `update`.
- **Manejo de errores centralizado**: `ResourceNotFoundException` -> 404,
  `DuplicateResourceException` -> 409, `InvalidCredentialsException` -> 401, errores de
  validacion de Bean Validation -> 400 con el detalle de cada campo.
- **DTOs como Java records**: inmutables, sin boilerplate de getters/setters.

## Como correrlo

Requisitos: Docker y Docker Compose.

```bash
docker compose up --build
```

Esto levanta:

- `db`: PostgreSQL 16 (puerto `5433` en el host para no chocar con otro Postgres local; `5432`
  puerto interno de siempre para la red de Docker).
- `app`: la API en `http://localhost:8080`, esperando a que Postgres este saludable y aplicando
  las migraciones de Flyway automaticamente al arrancar.

Swagger UI: `http://localhost:8080/swagger-ui.html`
Salud: `http://localhost:8080/actuator/health`

### Correrlo sin Docker (desarrollo local)

Necesitas un Postgres local (o cambiar `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`):

```bash
mvn spring-boot:run
```

Variables de entorno relevantes (todas tienen default para dev):

| Variable | Default | Descripcion |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/taskflow` | conexion a Postgres |
| `DB_USERNAME` / `DB_PASSWORD` | `taskflow` / `taskflow` | credenciales de la DB |
| `JWT_SECRET` | clave de ejemplo (cambiarla siempre en producción) | secreto HMAC para firmar los JWT |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | vigencia del token |
| `SERVER_PORT` | `8080` | puerto HTTP |

## Como correr los tests

```bash
mvn test
```

Incluye:

- **Tests unitarios** (JUnit 5 + Mockito) de la capa `service`: reglas de negocio, validaciones
  de existencia de relaciones (proyecto/owner/assignee), hashing de password, generacion de
  tokens, etc. (`src/test/java/com/taskflow/service`).
- **Tests de integracion** de los controllers con `MockMvc` (`src/test/java/com/taskflow/controller`,
  sufijo `*IT`): levantan el contexto completo de Spring (seguridad incluida) contra una base
  H2 en memoria en modo compatibilidad PostgreSQL, cubriendo el flujo real
  registro -> login -> crear proyecto -> crear tarea -> cambiar status -> filtrar -> borrar, mas
  los casos de error (401/403/404/409/400).
- **Tests parametrizados de validacion** (`RequestValidationParamIT`): recorren con
  `@ParameterizedTest` muchas combinaciones invalidas de cada DTO de entrada (nombre vacio, email
  con formato invalido, password corta, campos que exceden el `@Size` maximo, ids nulos, etc.)
  para los endpoints de registro, login, proyectos y tareas, verificando en cada caso el 400 y
  el campo que aparece en el detalle del error.
- **Test de concurrencia** (`ConcurrentTaskOperationsIT`): dispara ~20 requests en paralelo con
  un `ExecutorService` (creacion de tareas sobre el mismo proyecto y cambios de status sobre la
  misma tarea) para verificar que no se pierden escrituras, no se generan ids duplicados y el
  servidor responde de forma consistente bajo acceso simultaneo.

> Nota sobre Testcontainers: la consigna original pedia usar Testcontainers con un Postgres real
> "si se puede". En esta maquina Windows el cliente `docker-java` que trae Testcontainers no
> logra hablar con el pipe que expone Docker Desktop (`BadRequestException Status 400` al listar
> el daemon), aunque `docker` y `docker compose` funcionan perfectamente. Se volvio a probar el
> 2026-10-01, despues de liberar espacio en disco y reinstalar/reiniciar Docker Desktop (ahora
> Docker Desktop 4.56 / Engine 29.1.3), incluyendo con la ultima version de Testcontainers
> (1.21.3) y probando explicitamente los pipes `docker_cli` y `docker_engine`: el mismo error
> persiste. No es entonces un problema de espacio en disco sino de como esta instalacion de
> Docker Desktop expone el daemon por named pipe al cliente Java (`docker compose` funciona
> porque usa el binario oficial de Docker, no `docker-java`). Se mantiene la alternativa que la
> propia consigna habilita explicitamente: H2 + `@AutoConfigureMockMvc`, que da la misma
> cobertura de los controllers sin depender de esa integracion especifica del entorno.

## Documentacion de la API

Con la app corriendo, Swagger UI queda en `http://localhost:8080/swagger-ui.html` (spec OpenAPI
en `http://localhost:8080/v3/api-docs`).

### Tabla de endpoints

| Metodo | Ruta | Auth | Descripcion |
|---|---|---|---|
| POST | `/api/auth/login` | No | Login, devuelve `{ token }` |
| GET | `/api/users` | No | Lista usuarios |
| GET | `/api/users/{id}` | No | Busca un usuario por id |
| POST | `/api/users` | No | Registra un usuario nuevo (no forma parte del contrato original del portafolio, pero es necesaria para poder loguearse) |
| GET | `/api/projects` | No | Lista proyectos |
| GET | `/api/projects/{id}` | No | Busca un proyecto por id |
| POST | `/api/projects` | JWT | Crea un proyecto |
| PUT | `/api/projects/{id}` | JWT | Actualiza un proyecto |
| DELETE | `/api/projects/{id}` | JWT | Elimina un proyecto |
| GET | `/api/tasks?projectId=&status=` | No | Lista tareas, filtrables por proyecto y/o estado |
| GET | `/api/tasks/{id}` | No | Busca una tarea por id |
| POST | `/api/tasks` | JWT | Crea una tarea |
| PUT | `/api/tasks/{id}` | JWT | Actualiza una tarea |
| PATCH | `/api/tasks/{id}/status` | JWT | Cambia solo el estado de una tarea |
| DELETE | `/api/tasks/{id}` | JWT | Elimina una tarea |

### Ejemplo de uso con curl

```bash
# 1. Registrarse
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Maria","email":"maria@taskflow.dev","password":"password123"}'

# 2. Login
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"maria@taskflow.dev","password":"password123"}' | jq -r .token)

# 3. Crear un proyecto (requiere JWT)
curl -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"TaskFlow","description":"demo","ownerId":1}'

# 4. Crear una tarea
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"title":"Escribir README","projectId":1}'

# 5. Cambiar el estado de la tarea
curl -X PATCH http://localhost:8080/api/tasks/1/status \
  -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"status":"IN_PROGRESS"}'
```

### Coleccion de Postman

El archivo [`postman_collection.json`](./postman_collection.json) en la raiz del repo tiene el
mismo flujo de arriba ya armado, mas un par de casos de error:

1. En Postman: **File > Import** y seleccionar `postman_collection.json` (o arrastrarlo a la
   ventana de Postman).
2. La coleccion trae su variable `baseUrl` en `http://localhost:8080`; si la API corre en otro
   puerto/host, editarla en **Collection > Variables**.
3. Correr la carpeta **"Flujo completo"** de arriba hacia abajo (a mano, request por request, o
   con el boton **Run** del Collection Runner). El request **"2. Login"** tiene un test script
   que guarda el JWT devuelto en la variable de coleccion `{{token}}`, y los requests siguientes
   que necesitan autenticacion ya la usan solos en su header `Authorization: Bearer {{token}}` -
   no hay que copiar/pegar el token a mano. De la misma forma, `ownerId`, `projectId` y `taskId`
   se van completando solos a medida que cada request crea el recurso correspondiente.
4. La carpeta **"Casos de error"** tiene un POST sin token (401/403 esperado) y un GET a un id
   inexistente (404 esperado), independientes del flujo principal.

Tambien se puede correr toda la coleccion desde la terminal con
[Newman](https://www.npmjs.com/package/newman) (no requiere instalarlo, `npx` lo descarga al vuelo):

```bash
npx newman run postman_collection.json
```

## Modelo de datos

```
User        id, name, email (unico), passwordHash, createdAt
Project     id, name, description, ownerId (FK -> User), createdAt
Task        id, title, description, status (TODO|IN_PROGRESS|DONE),
            projectId (FK -> Project), assigneeId (FK -> User, nullable),
            dueDate (nullable), createdAt
```

Las migraciones Flyway (`V1__init_schema.sql`) crean las tablas, las foreign keys
(`ON DELETE CASCADE` para project->task y owner->project, `ON DELETE SET NULL` para el
assignee de una tarea) y los indices sobre las columnas mas consultadas.

## Limitaciones conocidas

- No hay roles/permisos finos: cualquier usuario autenticado puede crear/editar/borrar cualquier
  proyecto o tarea (no solo las propias). Se prioriza dejar clara la mecanica de JWT sobre un
  modelo de autorizacion granular.
- El endpoint de registro (`POST /api/users`) es publico y no estaba en el contrato original del
  portafolio; se agrego porque sin el no habria forma de crear cuentas para loguearse.
- Los tests de integracion usan H2 en modo compatibilidad PostgreSQL en vez de un Postgres real
  via Testcontainers, por la incompatibilidad de entorno descripta mas arriba (reconfirmada el
  2026-10-01 tras reinstalar Docker Desktop).
- No hay paginación en los listados (`GET /api/users`, `/api/projects`, `/api/tasks`): para el
  alcance de este portafolio se devuelven completos.
- No hay refresh tokens: el JWT expira (24h por default) y hay que volver a loguearse.
