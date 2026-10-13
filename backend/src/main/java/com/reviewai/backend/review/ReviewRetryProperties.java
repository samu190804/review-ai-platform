package com.reviewai.backend.review;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del job de reintentos (prefijo "reviews.retry").
 *
 * @param initialDelay espera antes de la primera ejecución tras arrancar
 * @param interval     espera entre el final de una ejecución y el inicio de la siguiente
 * @param batchSize    máximo de reseñas por ejecución
 * @param maxAttempts  intentos fallidos tras los cuales se deja de reintentar una reseña
 */
@ConfigurationProperties(prefix = "reviews.retry")
public record ReviewRetryProperties(Duration initialDelay, Duration interval, int batchSize, int maxAttempts) {
}
