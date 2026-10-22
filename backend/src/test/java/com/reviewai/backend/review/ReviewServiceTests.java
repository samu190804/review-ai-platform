package com.reviewai.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import com.reviewai.backend.ai.AiAnalysisResponse;
import com.reviewai.backend.ai.AiAnalysisResponse.Moderation;
import com.reviewai.backend.ai.AiAnalysisResponse.Rating;
import com.reviewai.backend.ai.AiRequestRejectedException;
import com.reviewai.backend.ai.AiServiceClient;
import com.reviewai.backend.ai.AiServiceUnavailableException;

// Test unitario: repositorio y cliente son falsos (Mockito), solo se prueba la regla de estados.
class ReviewServiceTests {

	private static final String TEXT = "La comida estaba fría y tardaron una hora.";

	private ReviewRepository repository;
	private AiServiceClient aiClient;
	private ReviewService service;

	@BeforeEach
	void setUp() {
		repository = mock(ReviewRepository.class);
		aiClient = mock(AiServiceClient.class);
		service = new ReviewService(repository, aiClient);
		// save() devuelve lo mismo que recibe.
		when(repository.save(any(Review.class))).thenAnswer(call -> call.getArgument(0));
	}

	private static ReviewRepository.StatusCount statusCount(ReviewStatus status, long total) {
		ReviewRepository.StatusCount row = mock(ReviewRepository.StatusCount.class);
		when(row.getStatus()).thenReturn(status);
		when(row.getTotal()).thenReturn(total);
		return row;
	}

	private static ReviewRepository.StarsCount starsCount(int stars, long total) {
		ReviewRepository.StarsCount row = mock(ReviewRepository.StarsCount.class);
		when(row.getStars()).thenReturn((short) stars);
		when(row.getTotal()).thenReturn(total);
		return row;
	}

	@Test
	void statsAddsUpTheTotalAndRoundsTheAverage() {
		// Los mocks de las filas se crean antes: no se pueden anidar dentro de un when(...).
		var statusRows = List.of(statusCount(ReviewStatus.APPROVED, 7), statusCount(ReviewStatus.REJECTED, 2),
				statusCount(ReviewStatus.PENDING, 1));
		var starsRows = List.of(starsCount(5, 4), starsCount(2, 3));
		when(repository.countGroupedByStatus()).thenReturn(statusRows);
		when(repository.countGroupedByStars()).thenReturn(starsRows);
		when(repository.averageConfidence()).thenReturn(0.71428571);

		ReviewStats stats = service.stats();

		assertThat(stats.total()).isEqualTo(10);
		assertThat(stats.byStatus()).containsEntry(ReviewStatus.APPROVED, 7L)
				.containsEntry(ReviewStatus.REJECTED, 2L).containsEntry(ReviewStatus.PENDING, 1L);
		assertThat(stats.starsDistribution()).containsEntry(5, 4L).containsEntry(2, 3L);
		assertThat(stats.averageConfidence()).isEqualTo(0.71);
	}

	@Test
	void statsFillsMissingStatesAndStarsWithZeroInAFixedOrder() {
		var statusRows = List.of(statusCount(ReviewStatus.REJECTED, 3));
		var starsRows = List.of(starsCount(3, 1));
		when(repository.countGroupedByStatus()).thenReturn(statusRows);
		when(repository.countGroupedByStars()).thenReturn(starsRows);
		when(repository.averageConfidence()).thenReturn(null);

		ReviewStats stats = service.stats();

		assertThat(stats.byStatus()).containsExactly(
				entry(ReviewStatus.APPROVED, 0L),
				entry(ReviewStatus.REJECTED, 3L),
				entry(ReviewStatus.PENDING, 0L));
		assertThat(stats.starsDistribution()).containsExactly(
				entry(1, 0L), entry(2, 0L),
				entry(3, 1L), entry(4, 0L),
				entry(5, 0L));
		assertThat(stats.averageConfidence()).isNull();
	}

	@Test
	void acceptedReviewIsApprovedWithStars() {
		when(aiClient.analyze(TEXT)).thenReturn(new AiAnalysisResponse(
				new Moderation(false, List.of(), Map.of("toxicity", 0.001)),
				new Rating(2, 0.71),
				"nlptown-bert-v1"));

		Review review = service.create(TEXT);

		assertThat(review.getStatus()).isEqualTo(ReviewStatus.APPROVED);
		assertThat(review.getStars()).isEqualTo((short) 2);
		assertThat(review.getConfidence()).isEqualTo(0.71);
		assertThat(review.getModelVersion()).isEqualTo("nlptown-bert-v1");
		verify(repository).save(review);
	}

