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

### Criterio de etiquetado de `flagged`

`flagged` responde a "¿la política de moderación bloquearía esta reseña?", no a "¿esta persona parece sincera?". Se etiqueta con la norma, no con la lectura individual de cada caso.

Se bloquea (`1`):
- Insultos o faltas de respeto a personas, aunque sean suaves.
- Amenazas.
- Contenido sexual explícito dirigido a alguien o gratuito.
- Enlaces.
- Spam: repeticiones exageradas de letras ("buenooooooo", aunque la intención sea sincera), símbolos repetidos o mensajes que son solo risas ("jajajajaja").

No se bloquea (`0`):
- La crítica dura, aunque sea desagradable ("pésimo servicio, una vergüenza").
- Describir hechos desagradables, incluido el acoso sufrido o el contenido sexual de una película, cuando la reseña los cuenta y no los ejerce.
- Risas dentro de una reseña con contenido real ("jajaja esto es lo máximo").

El archivo se guarda en UTF-8 con BOM. Para leerlo con pandas hay que usar `encoding="utf-8-sig"`.

## Cómo ejecutarlo

Desde la raíz del repositorio, con el mismo entorno de conda que usa `ai-service` (Python 3.12):

```bash
conda activate review-ai
pip install -r evaluation/requirements.txt
```

Las dependencias incluyen las de `ai-service`, de modo que el notebook importa `app.rating` y `app.moderation` y evalúa exactamente el código que corre en producción, con las mismas versiones. La primera vez se descargan los modelos desde Hugging Face.

## Métricas

- **Puntuación:** MAE, accuracy exacta, accuracy con margen de ±1 estrella, matriz de confusión y relación entre `confidence` y acierto.
- **Moderación:** precision, recall y F1 de `flagged`, y barrido del umbral entre 0.1 y 0.95 para elegir `TOXIC_THRESHOLD`.

## Resultados

Pendiente: se completarán al ejecutar el notebook.

## Limitaciones

- Con 200 reseñas el margen de error es considerable, sobre todo en las métricas por clase.
- Las reseñas son redactadas para la evaluación, no proceden de usuarios reales, y están en un registro más limpio que el habitual.
- Una sola persona etiquetó los datos, de modo que los casos ambiguos reflejan su criterio.
