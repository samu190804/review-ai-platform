package com.reviewai.backend.review;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una reseña y el resultado de su análisis. Se corresponde con la tabla "reviews". */
@Entity
@Table(name = "reviews")
public class Review {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, columnDefinition = "text")
	private String text;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReviewStatus status = ReviewStatus.PENDING;

	/** De 1 a 5. Null si la reseña fue marcada o aún no se analizó. */
	private Short stars;

	/** Probabilidad (0 a 1) que el modelo asigna a "stars". */
	private Double confidence;

	private String modelVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	private List<String> moderationReasons;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, Double> moderationScores;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	/** Constructor sin argumentos: lo exige JPA. */
	protected Review() {
	}

	public Review(String text) {
		this.text = text;
	}

	public Long getId() {
		return id;
	}

	public String getText() {
		return text;
	}

	public ReviewStatus getStatus() {
		return status;
	}

	public void setStatus(ReviewStatus status) {
		this.status = status;
	}

	public Short getStars() {
		return stars;
	}

	public void setStars(Short stars) {
		this.stars = stars;
	}

	public Double getConfidence() {
		return confidence;
	}

	public void setConfidence(Double confidence) {
		this.confidence = confidence;
	}

	public String getModelVersion() {
		return modelVersion;
	}

	public void setModelVersion(String modelVersion) {
		this.modelVersion = modelVersion;
	}

	public List<String> getModerationReasons() {
		return moderationReasons;
	}

	public void setModerationReasons(List<String> moderationReasons) {
		this.moderationReasons = moderationReasons;
	}

	public Map<String, Double> getModerationScores() {
		return moderationScores;
	}

	public void setModerationScores(Map<String, Double> moderationScores) {
		this.moderationScores = moderationScores;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
