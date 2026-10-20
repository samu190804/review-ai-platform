import { DatePipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Review, ReviewStatus } from '../review.model';

// Las clases van completas (no concatenadas) para que Tailwind las detecte al compilar.
const STATUS_VIEW: Record<ReviewStatus, { label: string; badge: string }> = {
  APPROVED: { label: 'Aprobada', badge: 'bg-emerald-100 text-emerald-800' },
  REJECTED: { label: 'Rechazada', badge: 'bg-red-100 text-red-800' },
  PENDING: { label: 'Pendiente de análisis', badge: 'bg-amber-100 text-amber-800' },
};

/** Muestra una reseña y su análisis. Cubre los tres estados: cada uno enseña lo que realmente tiene. */
@Component({
  selector: 'app-review-result',
  imports: [DatePipe, PercentPipe],
  templateUrl: './review-result.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewResult {
  readonly review = input.required<Review>();

  protected readonly view = computed(() => STATUS_VIEW[this.review().status]);

  /** Cinco posiciones, true = estrella rellena. Vacío si no hay puntuación. */
  protected readonly stars = computed(() => {
    const value = this.review().stars;
    return value === null ? [] : Array.from({ length: 5 }, (_, i) => i < value);
  });
}
