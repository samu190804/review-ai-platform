package com.reviewai.backend.ai;

/**
 * Fallo temporal: 500, conexión rechazada, timeout o respuesta ilegible.
 * La reseña se guarda como PENDING y se reintenta más tarde.
 */
public class AiServiceUnavailableException extends AiServiceException {

	public AiServiceUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
