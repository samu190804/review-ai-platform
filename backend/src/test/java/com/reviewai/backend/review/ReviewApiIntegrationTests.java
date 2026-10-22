package com.reviewai.backend.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.reviewai.backend.FakeAiService;
import com.reviewai.backend.TestcontainersConfiguration;

// Prueba de integración: aplicación completa (controlador, servicio, JPA, Flyway) con un
// PostgreSQL real en Docker y un ai-service falso. El job programado se retrasa una
// hora para que no interfiera; los reintentos se prueban llamando al servicio a mano.
@SpringBootTest(properties = "reviews.retry.initial-delay=1h")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReviewApiIntegrationTests {

	private static final FakeAiService FAKE_AI = new FakeAiService();

	@DynamicPropertySource
	static void aiServiceUrl(DynamicPropertyRegistry registry) {
		registry.add("ai-service.url", FAKE_AI::url);
	}

	@AfterAll
	static void stopFakeService() {
		FAKE_AI.close();
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ReviewRepository repository;

	@Autowired
	private ReviewService service;

	@BeforeEach
	void cleanUp() {
		repository.deleteAll();
		FAKE_AI.reset();
	}

	private ResultActions postReview(String json) throws Exception {
		return mockMvc.perform(post("/api/reviews")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.getBytes(StandardCharsets.UTF_8)));
	}

	private ResultActions postText(String text) throws Exception {
		return postReview("{\"text\":\"" + text + "\"}");
	}

	// ---- POST /api/reviews ----

	@Test
	void acceptedReviewIsApprovedAndPersisted() throws Exception {
		postText("La comida estaba fría y tardaron una hora.")
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/reviews/\\d+")))
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.stars").value(2))
				.andExpect(jsonPath("$.confidence").value(0.71))
				.andExpect(jsonPath("$.modelVersion").value("nlptown-bert-v1"))
				.andExpect(jsonPath("$.moderationReasons").isEmpty())
				.andExpect(jsonPath("$.moderationScores.toxicity").value(0.001))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());

		assertThat(repository.findAll()).singleElement().satisfies(review -> {
			assertThat(review.getStatus()).isEqualTo(ReviewStatus.APPROVED);
			assertThat(review.getAnalysisAttempts()).isZero();
		});
		assertThat(FAKE_AI.requests()).hasSize(1);
	}

	@Test
	void flaggedReviewIsRejectedButStillSaved() throws Exception {
		FAKE_AI.respondWith(200, FakeAiService.FLAGGED);

		postText("Texto ofensivo de ejemplo para la prueba.")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REJECTED"))
				.andExpect(jsonPath("$.stars").isEmpty())
				.andExpect(jsonPath("$.confidence").isEmpty())
				.andExpect(jsonPath("$.moderationReasons[0]").value("lenguaje tóxico"));

		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void unavailableAiServiceLeavesReviewPendingWith201() throws Exception {
		FAKE_AI.respondWith(500, FakeAiService.INTERNAL_ERROR);

		postText("La comida estaba fría y tardaron una hora.")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.stars").isEmpty())
				.andExpect(jsonPath("$.modelVersion").isEmpty());

		assertThat(repository.findAll()).singleElement()
				.satisfies(review -> assertThat(review.getAnalysisAttempts()).isEqualTo(1));
	}

	@Test
	void aiServiceRejectingTheRequestIsABadGateway() throws Exception {
		FAKE_AI.respondWith(422, "{\"detail\":[]}");

		postText("Texto suficientemente largo para validar.")
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.status").value(502));

		assertThat(repository.count()).isZero();
	}

	@Test
	void tooShortTextIsRejectedWithoutCallingTheAiService() throws Exception {
		postText("corta")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.text").value("El texto debe tener entre 10 y 2000 caracteres"));

		assertThat(FAKE_AI.requests()).isEmpty();
		assertThat(repository.count()).isZero();
	}

	@Test
	void tooLongTextIsRejected() throws Exception {
		postText("a".repeat(2001))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.text").isNotEmpty());
	}

	@Test
	void missingOrBlankTextIsRejected() throws Exception {
		postReview("{}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.text").value("El texto es obligatorio"));
		postReview("{\"text\":\"          \"}").andExpect(status().isBadRequest());
	}

	@Test
	void malformedJsonIsABadRequest() throws Exception {
		postReview("{esto no es json").andExpect(status().isBadRequest());
	}

	// ---- GET ----

	@Test
	void getByIdReturnsTheReviewAndUnknownIdIs404() throws Exception {
		long id = repository.save(new Review("Reseña guardada directamente en la base de datos.")).getId();

		mockMvc.perform(get("/api/reviews/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.status").value("PENDING"));

		mockMvc.perform(get("/api/reviews/{id}", id + 1000))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No existe la reseña con id " + (id + 1000)));
	}

	@Test
	void listIsPaginatedNewestFirstAndFilterableByStatus() throws Exception {
		postText("Primera reseña, aceptada por el modelo.").andExpect(status().isCreated());
		FAKE_AI.respondWith(200, FakeAiService.FLAGGED);
		postText("Segunda reseña, rechazada por moderación.").andExpect(status().isCreated());
		FAKE_AI.respondWith(500, FakeAiService.INTERNAL_ERROR);
		postText("Tercera reseña, queda pendiente de análisis.").andExpect(status().isCreated());

		mockMvc.perform(get("/api/reviews"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].status").value("PENDING"))
				.andExpect(jsonPath("$.content[2].status").value("APPROVED"));

		mockMvc.perform(get("/api/reviews").param("size", "2").param("page", "1"))
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalPages").value(2));

		mockMvc.perform(get("/api/reviews").param("status", "REJECTED"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].status").value("REJECTED"));
	}

	@Test
	void unknownStatusFilterIsABadRequest() throws Exception {
		mockMvc.perform(get("/api/reviews").param("status", "XXX"))
				.andExpect(status().isBadRequest());
	}

	// ---- Estadísticas ----

	@Test
	void statsOfAnEmptyDatabaseHaveTheFullShapeWithZeros() throws Exception {
		mockMvc.perform(get("/api/reviews/stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(0))
				.andExpect(jsonPath("$.byStatus.APPROVED").value(0))
				.andExpect(jsonPath("$.byStatus.REJECTED").value(0))
				.andExpect(jsonPath("$.byStatus.PENDING").value(0))
				.andExpect(jsonPath("$.starsDistribution['1']").value(0))
				.andExpect(jsonPath("$.starsDistribution['5']").value(0))
				.andExpect(jsonPath("$.averageConfidence").isEmpty());
	}

	@Test
	void statsSummariseReviewsCreatedThroughTheApi() throws Exception {
		postText("Primera reseña, aceptada por el modelo.").andExpect(status().isCreated());
		postText("Segunda reseña, también aceptada.").andExpect(status().isCreated());
		FAKE_AI.respondWith(200, FakeAiService.FLAGGED);
		postText("Tercera reseña, rechazada por moderación.").andExpect(status().isCreated());
		FAKE_AI.respondWith(500, FakeAiService.INTERNAL_ERROR);
		postText("Cuarta reseña, queda pendiente de análisis.").andExpect(status().isCreated());

		mockMvc.perform(get("/api/reviews/stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(4))
				.andExpect(jsonPath("$.byStatus.APPROVED").value(2))
				.andExpect(jsonPath("$.byStatus.REJECTED").value(1))
				.andExpect(jsonPath("$.byStatus.PENDING").value(1))
				.andExpect(jsonPath("$.starsDistribution['2']").value(2))
				.andExpect(jsonPath("$.starsDistribution['5']").value(0))
				.andExpect(jsonPath("$.averageConfidence").value(0.71));
	}

	// ---- Reintentos ----

	@Test
	void pendingReviewIsResolvedOnceTheAiServiceRecovers() throws Exception {
		FAKE_AI.respondWith(500, FakeAiService.INTERNAL_ERROR);
		postText("La comida estaba fría y tardaron una hora.").andExpect(jsonPath("$.status").value("PENDING"));

		// El ai-service se recupera y el job (aquí, llamado a mano) reanaliza.
		FAKE_AI.respondWith(200, FakeAiService.ACCEPTED);
		int resolved = service.retryPending(20, 5);

		assertThat(resolved).isEqualTo(1);
		mockMvc.perform(get("/api/reviews").param("status", "APPROVED"))
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].stars").value(2));
	}

	@Test
	void reviewIsGivenUpAfterMaxAttempts() throws Exception {
		FAKE_AI.respondWith(500, FakeAiService.INTERNAL_ERROR);
		postText("La comida estaba fría y tardaron una hora."); // intento 1

		service.retryPending(20, 3); // intento 2
		service.retryPending(20, 3); // intento 3: se agota
		int requestsBefore = FAKE_AI.requests().size();
		service.retryPending(20, 3); // ya no se intenta

		assertThat(FAKE_AI.requests()).hasSize(requestsBefore);
		assertThat(repository.findAll()).singleElement().satisfies(review -> {
			assertThat(review.getStatus()).isEqualTo(ReviewStatus.PENDING);
			assertThat(review.getAnalysisAttempts()).isEqualTo(3);
		});
	}
}
