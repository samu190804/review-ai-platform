# backend

API REST (Spring Boot + PostgreSQL). Escucha en http://localhost:8080.

## Usar Java 21 en esta terminal

Ejecutar cada vez que se abre una terminal nueva:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

## Comandos (desde `backend/`)

| Comando | Qué hace | Equivalente en npm |
|---|---|---|
| `.\mvnw.cmd compile` | Compila el código | `npm run build` |
| `.\mvnw.cmd test` | Ejecuta los tests | `npm test` |
| `.\mvnw.cmd spring-boot:run` | Enciende el servicio | `npm start` |
| `.\mvnw.cmd clean` | Borra la carpeta `target/` | borrar `dist/` |
| `.\mvnw.cmd package` | Genera el `.jar` en `target/` | `npm run build` |
| `.\mvnw.cmd clean package -DskipTests` | Limpia y empaqueta sin tests | |

## Encender

1. Base de datos (desde la raíz del repo, con Docker Desktop abierto):

```powershell
docker compose up -d postgres
```

2. Backend (desde `backend/`):

```powershell
.\mvnw.cmd spring-boot:run
```

3. Comprobar: http://localhost:8080/actuator/health debe devolver `{"status":"UP"}`.

Apagar la base de datos: `docker compose stop postgres` (los datos se conservan en el volumen).
