from fastapi import FastAPI
from pydantic import BaseModel, Field
from app.moderation import moderate
from app.rating import rate
import os

MODELNAME = os.getenv("MODELNAME", "nlptown-bert-v1")

app = FastAPI(title="Review AI Service")

class ReviewIn(BaseModel):
    text:str = Field(min_length=10, max_length=2000)
    
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