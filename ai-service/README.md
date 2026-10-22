# ai-service

Microservicio de IA (FastAPI). Escucha en http://localhost:8000.

## Encender con Docker

Desde la raíz del repo:

```powershell
docker compose up --build
```

## Encender en local (sin Docker)

Desde `ai-service/`:

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Comprobar

```powershell
curl http://localhost:8000/health
```

## Tests

Desde `ai-service/`:

```powershell
pip install -r requirements-dev.txt
python -m pytest
```

Los tests sustituyen los dos modelos (`transformers` y `detoxify`) por falsos controlables antes de importar la aplicación. Por eso son rápidos (un par de segundos) y no necesitan descargar ni cargar los modelos. Cubren:

- **Reglas de moderación:** longitud mínima, enlaces y spam, incluido el límite exacto de caracteres repetidos.
- **Umbral de toxicidad:** que un score igual al umbral marque la reseña, que uno justo por debajo no, que sea configurable y que el valor por defecto sea el elegido en la evaluación.
- **Puntuación:** conversión de la etiqueta del modelo a estrellas, redondeo de la confianza y truncado a 512 tokens.
- **API:** el contrato de `docs/api.md`, que una reseña marcada no ejecute el modelo de estrellas, la validación (422) y el error interno (500).

No miden la calidad de los modelos, que está en [evaluation/](../evaluation/README.md).
