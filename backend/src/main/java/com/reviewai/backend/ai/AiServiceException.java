package com.reviewai.backend.ai;

/**
 * Error al hablar con el ai-service. Las dos subclases distinguen lo que el
 * resto del backend necesita saber: ¿tiene sentido reintentar o no?
 */
public abstract class AiServiceException extends RuntimeException {

	protected AiServiceException(String message, Throwable cause) {
		super(message, cause);
	}
}
