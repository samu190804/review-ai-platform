import pytest

from app import moderation
from app.moderation import CATEGORY_REASONS, moderate

CLEAN_TEXT = "La comida estaba fría y tardaron una hora."


def test_clean_review_is_not_flagged():
    result = moderate(CLEAN_TEXT)

    assert result["flagged"] is False
    assert result["reasons"] == []


def test_scores_include_the_seven_categories():
    result = moderate(CLEAN_TEXT)

    assert set(result["scores"]) == set(CATEGORY_REASONS)


def test_scores_are_rounded_to_three_decimals(set_toxicity):
    set_toxicity(toxicity=0.123456)

    assert moderate(CLEAN_TEXT)["scores"]["toxicity"] == 0.123


# --- Reglas ---


def test_too_short_text_is_flagged():
    result = moderate("hola")

    assert result["flagged"] is True
    assert "demasiado corta" in result["reasons"]


def test_surrounding_spaces_do_not_count_towards_min_length():
    assert "demasiado corta" in moderate("   hola    ")["reasons"]


@pytest.mark.parametrize(
    "text",
    [
        "Mira esto http://ejemplo.com que está muy bien",
        "Mira esto https://ejemplo.com que está muy bien",
        "Más información en www.ejemplo.com para comprar",
        "Más información en WWW.EJEMPLO.COM para comprar",
    ],
)
def test_links_are_flagged(text):
    assert "contiene enlaces" in moderate(text)["reasons"]


def test_text_without_links_is_not_flagged_for_links():
    assert "contiene enlaces" not in moderate("Hablan de la web pero no ponen ninguna dirección")["reasons"]


def test_six_repeated_characters_are_spam():
    assert "posible spam" in moderate("Buenoooooo servicio, de verdad")["reasons"]  # 6 "o"


def test_five_repeated_characters_are_not_spam():
    assert "posible spam" not in moderate("Buenooooo servicio, de verdad")["reasons"]  # 5 "o"


def test_repeated_symbols_are_spam():
    assert "posible spam" in moderate("Comprad ya !!!!!!! una oferta")["reasons"]


# --- Detoxify y umbral ---


@pytest.mark.parametrize("category, reason", CATEGORY_REASONS.items())
def test_each_category_over_the_threshold_gives_its_reason(set_toxicity, category, reason):
    set_toxicity(**{category: 0.99})

    result = moderate(CLEAN_TEXT)

    assert result["flagged"] is True
    assert result["reasons"] == [reason]


def test_score_equal_to_the_threshold_is_flagged(set_toxicity):
    set_toxicity(toxicity=moderation.TOXIC_THRESHOLD)

    assert moderate(CLEAN_TEXT)["flagged"] is True


def test_score_just_below_the_threshold_is_not_flagged(set_toxicity):
    set_toxicity(toxicity=moderation.TOXIC_THRESHOLD - 0.01)

    assert moderate(CLEAN_TEXT)["flagged"] is False


def test_threshold_is_configurable(set_toxicity, monkeypatch):
    set_toxicity(insult=0.7)
    assert moderate(CLEAN_TEXT)["flagged"] is False

    monkeypatch.setattr(moderation, "TOXIC_THRESHOLD", 0.5)

    assert moderate(CLEAN_TEXT)["flagged"] is True


def test_default_threshold_is_the_one_chosen_in_the_evaluation():
    # El valor sale de evaluation/ y está documentado en docs/api.md.
    assert moderation.TOXIC_THRESHOLD == 0.90


def test_several_reasons_can_appear_together(set_toxicity):
    set_toxicity(toxicity=0.99, insult=0.99)

    result = moderate("Mira www.ejemplo.com eres un imbécil")

    assert set(result["reasons"]) == {"contiene enlaces", "lenguaje tóxico", "insulto"}
