# review-ai-platform

Plataforma de análisis de reseñas con inteligencia artificial. Se le da el texto de una reseña y el sistema la modera automáticamente y le asigna una puntuación de 1 a 5 estrellas. Pensada como herramienta para quien recibe reseñas y quiere analizarlas, no como un portal donde se publican opiniones: por eso una reseña es solo un texto, sin usuarios ni catálogo de productos. Todo funciona en local, sin servicios externos de pago ni cuentas en la nube.

Es un proyecto de portfolio, organizado como monorepo.

## Estado

En desarrollo. Fases 0 a 3 completadas: el backend y el frontend funcionan y tienen tests (Spring Security se añadirá al final). La evaluación de los modelos (fase 4) está hecha, con sus resultados en [evaluation/](evaluation/README.md). Ver el plan más abajo.

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
| `frontend/` | Aplicación Angular con Tailwind: analizar una reseña e historial con filtro y paginación |
| `evaluation/` | Dataset etiquetado a mano y notebook que mide la calidad de los modelos ([README](evaluation/README.md)) |
| `docs/api.md` | Contrato JSON entre `ai-service` y `backend` (fuente de verdad) |
| `docs/backend-api.md` | API REST del backend, la que usa el frontend |

## Decisiones de diseño

- **Todo en local.** No se usan APIs de pago ni servicios en la nube.
- **Puntuación.** Modelo preentrenado `nlptown/bert-base-multilingual-uncased-sentiment`: un BERT multilingüe ajustado para clasificar reseñas de 1 a 5 estrellas, que admite varios idiomas, entre ellos el español. Se usa tal cual, sin reentrenar. Se carga una sola vez al importar el módulo, para no pagar el coste de carga en cada petición.
- **Moderación híbrida.** Reglas simples (longitud mínima, enlaces, spam) combinadas con Detoxify `multilingual`, un modelo XLM-RoBERTa (familia BERT/RoBERTa) preentrenado para detectar toxicidad en varios idiomas, con un umbral de toxicidad configurable (0.90 por defecto, elegido en la evaluación). Las reglas son rápidas y explicables; el modelo cubre lo que las reglas no detectan.
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

## Evaluación de los modelos

Se midieron los dos modelos con 200 reseñas en español etiquetadas a mano. El dataset incluye a propósito insultos, amenazas y contenido sexual, para poder medir la moderación. El detalle, con el código y las gráficas, está en [evaluation/](evaluation/README.md).

- **Puntuación.** El 63.6 % de las reseñas recibe la puntuación exacta y el 96.5 % se queda a una estrella como máximo (error medio de 0.42 estrellas). Va bien en 1★ y 5★ y peor en las intermedias.
- **Moderación.** Con el umbral de toxicidad en 0.90, precision 0.93 y recall 0.80. El umbral se subió desde 0.65 para no bloquear reseñas legítimas. El punto débil son las amenazas, que Detoxify casi no detecta en español.

Son resultados con un dataset pequeño, redactado y etiquetado por una sola persona; las limitaciones están explicadas en el README de `evaluation/`.

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

Por último, el frontend desde `frontend/` (más detalles en su [README](frontend/README.md)):

```powershell
npm install   # solo la primera vez
npm start
```

- Frontend: http://localhost:4200. Reenvía `/api` al backend con un proxy de desarrollo.

Opcional, para ver y editar la base de datos con una interfaz web (pgAdmin en http://localhost:5050):

```powershell
docker compose --profile tools up -d pgadmin
```

## Ideas pendientes

- Carga masiva: subir un CSV con muchas reseñas y procesarlas por lotes.
- Decidir qué muestra la vista pública. Hoy el listado devuelve también las reseñas rechazadas, con su texto.
- Reintento manual de una reseña `PENDING` que agotó sus intentos.
- Spring Security, al final.
- Frontend: tras enviar una reseña que queda `PENDING`, enlazar al historial filtrado por "Pendientes" (para eso el filtro tendría que leerse de la URL). Hoy el historial no se actualiza solo: hay que pulsar "Actualizar".
- Frontend: un texto de solo emojis puede pasar el mínimo de 10 caracteres en JavaScript (UTF-16) y no en Python, y acabar en un 502.
- El límite de 2000 caracteres se cuenta distinto en Java (UTF-16) y en Python (caracteres): un texto con emojis justo en el límite podría dar un 502.

## Configuración y secretos

Las variables de entorno van en un archivo `.env`, que está en `.gitignore` y no se sube al repositorio. Nunca se deben commitear claves ni datos sensibles.
