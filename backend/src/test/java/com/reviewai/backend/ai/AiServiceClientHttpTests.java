package com.reviewai.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.reviewai.backend.FakeAiService;
import com.reviewai.backend.FakeAiService.RecordedRequest;

// Prueba el cliente con el RestClient REAL de producción (AiServiceConfig) contra un
// servidor HTTP de verdad. Cubre lo que MockRestServiceServer no puede: el protocolo
// que habla el cliente y los timeouts.
class AiServiceClientHttpTests {

	private static FakeAiService fake;
	private static AiServiceClient client;
	private static AiServiceClient impatientClient;

	@BeforeAll
	static void startFakeService() {
		fake = new FakeAiService();
		// Timeout generoso: la primera petición de un cliente HTTP recién creado es lenta.
		client = clientWithReadTimeout(Duration.ofSeconds(10));
		impatientClient = clientWithReadTimeout(Duration.ofMillis(300));
	}

	private static AiServiceClient clientWithReadTimeout(Duration readTimeout) {
		RestClient restClient = new AiServiceConfig().aiServiceRestClient(
				new AiServiceProperties(fake.url(), Duration.ofSeconds(2), readTimeout));
		return new AiServiceClient(restClient);
	}

	@AfterAll
	static void stopFakeService() {
		fake.close();
	}

	@BeforeEach
	void resetFake() {
		fake.reset();
	}

	@Test
	void sendsAPlainHttp11JsonPostWithoutProtocolUpgrade() {
		client.analyze("La comida estaba fría y tardaron una hora.");

		RecordedRequest request = fake.requests().get(0);
		assertThat(request.method()).isEqualTo("POST");
		assertThat(request.path()).isEqualTo("/analyze");
		assertThat(request.headers().getFirst("Content-Type")).startsWith("application/json");
		assertThat(request.body()).contains("\"text\"").contains("fría");
		// Regresión: el cliente de Java intenta por defecto subir a HTTP/2 (Upgrade: h2c)
		// y uvicorn, que solo habla HTTP/1.1, descarta el cuerpo y responde 422.
		assertThat(request.headers().getFirst("Upgrade")).isNull();
		assertThat(request.headers().getFirst("HTTP2-Settings")).isNull();
	}

	@Test
	void readsAcceptedAndFlaggedResponses() {
		assertThat(client.analyze("Una reseña de ejemplo bastante normal.").rating().stars()).isEqualTo(2);

		fake.respondWith(200, FakeAiService.FLAGGED);
		AiAnalysisResponse flagged = client.analyze("Una reseña de ejemplo ofensiva.");
		assertThat(flagged.moderation().flagged()).isTrue();
		assertThat(flagged.moderation().reasons()).containsExactly("lenguaje tóxico", "insulto");
		assertThat(flagged.rating()).isNull();
	}

	@Test
	void status422IsARejectedRequest() {
		fake.respondWith(422, "{\"detail\":[]}");

		assertThatThrownBy(() -> client.analyze("Texto suficientemente largo"))
				.isInstanceOf(AiRequestRejectedException.class);
	}

	@Test
	void status500IsTemporaryFailure() {
		fake.respondWith(500, FakeAiService.INTERNAL_ERROR);

		assertThatThrownBy(() -> client.analyze("Texto suficientemente largo"))
				.isInstanceOf(AiServiceUnavailableException.class);
	}

	@Test
	void slowResponseHitsReadTimeoutAndIsTemporaryFailure() {
		fake.delayResponses(Duration.ofSeconds(2));

		assertThatThrownBy(() -> impatientClient.analyze("Texto suficientemente largo"))
				.isInstanceOf(AiServiceUnavailableException.class);
	}

	@Test
	void unreachableServiceIsTemporaryFailure() {
		// Puerto 9 (discard): nadie escucha, la conexión se rechaza.
		AiServiceClient unreachable = new AiServiceClient(new AiServiceConfig().aiServiceRestClient(
				new AiServiceProperties("http://127.0.0.1:9", Duration.ofSeconds(2), Duration.ofSeconds(2))));

		assertThatThrownBy(() -> unreachable.analyze("Texto suficientemente largo"))
				.isInstanceOf(AiServiceUnavailableException.class);
	}
}
