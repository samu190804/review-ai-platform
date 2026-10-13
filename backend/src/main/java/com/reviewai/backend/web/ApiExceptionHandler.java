package com.reviewai.backend.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.reviewai.backend.ai.AiRequestRejectedException;
import com.reviewai.backend.review.ReviewNotFoundException;

/**
 * Manejo global de errores. Todos los errores salen con el formato estándar
 * ProblemDetail (RFC 9457): { "type", "title", "status", "detail", ... }.
 * ResponseEntityExceptionHandler ya cubre los errores comunes de Spring MVC
 * (JSON mal formado, parámetro con tipo incorrecto, método no permitido...).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ReviewNotFoundException.class)
	ProblemDetail handleNotFound(ReviewNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
	}

	/** El ai-service rechazó algo que nosotros ya validamos: desajuste entre ambos. */
	@ExceptionHandler(AiRequestRejectedException.class)
	ProblemDetail handleAiRejected(AiRequestRejectedException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
	}

	/** Añade el detalle por campo: { "errors": { "text": "mensaje" } }. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La petición no es válida");
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}
}
