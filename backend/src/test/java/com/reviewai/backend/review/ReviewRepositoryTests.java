package com.reviewai.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import com.reviewai.backend.TestcontainersConfiguration;

// Levanta un PostgreSQL real en Docker, aplica las migraciones de Flyway y
// comprueba que las entidades encajan con las tablas.
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ReviewRepositoryTests {

	@Autowired
	private ReviewRepository repository;

	@Test
	void newReviewIsPendingWithGeneratedIdAndTimestamp() {
		Review saved = repository.saveAndFlush(new Review("La comida estaba fría y tardaron una hora."));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getStatus()).isEqualTo(ReviewStatus.PENDING);
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getStars()).isNull();
	}

	@Test
	void analysisResultIsPersistedIncludingJsonColumns() {
		Review review = new Review("Servicio excelente, volveré seguro.");
		review.setStatus(ReviewStatus.APPROVED);
		review.setStars((short) 5);
		review.setConfidence(0.88);
		review.setModelVersion("nlptown-bert-v1");
		review.setModerationReasons(List.of());
		review.setModerationScores(Map.of("toxicity", 0.001, "insult", 0.0));
		Long id = repository.saveAndFlush(review).getId();

		repository.flush();
		Review found = repository.findById(id).orElseThrow();

		assertThat(found.getStars()).isEqualTo((short) 5);
		assertThat(found.getModelVersion()).isEqualTo("nlptown-bert-v1");
		assertThat(found.getModerationReasons()).isEmpty();
		assertThat(found.getModerationScores()).containsEntry("toxicity", 0.001);
	}

	@Test
	void findRetryableSkipsExhaustedReviewsAndOrdersByFewestAttempts() {
		Review exhausted = new Review("Reseña que ya agotó sus intentos de análisis.");
		Review oneFailure = new Review("Reseña con un intento fallido de análisis.");
		Review fresh = new Review("Reseña pendiente sin ningún intento fallido.");
		Review approved = new Review("Reseña que ya está aprobada del todo.");
		approved.setStatus(ReviewStatus.APPROVED);
		for (int i = 0; i < 3; i++) {
			exhausted.registerFailedAttempt();
		}
		oneFailure.registerFailedAttempt();
		repository.saveAll(List.of(exhausted, oneFailure, fresh, approved));
		repository.flush();

		var retryable = repository.findRetryable(ReviewStatus.PENDING, 3, PageRequest.of(0, 10));

		assertThat(retryable).extracting(Review::getText)
				.containsExactly(fresh.getText(), oneFailure.getText());
	}

	private Review saved(String text, ReviewStatus status, Integer stars, Double confidence) {
		Review review = new Review(text);
		review.setStatus(status);
		review.setStars(stars == null ? null : stars.shortValue());
		review.setConfidence(confidence);
		return repository.save(review);
	}

	@Test
	void countsReviewsGroupedByStatus() {
		saved("Reseña aprobada número uno.", ReviewStatus.APPROVED, 5, 0.9);
		saved("Reseña aprobada número dos.", ReviewStatus.APPROVED, 4, 0.8);
		saved("Reseña rechazada de ejemplo.", ReviewStatus.REJECTED, null, null);
		repository.flush();

		var counts = repository.countGroupedByStatus();

		assertThat(counts).extracting(ReviewRepository.StatusCount::getStatus, ReviewRepository.StatusCount::getTotal)
				.containsExactlyInAnyOrder(
						tuple(ReviewStatus.APPROVED, 2L),
						tuple(ReviewStatus.REJECTED, 1L));
	}

	@Test
	void countsStarsIgnoringReviewsWithoutThem() {
		saved("Reseña de cinco estrellas A.", ReviewStatus.APPROVED, 5, 0.9);
		saved("Reseña de cinco estrellas B.", ReviewStatus.APPROVED, 5, 0.8);
		saved("Reseña de una estrella.", ReviewStatus.APPROVED, 1, 0.7);
		saved("Reseña rechazada sin estrellas.", ReviewStatus.REJECTED, null, null);
		repository.flush();

		var counts = repository.countGroupedByStars();

		assertThat(counts).extracting(ReviewRepository.StarsCount::getStars, ReviewRepository.StarsCount::getTotal)
				.containsExactlyInAnyOrder(
						tuple((short) 5, 2L),
						tuple((short) 1, 1L));
	}

	@Test
	void averageConfidenceIgnoresReviewsWithoutConfidence() {
		saved("Reseña aprobada con confianza alta.", ReviewStatus.APPROVED, 5, 0.9);
		saved("Reseña aprobada con confianza media.", ReviewStatus.APPROVED, 3, 0.5);
		saved("Reseña rechazada sin confianza.", ReviewStatus.REJECTED, null, null);
		repository.flush();

		assertThat(repository.averageConfidence()).isEqualTo(0.7, offset(0.0001));
	}

	@Test
	void averageConfidenceIsNullWhenThereIsNoData() {
		assertThat(repository.averageConfidence()).isNull();
	}

	@Test
	void findsReviewsByStatus() {
		Review rejected = new Review("Esto es un insulto de ejemplo largo.");
		rejected.setStatus(ReviewStatus.REJECTED);
		rejected.setModerationReasons(List.of("insulto"));
		repository.save(rejected);
		repository.save(new Review("Otra reseña que queda pendiente."));
		repository.flush();

		var rejectedPage = repository.findByStatus(ReviewStatus.REJECTED, PageRequest.of(0, 10));

		assertThat(rejectedPage.getTotalElements()).isEqualTo(1);
		assertThat(rejectedPage.getContent().get(0).getModerationReasons()).containsExactly("insulto");
	}
}
