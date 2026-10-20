# Evaluación de los modelos

Aquí se mide qué tan bien funcionan las dos piezas del `ai-service` con un conjunto de reseñas propio, etiquetado a mano:

- **Puntuación** (`app/rating.py`): modelo `nlptown/bert-base-multilingual-uncased-sentiment`, de 1 a 5 estrellas.
- **Moderación** (`app/moderation.py`): reglas más Detoxify multilingüe con el umbral `TOXIC_THRESHOLD`.

El objetivo es doble: conocer las limitaciones reales de cada modelo y elegir el umbral de toxicidad con datos, en lugar de dejar un valor puesto a ojo.

## Estructura

```
evaluation/
├── data/
│   └── reviews.csv       # 200 reseñas etiquetadas a mano
├── notebooks/            # análisis y gráficos
├── requirements.txt
└── README.md
```

## Dataset

`data/reviews.csv` tiene 200 reseñas en español y estas columnas:

| Columna | Descripción |
|---|---|
| `id` | Identificador de la fila |
| `text` | Texto de la reseña |
| `stars` | Puntuación de 1 a 5 asignada a mano. Vacía si la reseña no se puede puntuar con sentido |
| `flagged` | `1` si un moderador la bloquearía, `0` si no |

Las reseñas se redactaron para cubrir casos distintos: opiniones normales de las cinco puntuaciones, críticas muy duras pero legítimas (para medir falsos positivos) e insultos, amenazas, contenido sexual, enlaces y spam. Las etiquetas las puso una sola persona, sin ejecutar antes los modelos, para no sesgarlas.

El archivo se guarda en UTF-8 con BOM. Para leerlo con pandas hay que usar `encoding="utf-8-sig"`.

## Cómo ejecutarlo

Desde la raíz del repositorio, con un entorno virtual propio:

```bash
python -m venv .venv
.venv\Scripts\activate          # Windows
pip install -r evaluation/requirements.txt
```

Las dependencias incluyen las de `ai-service`, de modo que el notebook importa `app.rating` y `app.moderation` y evalúa exactamente el código que corre en producción. La primera vez se descargan los modelos desde Hugging Face.

## Métricas

- **Puntuación:** MAE, accuracy exacta, accuracy con margen de ±1 estrella, matriz de confusión y relación entre `confidence` y acierto.
- **Moderación:** precision, recall y F1 de `flagged`, y barrido del umbral entre 0.1 y 0.95 para elegir `TOXIC_THRESHOLD`.

## Resultados

Pendiente: se completarán al ejecutar el notebook.

## Limitaciones

- Con 200 reseñas el margen de error es considerable, sobre todo en las métricas por clase.
- Las reseñas son redactadas para la evaluación, no proceden de usuarios reales, y están en un registro más limpio que el habitual.
- Una sola persona etiquetó los datos, de modo que los casos ambiguos reflejan su criterio.
