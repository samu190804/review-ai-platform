package com.reviewai.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
		verify(repository).save(review);
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
