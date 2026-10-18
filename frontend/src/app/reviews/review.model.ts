// Tipos que reflejan docs/backend-api.md. Si la API cambia, se cambia primero ese documento.

export type ReviewStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface Review {
  id: number;
  text: string;
  status: ReviewStatus;
  /** De 1 a 5. null si la reseña está REJECTED o PENDING. */
  stars: number | null;
  /** De 0 a 1. null si la reseña está REJECTED o PENDING. */
  confidence: number | null;
  /** null si la reseña está PENDING. */
  modelVersion: string | null;
  /** Vacío si no se marcó; null si está PENDING. */
  moderationReasons: string[] | null;
  /** Puntuación de 0 a 1 por categoría de Detoxify; null si está PENDING. */
  moderationScores: Record<string, number> | null;
  /** Fecha ISO 8601 en UTC. */
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  /** Número de página, empieza en 0. */
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateReviewRequest {
  text: string;
}

/** Límites del texto, iguales a los del backend y el ai-service. */
export const REVIEW_TEXT_MIN = 10;
export const REVIEW_TEXT_MAX = 2000;
