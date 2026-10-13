package com.reviewai.backend.review;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Lo que la API devuelve de una reseña. Se separa de la entidad para no exponer la estructura interna. */
public record ReviewResponse(
		Long id,
		String text,
		ReviewStatus status,
		Short stars,
		Double confidence,
		String modelVersion,
		List<String> moderationReasons,
		Map<String, Double> moderationScores,
		Instant createdAt) {

	public static ReviewResponse from(Review review) {
		return new ReviewResponse(
				review.getId(),
				review.getText(),
				review.getStatus(),
				review.getStars(),
				review.getConfidence(),
				review.getModelVersion(),
				review.getModerationReasons(),
				review.getModerationScores(),
				review.getCreatedAt());
	}
}
