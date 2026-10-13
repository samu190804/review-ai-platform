-- Intentos fallidos de análisis. El job de reintentos deja de intentarlo al
-- llegar al máximo configurado (reviews.retry.max-attempts), para que una
-- reseña que siempre falla no se reintente eternamente.
ALTER TABLE reviews
    ADD COLUMN analysis_attempts INTEGER NOT NULL DEFAULT 0;
