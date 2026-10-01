# TaskFlow API (Java + Spring Boot)

![CI](https://github.com/mbeltran93/taskflow-spring-boot/actions/workflows/ci.yml/badge.svg)

API REST (y SOAP) de gestion de tareas y proyectos tipo Trello/Jira reducido. Es parte de un
portafolio comparativo: el mismo dominio y el mismo contrato de API implementados con distintas
tecnologias backend; esta es la version en **Java 17 + Spring Boot 3**.

## Que hace

Permite:

- Registrar usuarios y autenticarlos con JWT.
- Crear proyectos y asignarles un dueño (`owner`).
- Crear tareas dentro de un proyecto, asignarlas a un usuario, ponerles fecha limite y moverlas
  entre los estados `TODO`, `IN_PROGRESS` y `DONE`.
- Listar y filtrar tareas por proyecto y/o estado.
- Consultar el dominio Project tambien por **SOAP** (ver seccion dedicada mas abajo), ademas del
  REST de siempre.

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
soap/         -> endpoint SOAP (Spring-WS) sobre el mismo dominio Project; ver "SOAP" mas abajo
tracing/      -> TraceIdFilter: traceId/requestId por request en el MDC; ver "Trazabilidad"
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

## SOAP (ademas de REST)

Para demostrar que el mismo servicio puede exponer **REST y SOAP** sobre el mismo dominio
(literalmente lo que pide un perfil que menciona "REST and SOAP APIs"), el dominio `Project` se
expone tambien por SOAP con [Spring-WS](https://docs.spring.io/spring-ws/docs/current/reference/),
**contract-first**:

- El contrato es el XSD en [`src/main/resources/xsd/projects.xsd`](src/main/resources/xsd/projects.xsd),
  con dos operaciones: `getProjectById` y `listProjects`.
- Las clases Java (JAXB) se generan a partir de ese XSD en build-time con el
  `jaxb2-maven-plugin` (`mvn generate-sources`, o automatico al compilar) - no se escriben a
  mano ni se versionan, viven en `target/generated-sources/jaxb`. El XSD es la unica fuente de
  verdad del contrato.
- `WebServiceConfig` expone el WSDL (auto-generado del XSD) en `http://localhost:8080/ws/projects.wsdl`
  y el endpoint SOAP en `http://localhost:8080/ws`.
- `ProjectSoapEndpoint` (`@Endpoint`) reusa el mismo `ProjectService` que usa el `ProjectController`
  REST: no hay logica de negocio duplicada, solo traduccion entre el modelo SOAP (JAXB) y el DTO
  interno.
- Un proyecto inexistente devuelve un SOAP Fault real (`ProjectNotFoundSoapException` +
  `@SoapFault`), no un 200 con error en el body.
- El endpoint SOAP es de solo lectura y publico (`/ws/**` esta permitido en `SecurityConfig`,
  igual que los `GET` de la API REST).

**Probado con un cliente SOAP real**, no solo "compila": `ProjectSoapEndpointIT` usa un
`WebServiceTemplate` de Spring-WS contra la app completa levantada en un puerto aleatorio
(serializa/deserializa XML de verdad sobre HTTP), cubriendo `getProjectById` (caso exitoso y
caso 404 -> SOAP Fault) y `listProjects`. Ademas se probo a mano con un envelope SOAP crudo por
curl:

```bash
curl -X POST http://localhost:8080/ws -H "Content-Type: text/xml" --data @- <<'EOF'
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:proj="http://taskflow.com/soap/projects">
  <soapenv:Header/>
  <soapenv:Body>
    <proj:getProjectByIdRequest>
      <proj:id>1</proj:id>
    </proj:getProjectByIdRequest>
  </soapenv:Body>
</soapenv:Envelope>
EOF
```

que devuelve (ejecutado de verdad contra la app corriendo con `docker compose up`):

```xml
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"><SOAP-ENV:Header/><SOAP-ENV:Body><ns2:getProjectByIdResponse xmlns:ns2="http://taskflow.com/soap/projects"><ns2:project><ns2:id>1</ns2:id><ns2:name>Smoke Project</ns2:name><ns2:description>demo</ns2:description><ns2:ownerId>1</ns2:ownerId><ns2:createdAt>2026-10-01T17:09:04.285Z</ns2:createdAt></ns2:project></ns2:getProjectByIdResponse></SOAP-ENV:Body></SOAP-ENV:Envelope>
```

y con un id inexistente responde un SOAP Fault real:

```xml
<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"><SOAP-ENV:Header/><SOAP-ENV:Body><SOAP-ENV:Fault><faultcode>SOAP-ENV:Client</faultcode><faultstring xml:lang="en">Proyecto no encontrado</faultstring></SOAP-ENV:Fault></SOAP-ENV:Body></SOAP-ENV:Envelope>
```

## Trazabilidad (traceId / requestId)

`TraceIdFilter` (un `Filter` registrado con la maxima precedencia, antes que la cadena de Spring
Security) intercepta cada request HTTP (REST y SOAP por igual, ya que es un filtro de servlet a
nivel `/*`):

1. Si el cliente ya manda un header `X-Request-Id` o `X-Trace-Id`, lo reusa (propagacion entre
   servicios).
2. Si no, genera un `UUID` nuevo.
3. Lo deja en el **MDC de SLF4J** (`traceId`) durante todo el ciclo de vida del request, asi
   **todos los logs que se emiten mientras se procesa ese request lo incluyen** (ver el patron
   en `logback-spring.xml`: `[traceId=%X{traceId}]`), y lo limpia al final (`finally`) para que
   no se filtre a otro request en el mismo hilo del pool.
4. Devuelve el mismo valor en la respuesta, en ambos headers (`X-Request-Id` y `X-Trace-Id`), para
   que el cliente pueda correlacionar.

Probado con requests reales (test de integracion `TraceIdFilterIT`, y a mano por curl contra
`docker compose up`). Ejemplo real de logs de la app corriendo en Docker, mostrando el traceId
propagado y uno generado, en REST y en SOAP:

```
2026-10-01 18:17:19.207 INFO  [http-nio-8080-exec-2] [traceId=demo-trace-abc123] c.t.controller.ProjectController - Listando todos los proyectos
2026-10-01 18:17:19.380 INFO  [http-nio-8080-exec-4] [traceId=daf989cc-ef38-407f-bc01-9c8ef8a657ad] c.t.controller.ProjectController - Listando todos los proyectos
2026-10-01 18:17:32.522 INFO  [http-nio-8080-exec-5] [traceId=soap-curl-demo-1] c.t.s.endpoint.ProjectSoapEndpoint - [SOAP] getProjectById id=3
```

La primera linea corresponde a `curl http://localhost:8080/api/projects -H "X-Request-Id: demo-trace-abc123"`
(se propaga el id del cliente); la segunda a la misma request sin header (se genera un UUID); la
tercera a una llamada SOAP con `X-Request-Id: soap-curl-demo-1` (el mismo filtro aplica a SOAP,
no solo a REST).

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
WSDL del servicio SOAP: `http://localhost:8080/ws/projects.wsdl`

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
| GET | `/ws/projects.wsdl` | No | WSDL del servicio SOAP (auto-generado del XSD) |
| POST | `/ws` | No | Endpoint SOAP: `getProjectByIdRequest` / `listProjectsRequest` (ver seccion "SOAP") |

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

## CI/CD (GitHub Actions)

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) corre automaticamente en cada `push` y
`pull_request` contra `main` (eso es lo que muestra el badge de arriba), con tres jobs
independientes:

| Job | Que hace |
|---|---|
| `test` | `mvn -B test`: compila y corre los 49+ tests (unitarios + integracion) |
| `docker-build` | `docker build` de la imagen (sin pushearla a ningun registry) para confirmar que el `Dockerfile` sigue sano; corre despues de `test` |
| `codeql` | Analisis de seguridad estatico con [CodeQL](https://codeql.github.com/) (`github/codeql-action`) sobre el codigo Java, gratis para repos publicos |

No hace falta ningun secreto ni configuracion extra para que corra: se activa solo al pushear
(este repo es publico, asi que CodeQL no requiere licencia). El YAML se valido sintacticamente
con `python -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"` (no se pudo
correr `act` localmente porque no esta instalado en esta maquina); los tres jobs tambien se
ejecutaron a mano en local con los mismos comandos que usa el workflow (`mvn test`, `docker
build`) para confirmar que son correctos antes de confiar en que GitHub Actions los corra igual.

## Despliegue en GCP

Documentacion + manifiestos **validados localmente**, sin desplegar de verdad (no hay cuenta/
billing de GCP configurado en esta maquina). Dos caminos, de mas simple a mas completo:

### Opcion simple: Cloud Run

Cloud Run es la forma mas directa de correr un container sin administrar cluster. Con
`gcloud` autenticado y un proyecto de GCP con billing habilitado:

```bash
# 1. Build + push de la imagen a Artifact Registry (una vez creado el repo con
#    `gcloud artifacts repositories create taskflow --repository-format=docker --location=us-central1`)
gcloud builds submit --tag us-central1-docker.pkg.dev/PROJECT_ID/taskflow/taskflow:latest

# 2. Crear la base de datos administrada (una vez)
gcloud sql instances create taskflow-db --database-version=POSTGRES_16 --tier=db-f1-micro --region=us-central1
gcloud sql databases create taskflow --instance=taskflow-db
gcloud sql users set-password postgres --instance=taskflow-db --password=<password-seguro>

# 3. Secretos (JWT_SECRET y credenciales de DB), nunca en el YAML ni en el repo
echo -n "<secreto-largo-y-aleatorio>" | gcloud secrets create taskflow-jwt-secret --data-file=-
echo -n "<password-de-postgres>" | gcloud secrets create taskflow-db-password --data-file=-

# 4. Deploy
gcloud run deploy taskflow \
  --image us-central1-docker.pkg.dev/PROJECT_ID/taskflow/taskflow:latest \
  --region us-central1 \
  --allow-unauthenticated \
  --add-cloudsql-instances PROJECT_ID:us-central1:taskflow-db \
  --set-env-vars "DB_URL=jdbc:postgresql:///taskflow?cloudSqlInstance=PROJECT_ID:us-central1:taskflow-db&socketFactory=com.google.cloud.sql.postgres.SocketFactory,DB_USERNAME=postgres" \
  --set-secrets "DB_PASSWORD=taskflow-db-password:latest,JWT_SECRET=taskflow-jwt-secret:latest"
```

Variables/secrets que necesita Cloud Run: `DB_URL`, `DB_USERNAME` (env vars, no sensibles salvo
por apuntar a la instancia), y `DB_PASSWORD`/`JWT_SECRET` como Secret Manager (`--set-secrets`,
nunca en texto plano en el comando de deploy en un pipeline real).

### Opcion GKE: Kubernetes

Para un despliegue mas parecido a lo que pide un cluster administrado (GKE), los manifiestos
estan en [`k8s/`](k8s/):

| Archivo | Contiene |
|---|---|
| `k8s/configmap.yaml` | Env vars **no sensibles**: `SERVER_PORT`, `JWT_EXPIRATION_MS`, `SPRING_PROFILES_ACTIVE` |
| `k8s/deployment.yaml` | 2 replicas de la app, probes de `/actuator/health`, variables sensibles inyectadas desde un `Secret` (`taskflow-secrets`, no versionado) |
| `k8s/service.yaml` | `Service` tipo `LoadBalancer` exponiendo el puerto 80 -> 8080 |

Comandos reales para un cluster GKE existente:

```bash
# 1. Cluster (una vez)
gcloud container clusters create-auto taskflow-cluster --region us-central1

# 2. Imagen en Artifact Registry (igual que en Cloud Run)
gcloud builds submit --tag us-central1-docker.pkg.dev/PROJECT_ID/taskflow/taskflow:latest

# 3. Secret con las variables sensibles (NO se versiona; se crea una sola vez por cluster/entorno)
kubectl create secret generic taskflow-secrets \
  --from-literal=DB_URL=jdbc:postgresql://<ip-privada-cloud-sql>:5432/taskflow \
  --from-literal=DB_USERNAME=taskflow \
  --from-literal=DB_PASSWORD=<password-seguro> \
  --from-literal=JWT_SECRET=<secreto-largo-y-aleatorio>

# 4. Apuntar deployment.yaml a la imagen real (reemplazar gcr.io/PROJECT_ID/taskflow:latest) y aplicar
kubectl apply -f k8s/

# 5. Ver la IP publica asignada por el LoadBalancer
kubectl get service taskflow-api
```

**Validacion local sin cluster real** (lo que pide la consigna: "no hace falta un cluster real"):
`kubectl apply --dry-run=client -f k8s/` necesita igual un API server para resolver el
`RESTMapper` (en esta maquina, sin ningun contexto de kubeconfig configurado, falla con
`connection refused` contra `localhost:8080`, el default de client-go cuando no hay cluster
alguno). Para validar los tres manifiestos sin desplegar de verdad se uso un cluster **local**
descartable con [`kind`](https://kind.sigs.k8s.io/) (Kubernetes-in-Docker, se crea y se borra en
minutos, sin tocar GCP):

```
$ kubectl apply --dry-run=client -f k8s/
configmap/taskflow-config created (dry run)
deployment.apps/taskflow-api created (dry run)
service/taskflow-api created (dry run)

$ kubectl apply --dry-run=server -f k8s/
configmap/taskflow-config created (server dry run)
deployment.apps/taskflow-api created (server dry run)
service/taskflow-api created (server dry run)
```

Ambos comandos (client-side y server-side) terminaron sin errores contra un API server real
(Kubernetes 1.31 via `kind`), confirmando que los tres YAML son schema-validos. Adicionalmente
se corrio una validacion 100% offline (sin ningun cluster, ni siquiera `kind`) con la libreria
`kubernetes-validate` (valida contra el OpenAPI de Kubernetes 1.30 embebido, en modo `strict`
que rechaza campos desconocidos) para los tres archivos, con resultado `OK` en los tres.

## Limitaciones conocidas

- El SOAP de `Project` es de solo lectura (`getProjectById`/`listProjects`) y no requiere JWT,
  igual que los `GET` del REST: el objetivo es demostrar la convivencia REST+SOAP sobre el mismo
  dominio, no migrar todo el contrato a SOAP.
- El despliegue a GCP (Cloud Run/GKE) es documentacion + manifiestos validados localmente (con
  `kind` y `kubernetes-validate`, ver seccion "Despliegue en GCP"): no se desplego de verdad
  porque esta maquina no tiene cuenta/billing de GCP configurado.
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
