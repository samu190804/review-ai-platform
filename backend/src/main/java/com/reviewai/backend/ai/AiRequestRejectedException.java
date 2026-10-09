package com.reviewai.backend.ai;

/** El ai-service respondió 422: la petición no cumple el contrato. Reintentar no sirve. */
public class AiRequestRejectedException extends AiServiceException {

	public AiRequestRejectedException(String message) {
		super(message, null);
	}
}
