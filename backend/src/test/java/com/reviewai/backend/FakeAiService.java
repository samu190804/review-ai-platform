package com.reviewai.backend;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * ai-service de mentira para los tests: un servidor HTTP real en un puerto libre
 * que responde a POST /analyze con lo que cada test configure y apunta las
 * peticiones recibidas. A diferencia de MockRestServiceServer, la petición
 * viaja por la red de verdad, así que también prueba el cliente HTTP.
 */
public class FakeAiService implements AutoCloseable {

	public static final String ACCEPTED = """
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

	public static final String FLAGGED = """
			{
			  "moderation": {
			    "flagged": true,
			    "reasons": ["lenguaje tóxico", "insulto"],
			    "scores": { "toxicity": 0.96, "severe_toxicity": 0.12, "obscene": 0.41,
			                "identity_attack": 0.03, "insult": 0.91, "threat": 0.01, "sexual_explicit": 0.02 }
			  },
			  "rating": null,
			  "model_version": "nlptown-bert-v1"
			}
			""";

	public static final String INTERNAL_ERROR = "{\"detail\":\"Error interno del servicio de IA\"}";

	/** Una petición recibida por el servidor falso. */
	public record RecordedRequest(String method, String path, Headers headers, String body) {
	}

	private final HttpServer server;
	private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
	private volatile int status = 200;
	private volatile String body = ACCEPTED;
	private volatile Duration delay = Duration.ZERO;

	public FakeAiService() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException e) {
			throw new IllegalStateException("No se pudo arrancar el ai-service falso", e);
		}
		server.createContext("/analyze", this::handle);
		server.start();
	}

	public String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	/** Lo que responderá a partir de ahora. */
	public void respondWith(int status, String body) {
		this.status = status;
		this.body = body;
	}

	/** Retraso antes de responder, para probar timeouts. */
	public void delayResponses(Duration delay) {
		this.delay = delay;
	}

	public List<RecordedRequest> requests() {
		return List.copyOf(requests);
	}

	/** Deja el servidor como al empezar: respuesta aceptada, sin retraso, sin peticiones apuntadas. */
	public void reset() {
		respondWith(200, ACCEPTED);
		delay = Duration.ZERO;
		requests.clear();
	}

	private void handle(HttpExchange exchange) throws IOException {
		String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		requests.add(new RecordedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
				exchange.getRequestHeaders(), requestBody));

		try {
			Thread.sleep(delay.toMillis());
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		byte[] response = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		try {
			exchange.sendResponseHeaders(status, response.length);
			exchange.getResponseBody().write(response);
		}
		catch (IOException e) {
			// El cliente cerró la conexión (p. ej. por timeout): no es un error del test.
		}
		finally {
			exchange.close();
		}
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
