# review-ai-platform

Plataforma de análisis de reseñas con inteligencia artificial. Se le da el texto de una reseña y el sistema la modera automáticamente y le asigna una puntuación de 1 a 5 estrellas. Pensada como herramienta para quien recibe reseñas y quiere analizarlas, no como un portal donde se publican opiniones: por eso una reseña es solo un texto, sin usuarios ni catálogo de productos. Todo funciona en local, sin servicios externos de pago ni cuentas en la nube.

Es un proyecto de portfolio, organizado como monorepo.

## Estado

En desarrollo. Fases 0 a 2 completadas (el backend funciona y tiene tests; Spring Security se añadirá al final). Fase actual: 3 (frontend Angular). Ver el plan más abajo.

## Arquitectura

```
Frontend (Angular)  -->  Backend (Spring Boot + PostgreSQL)  -->  AI service (FastAPI)
```

- El frontend solo habla con el backend.
- El backend guarda las reseñas, gestiona su estado y llama al microservicio de IA.
- El microservicio de IA analiza un texto y devuelve la moderación y la puntuación. No guarda nada.

## Estructura del repositorio

| Carpeta | Contenido |
|---|---|
| `ai-service/` | Microservicio de IA en Python con FastAPI |
| `backend/` | API REST en Spring Boot con PostgreSQL y validación (la seguridad llegará al final) |
| `frontend/` | Aplicación Angular |
| `evaluation/` | Notebooks y scripts para medir la calidad del modelo |
| `docs/api.md` | Contrato JSON entre `ai-service` y `backend` (fuente de verdad) |
| `docs/backend-api.md` | API REST del backend, la que usará el frontend |

## Decisiones de diseño

- **Todo en local.** No se usan APIs de pago ni servicios en la nube.
- **Puntuación.** Modelo `nlptown/bert-base-multilingual-uncased-sentiment`, que clasifica de 1 a 5 estrellas y admite varios idiomas. Se carga una sola vez al importar el módulo, para no pagar el coste de carga en cada petición.
- **Moderación híbrida.** Reglas simples (longitud mínima, enlaces, spam) combinadas con Detoxify multilingual, con un umbral de toxicidad configurable. Las reglas son rápidas y explicables; el modelo cubre lo que las reglas no detectan.
- **Reseña marcada, sin puntuación.** Si la moderación marca una reseña, `rating` devuelve `null` y no se ejecuta el modelo de estrellas. Así se evita gastar cómputo en texto que no se va a publicar.
- **Estados de reseña.** Los gestiona Spring: `PENDING` pasa a `APPROVED` o `REJECTED`.
- **Las reseñas rechazadas se guardan.** Sirven para auditar la moderación y, en la fase 4, para medir el modelo y elegir el umbral.
- **Tolerancia a fallos de la IA.** Si el `ai-service` no responde, la reseña se guarda como `PENDING` y un job programado la reintenta, con un máximo de intentos. No se pierde ningún dato por una caída.
- **Esquema versionado con Flyway.** La base de datos se crea con migraciones SQL del repositorio, no con el esquema autogenerado de Hibernate.
- **Sin modelo de dominio extra.** Una reseña es solo texto más su análisis: ni usuarios ni productos. El objetivo del proyecto es la IA y el backend.
- **Versión del modelo.** Cada respuesta incluye `model_version`, para poder comparar modelos durante la evaluación.

## Plan por fases

0. Estructura y contrato
1. Microservicio FastAPI y Dockerfile
2. Backend Spring Boot y PostgreSQL
3. Frontend Angular
4. Evaluación del modelo: MAE, accuracy y matriz de confusión. El umbral de toxicidad se elige con un CSV propio de reseñas etiquetadas.
5. Panel de análisis, tests, Docker Compose, CI y despliegue

## Contrato de la API de IA

Resumen del endpoint principal. El detalle completo está en [docs/api.md](docs/api.md).

```
POST /analyze
{ "text": "La comida estaba fría y tardaron una hora." }
```

La respuesta incluye `moderation`, `rating` y `model_version`.

## Cómo ejecutarlo

Requisitos: Docker Desktop y Java 21. Copiar `.env.example` a `.env` y cambiar la contraseña.

Desde la raíz del repo, el servicio de IA y la base de datos:

```powershell
docker compose up -d --build postgres ai-service
```

La primera vez tarda, porque descarga los modelos. Después, el backend desde `backend/` (los comandos y la configuración de Java están en su [README](backend/README.md)):

```powershell
.\mvnw.cmd spring-boot:run
```

- AI service: http://localhost:8000 (`/health`)
- Backend: http://localhost:8080 (`/actuator/health`, `/api/reviews`)

El frontend todavía no existe.

## Ideas pendientes

- Carga masiva: subir un CSV con muchas reseñas y procesarlas por lotes.
- Decidir qué muestra la vista pública. Hoy el listado devuelve también las reseñas rechazadas, con su texto.
- Reintento manual de una reseña `PENDING` que agotó sus intentos.
- Spring Security, al final.
- El límite de 2000 caracteres se cuenta distinto en Java (UTF-16) y en Python (caracteres): un texto con emojis justo en el límite podría dar un 502.

## Configuración y secretos

Las variables de entorno van en un archivo `.env`, que está en `.gitignore` y no se sube al repositorio. Nunca se deben commitear claves ni datos sensibles.
