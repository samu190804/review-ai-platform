# API del backend

API REST de `backend` (Spring Boot), la que consumirá el frontend. No confundir con [api.md](api.md), que es el contrato entre el backend y el `ai-service`.

- Formato: JSON, UTF-8.
- URL base en local: `http://localhost:8080`.
- Sin autenticación por ahora (Spring Security se añadirá al final).
- Las fechas van en ISO 8601 y en UTC (`2026-10-09T11:14:03.030235Z`).

## Estados de una reseña

| Estado | Significado |
|---|---|
| `APPROVED` | La moderación no la marcó. Tiene `stars` y `confidence`. |
| `REJECTED` | La moderación la marcó. `stars` y `confidence` son `null`. `moderationReasons` explica por qué. |
| `PENDING` | El `ai-service` no respondió. Aún no tiene análisis. Un job la reintenta (ver más abajo). |

Las reseñas rechazadas **se guardan igualmente**: sirven para auditar y para la evaluación del modelo.

## POST /api/reviews

Crea una reseña, la analiza con el `ai-service` y la guarda.

```json
{ "text": "La comida estaba fría y tardaron una hora." }
```

| Campo | Tipo | Obligatorio | Restricciones |
|---|---|---|---|
| `text` | string | sí | No vacío, entre 10 y 2000 caracteres |

### Respuesta 201

