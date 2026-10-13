package com.reviewai.backend.review;

public class ReviewNotFoundException extends RuntimeException {

	public ReviewNotFoundException(Long id) {
		super("No existe la reseña con id " + id);
	}
}
