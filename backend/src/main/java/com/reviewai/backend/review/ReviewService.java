package com.reviewai.backend.review;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.reviewai.backend.ai.AiAnalysisResponse;
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
		}
		return repository.save(review);
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
}
