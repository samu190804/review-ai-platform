package com.reviewai.backend.review;

/**
 * Estados de una reseña. Los decide el backend a partir de la respuesta del ai-service:
 * flagged=true -> REJECTED, flagged=false -> APPROVED, sin respuesta -> PENDING.
 */
public enum ReviewStatus {
	PENDING,
	APPROVED,
	REJECTED
}
