package com.reviewai.backend.review;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a datos. Es solo una interfaz: Spring Data genera la implementación
 * (save, findById, findAll, delete...) y también las consultas a partir del
 * nombre de los métodos.
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

	Page<Review> findByStatus(ReviewStatus status, Pageable pageable);

	/**
	 * Reseñas que el job puede reintentar: en el estado dado y con menos de
	 * "maxAttempts" intentos fallidos. Las de menos intentos van primero y, a igualdad,
	 * las más antiguas. Cuando la consulta es demasiado larga para expresarla con el
	 * nombre del método, se escribe con @Query (JPQL: como SQL, pero sobre las entidades).
	 */
	@Query("""
			select r from Review r
			where r.status = :status and r.analysisAttempts < :maxAttempts
			order by r.analysisAttempts asc, r.createdAt asc
			""")
	List<Review> findRetryable(@Param("status") ReviewStatus status, @Param("maxAttempts") int maxAttempts,
			Pageable pageable);
}
