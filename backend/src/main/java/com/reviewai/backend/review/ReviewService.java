package com.reviewai.backend.review;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.reviewai.backend.ai.AiAnalysisResponse;
import com.reviewai.backend.ai.AiRequestRejectedException;
import com.reviewai.backend.ai.AiServiceClient;
import com.reviewai.backend.ai.AiServiceUnavailableException;

@Service
public class ReviewService {

	private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

	private final ReviewRepository repository;
	private final AiServiceClient aiClient;

	public ReviewService(ReviewRepository repository, AiServiceClient aiClient) {
		this.repository = repository;
		this.aiClient = aiClient;
	}

	/**
	 * Crea una reseña y la analiza. A propósito NO es @Transactional: así no se
	 * mantiene una conexión a la base de datos abierta mientras esperamos al
	 * ai-service. Se llama a la IA primero y se guarda una sola vez.
	 *
	 * Si el ai-service no responde (fallo temporal), la reseña se guarda igualmente
	 * como PENDING, sin análisis; un job la reintentará más tarde.
	 * Un AiRequestRejectedException (422) no se captura: significaría un desajuste
	 * entre nuestra validación y el contrato, es decir, un bug nuestro.
	 */
	public Review create(String text) {
		Review review = new Review(text);
		try {
			applyAnalysis(review, aiClient.analyze(text));
		}
		catch (AiServiceUnavailableException e) {
			log.warn("ai-service no disponible; la reseña queda PENDING: {}", e.getMessage());
			review.registerFailedAttempt();
		}
		return repository.save(review);
	}

	/**
	 * Reintenta el análisis de las reseñas PENDING (lo llama el job programado).
	 * Devuelve cuántas se resolvieron.
	 *
	 * - Fallo temporal: se anota el intento y se corta el lote. Si el ai-service
	 *   está caído, llamarlo para las demás solo gastaría sus intentos.
	 * - Rechazo (422): sería un bug nuestro; esa reseña no se arreglará sola, así que
	 *   se anota el intento (acabará agotándose) y se sigue con las demás.
	 * Tampoco es @Transactional, por la misma razón que create().
	 */
	public int retryPending(int batchSize, int maxAttempts) {
		List<Review> batch = repository.findRetryable(ReviewStatus.PENDING, maxAttempts, PageRequest.of(0, batchSize));
		int resolved = 0;

		for (Review review : batch) {
			try {
				applyAnalysis(review, aiClient.analyze(review.getText()));
				repository.save(review);
				resolved++;
			}
			catch (AiServiceUnavailableException e) {
				review.registerFailedAttempt();
				repository.save(review);
				log.warn("Reintento fallido de la reseña {} (intento {}); se corta el lote: {}",
						review.getId(), review.getAnalysisAttempts(), e.getMessage());
				break;
			}
			catch (AiRequestRejectedException e) {
				review.registerFailedAttempt();
				repository.save(review);
				log.error("El ai-service rechazó la reseña {} (intento {}): {}",
						review.getId(), review.getAnalysisAttempts(), e.getMessage());
			}
		}
		return resolved;
	}

	/**
	 * Regla de estados: marcada -> REJECTED (sin estrellas); aceptada -> APPROVED.
	 * Es pública porque el job de reintentos la reutiliza.
	 */
	public void applyAnalysis(Review review, AiAnalysisResponse analysis) {
		boolean flagged = analysis.moderation().flagged();
		if (!flagged && analysis.rating() == null) {
			// El contrato no lo permite (rating solo es null si flagged es true):
			// se trata como fallo temporal para que se reintente. Se comprueba
			// antes de tocar la reseña para no dejarla a medias.
			throw new AiServiceUnavailableException("Respuesta sin rating para una reseña no marcada", null);
		}

		review.setModelVersion(analysis.modelVersion());
		review.setModerationReasons(analysis.moderation().reasons());
		review.setModerationScores(analysis.moderation().scores());

		if (flagged) {
			review.setStatus(ReviewStatus.REJECTED);
			review.setStars(null);
			review.setConfidence(null);
		}
		else {
			review.setStatus(ReviewStatus.APPROVED);
			review.setStars((short) analysis.rating().stars());
			review.setConfidence(analysis.rating().confidence());
		}
	}

	public Review get(Long id) {
		return repository.findById(id).orElseThrow(() -> new ReviewNotFoundException(id));
	}

	public Page<Review> list(ReviewStatus status, Pageable pageable) {
		return status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
	}

	/**
	 * Resumen para el panel. Los conteos se hacen en la base de datos; aquí solo se
	 * rellenan con 0 los estados y puntuaciones que no tienen reseñas.
	 */
	public ReviewStats stats() {
		Map<ReviewStatus, Long> byStatus = new LinkedHashMap<>();
		for (ReviewStatus status : List.of(ReviewStatus.APPROVED, ReviewStatus.REJECTED, ReviewStatus.PENDING)) {
			byStatus.put(status, 0L);
		}
		repository.countGroupedByStatus().forEach(row -> byStatus.put(row.getStatus(), row.getTotal()));

		Map<Integer, Long> starsDistribution = new LinkedHashMap<>();
		for (int stars = 1; stars <= 5; stars++) {
			starsDistribution.put(stars, 0L);
		}
		repository.countGroupedByStars().forEach(row -> starsDistribution.put(row.getStars().intValue(), row.getTotal()));

		long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
		Double average = repository.averageConfidence();
		return new ReviewStats(total, byStatus, starsDistribution,
				average == null ? null : Math.round(average * 100) / 100.0);
	}
}
