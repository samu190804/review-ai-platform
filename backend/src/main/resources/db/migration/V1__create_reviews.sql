-- Reseñas y el resultado de su análisis por el ai-service (ver docs/api.md).
-- Las columnas de análisis admiten NULL: una reseña puede quedar PENDING
-- sin análisis si el ai-service no respondió.
CREATE TABLE reviews (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    text               TEXT             NOT NULL,
    status             VARCHAR(20)      NOT NULL DEFAULT 'PENDING',
    stars              SMALLINT,
    confidence         DOUBLE PRECISION,
    model_version      VARCHAR(100),
    moderation_reasons JSONB,
    moderation_scores  JSONB,
    created_at         TIMESTAMPTZ      NOT NULL DEFAULT now(),

    CONSTRAINT ck_reviews_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_reviews_stars CHECK (stars BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_confidence CHECK (confidence BETWEEN 0 AND 1)
);

-- Para listar por estado (y para el job que reintenta las PENDING).
CREATE INDEX idx_reviews_status_created_at ON reviews (status, created_at);
