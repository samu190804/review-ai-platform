# Documentación API
### Endpoint analyze
```json
    // POST /analyze
    { "text": "La comida estaba fría y tardaron una hora." }

    // respuesta
    {
    "moderation": { "flagged": false, "reason": null },
    "rating": { "stars": 2, "confidence": 0.71 },
    "model_version": "sentiment-v1"
    }
```