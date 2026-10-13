package com.reviewai.backend.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /api/reviews. Los límites coinciden con los del ai-service (docs/api.md). */
public record CreateReviewRequest(
		@NotBlank(message = "El texto es obligatorio")
		@Size(min = 10, max = 2000, message = "El texto debe tener entre 10 y 2000 caracteres")
		String text) {
}
