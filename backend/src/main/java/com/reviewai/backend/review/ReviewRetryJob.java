package com.reviewai.backend.review;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarea programada que reanaliza las reseñas que quedaron PENDING porque el
 * ai-service no respondió. Es solo el "despertador": la lógica está en ReviewService.
 *
 * fixedDelay (y no fixedRate) cuenta el tiempo desde que TERMINA una ejecución,
 * así dos ejecuciones nunca se solapan aunque el ai-service tarde.
 * Funciona bien con una sola instancia del backend; con varias habría que evitar
 * que reintenten la misma reseña a la vez (p. ej. con un bloqueo en base de datos).
 */
@Component
@EnableScheduling
@EnableConfigurationProperties(ReviewRetryProperties.class)
public class ReviewRetryJob {

	private static final Logger log = LoggerFactory.getLogger(ReviewRetryJob.class);

	private final ReviewService service;
	private final ReviewRetryProperties properties;

	public ReviewRetryJob(ReviewService service, ReviewRetryProperties properties) {
		this.service = service;
		this.properties = properties;
	}

	@Scheduled(initialDelayString = "${reviews.retry.initial-delay}", fixedDelayString = "${reviews.retry.interval}")
	public void retryPendingReviews() {
		int resolved = service.retryPending(properties.batchSize(), properties.maxAttempts());
		if (resolved > 0) {
			log.info("Reintento de reseñas PENDING: {} resuelta(s)", resolved);
		}
	}
}
