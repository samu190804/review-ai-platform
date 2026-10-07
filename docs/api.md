# Contrato de la API del servicio de IA

Contrato JSON entre `ai-service` (FastAPI) y `backend` (Spring Boot). Es la fuente de verdad: si el código y este documento difieren, se corrige el que esté mal y se deja este archivo actualizado.

- Formato: JSON, codificación UTF-8, cabecera `Content-Type: application/json`.
- URL base en local: `http://localhost:8000`.
- El servicio no guarda estado. Cada petición es independiente.

## POST /analyze

Modera una reseña y, si es aceptable, le asigna una puntuación de 1 a 5 estrellas.

### Petición

```json
{ "text": "La comida estaba fría y tardaron una hora." }
```

| Campo | Tipo | Obligatorio | Restricciones |
|---|---|---|---|
| `text` | string | si | Entre 10 y 2000 caracteres |

### Respuesta 200: reseña aceptada

```json
{
  "moderation": {
    "flagged": false,
    "reasons": [],
    "scores": {
      "toxicity": 0.001,
      "severe_toxicity": 0.0,
      "obscene": 0.0,
      "identity_attack": 0.0,
      "insult": 0.001,
      "threat": 0.0,
      "sexual_explicit": 0.0
    }
  },
  "rating": { "stars": 2, "confidence": 0.71 },
  "model_version": "nlptown-bert-v1"
}
```

### Respuesta 200: reseña marcada

Si la moderación marca la reseña, `rating` es `null` y no se ejecuta el modelo de estrellas.

```json
{
  "moderation": {
    "flagged": true,
    "reasons": ["lenguaje tóxico", "insulto"],
    "scores": {
      "toxicity": 0.96,
      "severe_toxicity": 0.12,
      "obscene": 0.41,
      "identity_attack": 0.03,
      "insult": 0.91,
      "threat": 0.01,
      "sexual_explicit": 0.02
    }
  },
  "rating": null,
  "model_version": "nlptown-bert-v1"
}
```

### Campos de la respuesta

| Campo | Tipo | Descripción |
|---|---|---|
| `moderation.flagged` | boolean | `true` si hay al menos un motivo en `reasons` |
| `moderation.reasons` | array de string | Motivos por los que se marcó. Vacío si `flagged` es `false` |
| `moderation.scores` | objeto | Puntuación de 0 a 1 por categoría de Detoxify. Siempre incluye las 7 categorías |
| `rating` | objeto o `null` | `null` cuando `flagged` es `true` |
| `rating.stars` | entero | De 1 a 5 |
| `rating.confidence` | número | De 0 a 1, probabilidad que el modelo asigna a esa puntuación |
| `model_version` | string | Identificador de la versión del modelo de puntuación, para comparar modelos en la evaluación |

### Motivos posibles en `reasons`

Pueden aparecer varios a la vez. Los textos son estables: el backend puede mostrarlos o compararlos.

| Origen | Motivo |
|---|---|
| Regla | `demasiado corta` |
| Regla | `contiene enlaces` |
| Regla | `posible spam` |
| Detoxify `toxicity` | `lenguaje tóxico` |
| Detoxify `severe_toxicity` | `toxicidad severa` |
| Detoxify `obscene` | `lenguaje obsceno` |
| Detoxify `identity_attack` | `ataque a la identidad` |
| Detoxify `insult` | `insulto` |
| Detoxify `threat` | `amenaza` |
| Detoxify `sexual_explicit` | `contenido sexual explícito` |

Un motivo de Detoxify aparece cuando el score de su categoría es mayor o igual que el umbral configurable `TOXIC_THRESHOLD` (por defecto 0.65).

### Errores

| Código | Cuándo | Cuerpo |
|---|---|---|
| 422 | El cuerpo no cumple las restricciones: falta `text`, tipo incorrecto, o longitud fuera de 10 a 2000 caracteres | Detalle de validación de FastAPI |
| 500 | Error interno inesperado | `{ "detail": "Error interno del servicio de IA" }` |

Los modelos se cargan al arrancar el servicio. Si la carga falla, el servicio no arranca y no hay nadie que responda, así que no existe un 503.

El backend debe tratar 422 como un error de validación de su lado. Debe tratar 500, un rechazo de conexión o un timeout como un fallo temporal del servicio de IA. En ese caso la reseña queda en `PENDING`.

## GET /health

Comprobación de que el servicio está activo.

```json
{ "status": "ok" }
```

Respuesta 200 cuando el servicio funciona.

## Notas

- **Estados de reseña.** Este servicio no los gestiona. `PENDING`, `APPROVED` y `REJECTED` son responsabilidad del backend, que decide el estado a partir de `moderation.flagged`.
- **Texto largo.** El modelo de puntuación solo analiza el inicio del texto. Una reseña de hasta 2000 caracteres se acepta, pero la puntuación se calcula con 512 tokens.
- **Versionado.** Si cambia el formato de la respuesta, hay que actualizar este documento y el backend a la vez. Si solo cambia el modelo, se cambia `model_version`.
