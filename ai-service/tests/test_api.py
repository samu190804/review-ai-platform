import pytest

from app import main

VALID_TEXT = "La comida estaba fría y tardaron una hora."


def test_health(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


# --- Respuesta del contrato (docs/api.md) ---


def test_accepted_review_follows_the_contract(client, set_rating):
    set_rating("2 stars", 0.71)

    response = client.post("/analyze", json={"text": VALID_TEXT})

    assert response.status_code == 200
    body = response.json()
    assert set(body) == {"moderation", "rating", "model_version"}
    assert set(body["moderation"]) == {"flagged", "reasons", "scores"}
    assert body["moderation"]["flagged"] is False
    assert body["moderation"]["reasons"] == []
    assert len(body["moderation"]["scores"]) == 7
    assert body["rating"] == {"stars": 2, "confidence": 0.71}


def test_flagged_review_has_no_rating_and_skips_the_stars_model(client, set_toxicity, classifier_calls):
    set_toxicity(toxicity=0.99, insult=0.99)

    response = client.post("/analyze", json={"text": "Eres un imbécil y un inútil, lárgate"})

    assert response.status_code == 200
    body = response.json()
    assert body["moderation"]["flagged"] is True
    assert body["moderation"]["reasons"] == ["lenguaje tóxico", "insulto"]
    assert body["rating"] is None
    assert classifier_calls == []


def test_review_flagged_by_a_rule_has_no_rating(client, classifier_calls):
    response = client.post("/analyze", json={"text": "Mira esta oferta en www.ejemplo.com ahora"})

    body = response.json()
    assert body["moderation"]["reasons"] == ["contiene enlaces"]
    assert body["rating"] is None
    assert classifier_calls == []


def test_model_version_comes_from_the_environment_setting(client, monkeypatch):
    monkeypatch.setattr(main, "MODELNAME", "otro-modelo-v2")

    response = client.post("/analyze", json={"text": VALID_TEXT})

    assert response.json()["model_version"] == "otro-modelo-v2"


# --- Validación (422) ---


@pytest.mark.parametrize(
    "payload",
    [
        {},
        {"text": None},
        {"text": 12345678901},
        {"text": "corto"},
        {"text": "a" * 9},
        {"text": "a" * 2001},
    ],
)
def test_invalid_body_returns_422(client, payload):
    response = client.post("/analyze", json=payload)

    assert response.status_code == 422


def test_invalid_json_returns_422(client):
    response = client.post("/analyze", content="no es json", headers={"Content-Type": "application/json"})

    assert response.status_code == 422


@pytest.mark.parametrize("length", [10, 2000])
def test_limit_lengths_are_accepted(client, length):
    response = client.post("/analyze", json={"text": "ab" * (length // 2)})

    assert response.status_code == 200


# --- Error interno (500) ---


def test_unexpected_error_returns_the_contract_500(client, monkeypatch):
    def boom(text):
        raise RuntimeError("fallo inesperado")

    monkeypatch.setattr(main, "moderate", boom)

    response = client.post("/analyze", json={"text": VALID_TEXT})

    assert response.status_code == 500
    assert response.json() == {"detail": "Error interno del servicio de IA"}