Cabecera `Location: /api/reviews/{id}`. Cuerpo: la reseña creada (ver [Objeto reseña](#objeto-reseña)).

Devuelve 201 **también cuando el estado es `PENDING`**: la reseña sí se creó, solo falta su análisis.

```json
{
  "id": 1,
  "text": "La comida estaba fría y tardaron una hora.",
  "status": "APPROVED",
  "stars": 1,
  "confidence": 0.44,
  "modelVersion": "nlptown-bert-v1",
  "moderationReasons": [],
  "moderationScores": {
    "toxicity": 0.001,
    "severe_toxicity": 0.0,
    "obscene": 0.001,
    "identity_attack": 0.0,
    "insult": 0.0,
    "threat": 0.0,
    "sexual_explicit": 0.0
  },
  "createdAt": "2026-10-09T11:14:03.030235Z"
}
```

Reseña rechazada:

```json
{
  "id": 2,
  "text": "Eres un idiota y un inútil, lárgate de aquí imbécil.",
  "status": "REJECTED",
  "stars": null,
  "confidence": null,
  "modelVersion": "nlptown-bert-v1",
  "moderationReasons": ["lenguaje tóxico"],
  "moderationScores": { "toxicity": 0.993, "insult": 0.445, "...": 0.0 },
  "createdAt": "2026-10-09T11:14:03.474022Z"
}
```

Reseña pendiente (el `ai-service` no respondió):

```json
{
  "id": 3,
  "text": "La comida estaba fría y tardaron una hora.",
  "status": "PENDING",
  "stars": null,
  "confidence": null,
  "modelVersion": null,
  "moderationReasons": null,
  "moderationScores": null,
  "createdAt": "2026-10-09T11:14:27.665549Z"
}
```

### Errores

| Código | Cuándo |
|---|---|
| 400 | Texto ausente, vacío, de menos de 10 o más de 2000 caracteres, o JSON mal formado |
| 502 | El `ai-service` rechazó la petición con 422. Indica un desajuste entre las validaciones de ambos servicios (un bug), no un fallo del cliente |

## GET /api/reviews

Lista reseñas, paginadas y de más reciente a más antigua.

| Parámetro | Tipo | Por defecto | Descripción |
|---|---|---|---|
| `status` | `PENDING`, `APPROVED` o `REJECTED` | todas | Filtra por estado |
| `page` | entero | 0 | Número de página, empieza en 0 |
| `size` | entero | 20 | Reseñas por página |

Ejemplo: `GET /api/reviews?status=APPROVED&page=0&size=5`

### Respuesta 200

```json
{
  "content": [ { "id": 2, "...": "..." }, { "id": 1, "...": "..." } ],
  "page": 0,
  "size": 5,
  "totalElements": 2,
  "totalPages": 1
}
```

`content` es una lista de [reseñas](#objeto-reseña).

### Errores

| Código | Cuándo |
|---|---|
| 400 | `status` no es uno de los tres valores |

## GET /api/reviews/{id}

Devuelve una reseña ([Objeto reseña](#objeto-reseña)).

| Código | Cuándo |
|---|---|
| 200 | Existe |
| 404 | No existe |

## Objeto reseña

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | entero | Identificador |
| `text` | string | Texto original |
| `status` | string | `PENDING`, `APPROVED` o `REJECTED` |
| `stars` | entero o `null` | De 1 a 5. `null` si está `REJECTED` o `PENDING` |
| `confidence` | número o `null` | De 0 a 1. Probabilidad que el modelo asigna a `stars` |
| `modelVersion` | string o `null` | Versión del modelo que hizo el análisis. `null` si está `PENDING` |
| `moderationReasons` | array de string o `null` | Motivos de la moderación (lista en [api.md](api.md)). Vacío si no se marcó; `null` si está `PENDING` |
| `moderationScores` | objeto o `null` | Puntuación de 0 a 1 por categoría de Detoxify. `null` si está `PENDING` |
| `createdAt` | string | Fecha de creación |

Los nombres van en `camelCase` aquí, mientras que el `ai-service` usa `snake_case` (`model_version`). Es a propósito: cada API sigue la convención de su ecosistema.

## Formato de los errores

Todos los errores siguen el estándar [ProblemDetail (RFC 9457)](https://www.rfc-editor.org/rfc/rfc9457):

```json
{
  "title": "Not Found",
  "status": 404,
  "detail": "No existe la reseña con id 9999",
  "instance": "/api/reviews/9999"
}
```

Los errores de validación añaden `errors`, con el mensaje de cada campo:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "La petición no es válida",
  "instance": "/api/reviews",
  "errors": { "text": "El texto debe tener entre 10 y 2000 caracteres" }
}
```

## Reintentos de reseñas PENDING

Un job (`ReviewRetryJob`) reanaliza periódicamente las reseñas `PENDING`. Se configura en `application.properties`:

| Propiedad | Por defecto | Descripción |
|---|---|---|
| `reviews.retry.initial-delay` | `30s` | Espera tras arrancar el backend |
| `reviews.retry.interval` | `60s` | Espera entre el fin de una ejecución y el inicio de la siguiente |
| `reviews.retry.batch-size` | `20` | Máximo de reseñas por ejecución |
| `reviews.retry.max-attempts` | `5` | Intentos fallidos tras los cuales se deja de reintentar |

Reglas:

- Las reseñas con menos intentos fallidos van primero; a igualdad, las más antiguas.
- Si el `ai-service` falla, el lote se corta: no tiene sentido llamarlo para las demás y así no se gastan sus intentos.
- Una reseña que agota sus intentos se queda `PENDING` y el job la ignora.
- El job está pensado para **una sola instancia** del backend.

## Otros endpoints

| Endpoint | Descripción |
|---|---|
| `GET /actuator/health` | Estado del backend: `{"status":"UP"}` |

## Probar con curl (PowerShell)

Con el backend, PostgreSQL y el `ai-service` encendidos. En PowerShell hay que usar `curl.exe`, porque `curl` a secas es un alias de otro comando. Para evitar problemas con tildes, el cuerpo va en un archivo:

```powershell
Set-Content body.json -Value '{"text":"La comida estaba fría y tardaron una hora."}' -Encoding utf8NoBOM
curl.exe -i -X POST http://localhost:8080/api/reviews -H "Content-Type: application/json" --data-binary "@body.json"
curl.exe "http://localhost:8080/api/reviews?status=APPROVED&size=5"
curl.exe http://localhost:8080/api/reviews/1
```
