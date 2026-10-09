package com.reviewai.backend.ai;

import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente del ai-service. Implementa el contrato de docs/api.md y traduce sus
 * errores a dos excepciones propias (ver AiServiceException).
 *
 * Spring crea una sola instancia de esta clase (por ser @Component) y le pasa
 * el RestClient por el constructor: eso es la inyección de dependencias.
 */
@Component
public class AiServiceClient {

	private static final int UNPROCESSABLE = 422;

	private final RestClient restClient;

	public AiServiceClient(RestClient aiServiceRestClient) {
		this.restClient = aiServiceRestClient;
	}

	public AiAnalysisResponse analyze(String text) {
		try {
			AiAnalysisResponse response = restClient.post()
					.uri("/analyze")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("text", text))
					.retrieve()
					.onStatus(status -> status.value() == UNPROCESSABLE, (request, httpResponse) -> {
						throw new AiRequestRejectedException(
								"El ai-service rechazó la petición (422): revisa la validación del texto");
					})
					.onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
						throw new AiServiceUnavailableException(
								"El ai-service respondió con error " + httpResponse.getStatusCode().value(), null);
					})
					.body(AiAnalysisResponse.class);

			if (response == null || response.moderation() == null) {
				throw new AiServiceUnavailableException("Respuesta vacía o incompleta del ai-service", null);
			}
			return response;
		}
		catch (AiServiceException e) {
			throw e;
		}
		catch (RestClientException e) {
			// Conexión rechazada, timeout, JSON ilegible...
			throw new AiServiceUnavailableException("No se pudo obtener respuesta del ai-service", e);
		}
	}
}
