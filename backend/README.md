# backend

API REST de la plataforma (Spring Boot 4 + PostgreSQL). Escucha en http://localhost:8080.

Los endpoints están documentados en [../docs/backend-api.md](../docs/backend-api.md).

## Requisitos

- Java 21 (JDK). No hace falta instalar Maven: el proyecto lleva el Maven Wrapper (`mvnw`).
- Docker Desktop abierto, para PostgreSQL y para los tests.
- Un archivo `.env` en la raíz del repo (copiar de `.env.example`). El backend lo lee al arrancar.

## Usar Java 21 en esta terminal

Ejecutar cada vez que se abre una terminal nueva:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

## Encender

1. Base de datos y servicio de IA (desde la raíz del repo):

```powershell
docker compose up -d postgres ai-service
```

2. Backend (desde `backend/`):

```powershell
.\mvnw.cmd spring-boot:run
```

3. Comprobar: http://localhost:8080/actuator/health debe devolver `{"status":"UP"}`.

El `ai-service` es opcional para arrancar: si no está, las reseñas nuevas quedan `PENDING` y se reintentan cuando vuelva.

Apagar: `Ctrl+C` en el backend y `docker compose stop postgres ai-service`. Los datos de PostgreSQL se conservan en el volumen `postgres-data`.

## Comandos (desde `backend/`)

| Comando | Qué hace | Equivalente en npm |
|---|---|---|
| `.\mvnw.cmd compile` | Compila el código | `npm run build` |
| `.\mvnw.cmd test` | Ejecuta los tests | `npm test` |
| `.\mvnw.cmd spring-boot:run` | Enciende el servicio | `npm start` |
| `.\mvnw.cmd clean` | Borra la carpeta `target/` | borrar `dist/` |
| `.\mvnw.cmd package` | Genera el `.jar` en `target/` | `npm run build` |
| `.\mvnw.cmd clean package -DskipTests` | Limpia y empaqueta sin tests | |

## Configuración

Está en `src/main/resources/application.properties`. Las credenciales salen del `.env`; el resto tiene valores por defecto.

| Variable (`.env`) | Descripción |
|---|---|
| `DB_URL` | Conexión JDBC. Por defecto `jdbc:postgresql://localhost:5432/reviews` |
| `DB_USER`, `DB_PASSWORD` | Credenciales de PostgreSQL. También las usa `docker-compose.yml` para crear la base de datos |
| `AI_SERVICE_URL` | URL del `ai-service`. Por defecto `http://localhost:8000` |

Los parámetros del cliente del `ai-service` (`ai-service.*`, timeouts de conexión y de lectura) y del job de reintentos (`reviews.retry.*`) se explican en [../docs/backend-api.md](../docs/backend-api.md).

## Estructura

```
src/main/java/com/reviewai/backend/
  ai/       Cliente del ai-service: llamada HTTP, respuesta y excepciones (ver docs/api.md)
  review/   Entidad, repositorio, servicio, controlador y job de reintentos
  web/      Manejo global de errores
src/main/resources/
  db/migration/   Migraciones de Flyway (V1, V2...), el esquema de la base de datos
```

Una petición pasa por `ReviewController` (HTTP) → `ReviewService` (reglas) → `AiServiceClient` (IA) y `ReviewRepository` (base de datos).

## Base de datos y migraciones

El esquema lo crea Flyway al arrancar, ejecutando en orden los archivos de `db/migration`. Hibernate solo comprueba que las entidades encajen (`ddl-auto=validate`).

Regla: **una migración ya aplicada no se edita nunca.** Los cambios van en un archivo nuevo (`V3__...sql`).

Ver el contenido de la base de datos:

```powershell
docker exec review-postgres psql -U reviews_user -d reviews -c "select id, status, stars, analysis_attempts from reviews order by id"
```

### pgAdmin (interfaz web)

Para ver y editar los datos con una interfaz gráfica hay un contenedor de pgAdmin, opcional. Desde la raíz del repo:

```powershell
docker compose --profile tools up -d pgadmin
```

- Abrir http://localhost:5050. No pide login: solo escucha en `127.0.0.1`.
- El servidor `review-postgres` ya aparece registrado (definido en `docker/pgadmin-servers.json`). Al abrirlo pide la contraseña: es `DB_PASSWORD` del `.env`.
- Las tablas están en Servers → review-postgres → Databases → reviews → Schemas → public → Tables.
- Dentro de pgAdmin el host de Postgres es `postgres`, no `localhost`: pgAdmin corre en su propio contenedor y los contenedores se ven por el nombre del servicio. `localhost:5432` es para programas fuera de Docker, como el backend.
- Apagar: `docker compose stop pgadmin`. Su configuración se guarda en el volumen `pgadmin-data`.

Usarlo para ver y corregir datos, pero no para cambiar el esquema: eso se hace con migraciones de Flyway. Y `flyway_schema_history` no se toca.

## En Docker

El backend también se puede ejecutar como contenedor, junto al resto del sistema (`docker compose up -d --build` desde la raíz, ver el [README raíz](../README.md)). Su `Dockerfile` tiene dos etapas: una con el JDK compila el proyecto con `mvnw` y otra, solo con el JRE de Java 21, ejecuta el `.jar`. La imagen no lleva Maven ni el código fuente y corre como un usuario sin privilegios.

Dentro de Docker, `docker-compose.yml` fija `DB_URL` y `AI_SERVICE_URL` con los nombres de los servicios (`postgres`, `ai-service`) y toma `DB_USER` y `DB_PASSWORD` de `.env`. Los `DB_URL` y `AI_SERVICE_URL` del `.env` son para ejecutarlo fuera de Docker. El contenedor no ejecuta los tests: necesitan Docker (Testcontainers) y se lanzan desde fuera con `.\mvnw.cmd test`.

## Tests

```powershell
.\mvnw.cmd test
```

Necesita Docker abierto: varios tests levantan un PostgreSQL real con Testcontainers. No necesitan el `ai-service` encendido.

| Test | Qué prueba |
|---|---|
| `ReviewApiIntegrationTests` | La API completa (HTTP, servicio y PostgreSQL real) con un ai-service falso |
| `AiServiceClientHttpTests` | El cliente HTTP real contra un servidor de verdad: protocolo HTTP/1.1, timeouts, errores |
| `AiServiceClientTests` | El cliente con respuestas simuladas, siguiendo `docs/api.md` |
| `ReviewServiceTests` | Las reglas de estado, los reintentos y el resumen de estadísticas, con repositorio y cliente falsos |
| `ReviewRepositoryTests` | Migraciones, mapeo de entidades (incluido JSON) y consultas, también las agrupaciones del panel |

`FakeAiService` es un servidor HTTP mínimo en un puerto libre que hace de ai-service en los tests.

## Cosas que conviene saber

- **HTTP/1.1 forzado.** El cliente HTTP de Java intenta por defecto subir a HTTP/2 incluso sin TLS (`Upgrade: h2c`), y uvicorn solo habla HTTP/1.1: descarta el cuerpo y responde 422. Está fijado en `AiServiceConfig` y cubierto por un test.
- **Sin transacción durante la llamada a la IA.** Se llama primero al `ai-service` y se guarda después, para no retener una conexión de base de datos mientras se espera.
- **Aviso de Mockito al ejecutar los tests.** Es normal con Java 21 y no afecta a los resultados.
