import pytest

from app.rating import rate


@pytest.mark.parametrize(
    "label, stars",
    [("1 star", 1), ("2 stars", 2), ("3 stars", 3), ("4 stars", 4), ("5 stars", 5)],
)
def test_label_is_converted_to_stars(set_rating, label, stars):
    set_rating(label, 0.9)

    assert rate("Una reseña cualquiera")["stars"] == stars


def test_stars_is_an_integer(set_rating):
    set_rating("3 stars", 0.5)

    assert isinstance(rate("Una reseña cualquiera")["stars"], int)


def test_confidence_is_rounded_to_two_decimals(set_rating):
    set_rating("2 stars", 0.71234)

    assert rate("Una reseña cualquiera")["confidence"] == 0.71


def test_long_texts_are_truncated_to_512_tokens(classifier_calls):
    rate("Una reseña cualquiera")

    _, kwargs = classifier_calls[0]
    assert kwargs == {"truncation": True, "max_length": 512}
