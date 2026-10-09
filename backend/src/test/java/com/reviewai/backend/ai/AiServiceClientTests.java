package com.reviewai.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Test unitario puro: no arranca Spring. MockRestServiceServer sustituye al
// ai-service con respuestas fabricadas siguiendo docs/api.md.
class AiServiceClientTests {

	private static final String ACCEPTED = """
			{
			  "moderation": {
			    "flagged": false,
			    "reasons": [],
			    "scores": { "toxicity": 0.001, "severe_toxicity": 0.0, "obscene": 0.0,
			                "identity_attack": 0.0, "insult": 0.001, "threat": 0.0, "sexual_explicit": 0.0 }
			  },
			  "rating": { "stars": 2, "confidence": 0.71 },
			  "model_version": "nlptown-bert-v1"
			}
			""";

	private static final String FLAGGED = """
			{
			  "moderation": {
			    "flagged": true,
			    "reasons": ["lenguaje tóxico", "insulto"],
			    "scores": { "toxicity": 0.96, "insult": 0.91 }
			  },
			  "rating": null,
			  "model_version": "nlptown-bert-v1"
			}
			""";

	private MockRestServiceServer server;
	private AiServiceClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-service.test");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new AiServiceClient(builder.build());
	}

	@Test
	void parsesAcceptedReview() {
		server.expect(requestTo("http://ai-service.test/analyze"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().json("{\"text\":\"La comida estaba fría y tardaron una hora.\"}"))
				.andRespond(withSuccess(ACCEPTED, MediaType.APPLICATION_JSON));

		AiAnalysisResponse response = client.analyze("La comida estaba fría y tardaron una hora.");

		assertThat(response.moderation().flagged()).isFalse();
		assertThat(response.moderation().reasons()).isEmpty();
		assertThat(response.moderation().scores()).containsEntry("toxicity", 0.001);
		assertThat(response.rating().stars()).isEqualTo(2);
		assertThat(response.rating().confidence()).isEqualTo(0.71);
		assertThat(response.modelVersion()).isEqualTo("nlptown-bert-v1");
		server.verify();
	}

	@Test
	void parsesFlaggedReviewWithNullRating() {
		server.expect(requestTo("http://ai-service.test/analyze"))
				.andRespond(withSuccess(FLAGGED, MediaType.APPLICATION_JSON));

		AiAnalysisResponse response = client.analyze("Texto ofensivo de ejemplo");

		assertThat(response.moderation().flagged()).isTrue();
		assertThat(response.moderation().reasons()).containsExactly("lenguaje tóxico", "insulto");
		assertThat(response.rating()).isNull();
	}

	@Test
	void status422IsARejectedRequest() {
		server.expect(requestTo("http://ai-service.test/analyze"))
				.andRespond(withStatus(HttpStatusCode.valueOf(422)).contentType(MediaType.APPLICATION_JSON)
						.body("{\"detail\":[]}"));

		assertThatThrownBy(() -> client.analyze("corta"))
				.isInstanceOf(AiRequestRejectedException.class);
	}

	@Test
	void status500IsTemporaryFailure() {
		server.expect(requestTo("http://ai-service.test/analyze"))
				.andRespond(withServerError().contentType(MediaType.APPLICATION_JSON)
						.body("{\"detail\":\"Error interno del servicio de IA\"}"));

		assertThatThrownBy(() -> client.analyze("Texto suficientemente largo"))
				.isInstanceOf(AiServiceUnavailableException.class);
	}

	@Test
	void connectionFailureIsTemporaryFailure() {
		server.expect(requestTo("http://ai-service.test/analyze"))
				.andRespond(withException(new IOException("Connection refused")));

		assertThatThrownBy(() -> client.analyze("Texto suficientemente largo"))
				.isInstanceOf(AiServiceUnavailableException.class)
				.hasRootCauseInstanceOf(IOException.class);
	}

	@Test
	void unreadableBodyIsTemporaryFailure() {
		server.expect(requestTo("http://ai-service.test/analyze"))
				.andRespond(withSuccess("esto no es json", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.analyze("Texto suficientemente largo"))
				.isInstanceOf(AiServiceUnavailableException.class);
	}
}
