"""Fixtures compartidas de los tests del ai-service.

`app.moderation` y `app.rating` cargan sus modelos al importarse (más de 1 GB, y
necesitan torch). Aquí se sustituyen `transformers` y `detoxify` por falsos
controlables ANTES de que ningún test importe `app`, así que los tests no
necesitan torch ni descargar nada y corren en segundos.

Esto prueba la lógica del servicio (reglas, umbral, contrato, validación), no la
calidad de los modelos: esa se mide en `evaluation/`.
"""
import sys
import types

import pytest

CATEGORIES = [
    "toxicity",
    "severe_toxicity",
    "obscene",
    "identity_attack",
    "insult",
    "threat",
    "sexual_explicit",
]


class FakeDetoxify:
    """Devuelve los scores que cada test configure; 0.0 en las categorías no indicadas."""

    scores: dict = {}

    def __init__(self, *args, **kwargs):
        pass

    def predict(self, text):
        return {category: self.scores.get(category, 0.0) for category in CATEGORIES}


class FakeClassifier:
    """Imita el pipeline de estrellas: devuelve la etiqueta configurada y anota las llamadas."""

    label = "4 stars"
    score = 0.8
    calls: list = []

    def __call__(self, text, **kwargs):
        FakeClassifier.calls.append((text, kwargs))
        return [{"label": FakeClassifier.label, "score": FakeClassifier.score}]


def _install_fake_modules():
    fake_transformers = types.ModuleType("transformers")
    fake_transformers.pipeline = lambda *args, **kwargs: FakeClassifier()
    fake_detoxify = types.ModuleType("detoxify")
    fake_detoxify.Detoxify = FakeDetoxify
    sys.modules["transformers"] = fake_transformers
    sys.modules["detoxify"] = fake_detoxify


_install_fake_modules()


@pytest.fixture(autouse=True)
def reset_fakes():
    FakeDetoxify.scores = {}
    FakeClassifier.label = "4 stars"
    FakeClassifier.score = 0.8
    FakeClassifier.calls = []


@pytest.fixture
def set_toxicity():
    """Fija los scores de Detoxify: set_toxicity(insult=0.95, threat=0.1)."""

    def _set(**scores):
        FakeDetoxify.scores = scores

    return _set


@pytest.fixture
def set_rating():
    """Fija lo que devuelve el modelo de estrellas: set_rating("2 stars", 0.71)."""

    def _set(label, score):
        FakeClassifier.label = label
        FakeClassifier.score = score

    return _set


@pytest.fixture
def classifier_calls():
    return FakeClassifier.calls


@pytest.fixture
def client():
    from fastapi.testclient import TestClient

    from app.main import app

    # raise_server_exceptions=False: el 500 llega como respuesta, igual que en producción.
    return TestClient(app, raise_server_exceptions=False)
