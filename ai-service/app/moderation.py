import os
import re
from detoxify import Detoxify

_model = Detoxify("multilingual")
TOXIC_THRESHOLD = float(os.getenv("TOXIC_THRESHOLD", "0.65"))
MIN_LENGTH = int(os.getenv("MIN_LENGTH", "10"))

# Categorías que devuelve Detoxify y el motivo legible que se informa al marcar.
CATEGORY_REASONS = {
    "toxicity": "lenguaje tóxico",
    "severe_toxicity": "toxicidad severa",
    "obscene": "lenguaje obsceno",
    "identity_attack": "ataque a la identidad",
    "insult": "insulto",
    "threat": "amenaza",
    "sexual_explicit": "contenido sexual explícito",
}

def _check_rules(text: str) -> list[str]:
    reasons = []
    lower = text.lower()
    if len(text.strip()) < MIN_LENGTH:
        reasons.append("demasiado corta")
    if re.search(r"https?://|www\.", lower):
        reasons.append("contiene enlaces")
    if re.search(r"(.)\1{5,}", lower):
        reasons.append("posible spam")
    return reasons

def moderate(text: str) -> dict:
    reasons = _check_rules(text)
    scores = {category: round(float(value), 3) for category, value in _model.predict(text).items()}
    for category, reason in CATEGORY_REASONS.items():
        if scores.get(category, 0.0) >= TOXIC_THRESHOLD:
            reasons.append(reason)
    return {"flagged": bool(reasons), "reasons": reasons, "scores": scores}
