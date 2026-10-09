package com.reviewai.backend.review;

import static org.assertj.core.api.Assertions.assertThat;

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
