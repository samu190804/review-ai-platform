package com.reviewai.backend.ai;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta de POST /analyze, tal como la define docs/api.md.
 * Un record es una clase inmutable: Java genera constructor, getters
 * (moderation(), rating()...), equals y toString.
 */
public record AiAnalysisResponse(
		Moderation moderation,
		/* Null cuando la moderación marca la reseña. */
		Rating rating,
		@JsonProperty("model_version") String modelVersion) {

	public record Moderation(boolean flagged, List<String> reasons, Map<String, Double> scores) {
	}

	public record Rating(int stars, double confidence) {
	}
}
