from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from app.moderation import moderate
from app.rating import rate
import os

MODELNAME = os.getenv("MODELNAME", "nlptown-bert-v1")

app = FastAPI(title="Review AI Service")

class ReviewIn(BaseModel):
    text:str = Field(min_length=10, max_length=2000)

# Cualquier excepción no controlada responde con el JSON del contrato.
# Uvicorn sigue registrando el traceback completo en el log.
@app.exception_handler(Exception)
async def internal_error_handler(request: Request, exc: Exception):
    return JSONResponse(status_code=500, content={"detail": "Error interno del servicio de IA"})

@app.get("/health")
def health():
    return {"status":"ok"}

@app.post("/analyze")
def analyze(review: ReviewIn):
    moderation = moderate(review.text)
    rating = None
    if not moderation["flagged"]:
        rating = rate(review.text)
    return {
        "moderation" : moderation,
        "rating" : rating,
        "model_version": MODELNAME,
    }