package com.reviewai.backend.review;

import java.net.URI;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

	private final ReviewService service;

	public ReviewController(ReviewService service) {
		this.service = service;
	}

	/** 201 Created con la cabecera Location apuntando a la reseña nueva. */
	@PostMapping
	public ResponseEntity<ReviewResponse> create(@Valid @RequestBody CreateReviewRequest request) {
		Review review = service.create(request.text());
		return ResponseEntity
				.created(URI.create("/api/reviews/" + review.getId()))
				.body(ReviewResponse.from(review));
	}

	/** Ejemplo: GET /api/reviews?status=APPROVED&page=0&size=20 */
	@GetMapping
	public PageResponse<ReviewResponse> list(
			@RequestParam(required = false) ReviewStatus status,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(service.list(status, pageable), ReviewResponse::from);
	}

	/** Resumen para el panel de análisis. Spring prefiere esta ruta fija a "/{id}". */
	@GetMapping("/stats")
	public ReviewStats stats() {
		return service.stats();
	}

	@GetMapping("/{id}")
	public ReviewResponse get(@PathVariable Long id) {
		return ReviewResponse.from(service.get(id));
	}
}
