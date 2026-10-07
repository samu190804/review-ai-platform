FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    HF_HOME=/home/app/.cache/huggingface \
    TORCH_HOME=/home/app/.cache/torch

# Usuario sin privilegios: el servicio no necesita ser root.
RUN useradd --create-home --uid 1000 app

WORKDIR /service

# 1) Dependencias. Se copia solo requirements.txt para que esta capa se reutilice
#    mientras no cambien las dependencias.
#    torch se instala antes desde el índice CPU: la versión por defecto incluye
#    soporte CUDA y pesa varios GB que aquí no se usan.
COPY requirements.txt .
RUN pip install $(grep -i '^torch==' requirements.txt) --index-url https://download.pytorch.org/whl/cpu \
    && pip install -r requirements.txt

# 2) Modelos. Se descargan en la construcción para que el contenedor arranque
#    sin red y sin esperar descargas. Esta capa también se reutiliza mientras
#    no cambien las dependencias.
USER app
RUN python -c "from transformers import pipeline; pipeline('sentiment-analysis', model='nlptown/bert-base-multilingual-uncased-sentiment')" \
    && python -c "from detoxify import Detoxify; Detoxify('multilingual')"

# 3) Código. Es lo que más cambia, por eso va al final.
COPY --chown=app:app app ./app

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
