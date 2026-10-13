package com.reviewai.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