	@Test
	void flaggedReviewIsRejectedWithoutStars() {
		when(aiClient.analyze(TEXT)).thenReturn(new AiAnalysisResponse(
				new Moderation(true, List.of("insulto"), Map.of("insult", 0.91)),
				null,
				"nlptown-bert-v1"));

		Review review = service.create(TEXT);

		assertThat(review.getStatus()).isEqualTo(ReviewStatus.REJECTED);
		assertThat(review.getStars()).isNull();
		assertThat(review.getModerationReasons()).containsExactly("insulto");
	}

	@Test
	void unavailableAiServiceLeavesReviewPendingButSaved() {
		when(aiClient.analyze(TEXT)).thenThrow(new AiServiceUnavailableException("caído", null));

		Review review = service.create(TEXT);

		assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING);
		assertThat(review.getStars()).isNull();
		assertThat(review.getModerationReasons()).isNull();
		assertThat(review.getAnalysisAttempts()).isEqualTo(1);
		verify(repository).save(review);
	}

	@Test
	void retryResolvesPendingReviews() {
		Review pending = new Review(TEXT);
		when(repository.findRetryable(eq(ReviewStatus.PENDING), eq(5), any(Pageable.class)))
				.thenReturn(List.of(pending));
		when(aiClient.analyze(TEXT)).thenReturn(new AiAnalysisResponse(
				new Moderation(false, List.of(), Map.of()), new Rating(4, 0.8), "nlptown-bert-v1"));

		int resolved = service.retryPending(20, 5);

		assertThat(resolved).isEqualTo(1);
		assertThat(pending.getStatus()).isEqualTo(ReviewStatus.APPROVED);
		assertThat(pending.getStars()).isEqualTo((short) 4);
		verify(repository).save(pending);
	}

	@Test
	void retryStopsTheBatchAtFirstTemporaryFailure() {
		Review first = new Review("Primera reseña pendiente de análisis.");
		Review second = new Review("Segunda reseña pendiente de análisis.");
		when(repository.findRetryable(eq(ReviewStatus.PENDING), eq(5), any(Pageable.class)))
				.thenReturn(List.of(first, second));
		when(aiClient.analyze(any())).thenThrow(new AiServiceUnavailableException("caído", null));

		int resolved = service.retryPending(20, 5);

		assertThat(resolved).isZero();
		assertThat(first.getAnalysisAttempts()).isEqualTo(1);
		assertThat(second.getAnalysisAttempts()).isZero();
		verify(aiClient, times(1)).analyze(any());
	}

	@Test
	void retryCountsRejectionAndContinuesWithTheRest() {
		Review rejected = new Review("Reseña que el ai-service rechaza siempre.");
		Review fine = new Review("Reseña correcta que sí se puede analizar.");
		when(repository.findRetryable(eq(ReviewStatus.PENDING), eq(5), any(Pageable.class)))
				.thenReturn(List.of(rejected, fine));
		when(aiClient.analyze(rejected.getText())).thenThrow(new AiRequestRejectedException("422"));
		when(aiClient.analyze(fine.getText())).thenReturn(new AiAnalysisResponse(
				new Moderation(false, List.of(), Map.of()), new Rating(5, 0.9), "nlptown-bert-v1"));

		int resolved = service.retryPending(20, 5);

		assertThat(resolved).isEqualTo(1);
		assertThat(rejected.getAnalysisAttempts()).isEqualTo(1);
		assertThat(rejected.getStatus()).isEqualTo(ReviewStatus.PENDING);
		assertThat(fine.getStatus()).isEqualTo(ReviewStatus.APPROVED);
	}

	@Test
	void unflaggedResponseWithoutRatingIsTreatedAsTemporaryFailure() {
		when(aiClient.analyze(TEXT)).thenReturn(new AiAnalysisResponse(
				new Moderation(false, List.of(), Map.of()), null, "nlptown-bert-v1"));

		Review review = service.create(TEXT);

		assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING);
		assertThat(review.getModelVersion()).isNull();
	}

	@Test
	void rejectedRequestFromAiServiceIsNotSwallowed() {
		when(aiClient.analyze(TEXT)).thenThrow(new AiRequestRejectedException("422"));

		assertThatThrownBy(() -> service.create(TEXT)).isInstanceOf(AiRequestRejectedException.class);
	}

	@Test
	void getThrowsWhenReviewDoesNotExist() {
		when(repository.findById(99L)).thenReturn(java.util.Optional.empty());

		assertThatThrownBy(() -> service.get(99L)).isInstanceOf(ReviewNotFoundException.class);
	}
}
