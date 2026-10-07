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
