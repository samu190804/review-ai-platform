package com.reviewai.backend.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a datos. Es solo una interfaz: Spring Data genera la implementación
 * (save, findById, findAll, delete...) y también las consultas a partir del
 * nombre de los métodos.
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

	Page<Review> findByStatus(ReviewStatus status, Pageable pageable);
}
