"""Carga las reseñas de demo_reviews.csv en el backend, una petición POST por reseña.

Solo usa la librería estándar de Python. Uso, desde la raíz del repo:

    python scripts/seed_reviews.py
    python scripts/seed_reviews.py --url http://localhost:8080 --file scripts/demo_reviews.csv

Las reseñas pasan por la moderación y el modelo reales, así que el estado y las estrellas
salen del ai-service. La columna "esperado" del CSV solo sirve para avisar si la moderación
no coincide con lo previsto; no se envía al backend.
"""
import argparse
import csv
import json
import sys
import urllib.error
import urllib.request
from pathlib import Path

DEFAULT_FILE = Path(__file__).with_name("demo_reviews.csv")


def post_review(base_url, text):
    body = json.dumps({"text": text}).encode("utf-8")
    request = urllib.request.Request(
        f"{base_url}/api/reviews",
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser(description="Carga reseñas de demo en el backend.")
    parser.add_argument("--url", default="http://localhost:8080", help="URL base del backend")
    parser.add_argument("--file", type=Path, default=DEFAULT_FILE, help="CSV con columnas text,esperado")
    args = parser.parse_args()
    base_url = args.url.rstrip("/")

    with open(args.file, encoding="utf-8", newline="") as f:
        rows = list(csv.DictReader(f))

    counts = {}
    mismatches = 0
    failures = 0
    for number, row in enumerate(rows, start=1):
        text, expected = row["text"], row["esperado"]
        try:
            review = post_review(base_url, text)
        except urllib.error.HTTPError as error:
            print(f"[{number:02d}] ERROR {error.code}: {text[:50]}")
            failures += 1
            continue
        except urllib.error.URLError as error:
            sys.exit(f"No se pudo conectar con {base_url}: {error.reason}")

        status = review["status"]
        counts[status] = counts.get(status, 0) + 1
        stars = f"{review['stars']}★" if review["stars"] is not None else "-"
        note = ""
        if status != expected:
            mismatches += 1
            note = f"  <- esperado {expected}"
        print(f"[{number:02d}] {status:<8} {stars:<3} {text[:50]}{note}")

    print()
    print(f"Enviadas: {len(rows) - failures}/{len(rows)}  " + "  ".join(f"{k}: {v}" for k, v in sorted(counts.items())))
    print(f"Estados distintos de lo esperado: {mismatches}")
    if failures:
        sys.exit(1)


if __name__ == "__main__":
    main()
