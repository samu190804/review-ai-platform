package com.reviewai.backend.review;

import java.util.Map;

/**
 * Resumen de las reseñas para el panel de análisis. Tiene siempre la misma forma:
 * los tres estados y las cinco puntuaciones aparecen aunque valgan 0, para que el
 * cliente no tenga que rellenar huecos.
 *
 * @param total              reseñas guardadas, de cualquier estado
 * @param byStatus           reseñas por estado, en el orden APPROVED, REJECTED, PENDING
 * @param starsDistribution  reseñas por puntuación (1 a 5); solo cuentan las aprobadas
 * @param averageConfidence  confianza media del modelo; null si todavía no hay ninguna aprobada
 */
public record ReviewStats(
		long total,
		Map<ReviewStatus, Long> byStatus,
		Map<Integer, Long> starsDistribution,
		Double averageConfidence) {
}
