import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { describeApiError } from '../../reviews/api-error';
import { Review } from '../../reviews/review.model';
import { ReviewResult } from '../../reviews/review-result/review-result';
import { ReviewService } from '../../reviews/review.service';

@Component({
  selector: 'app-history-page',
  imports: [ReviewResult],
  templateUrl: './history-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoryPage implements OnInit {
  private readonly reviews = inject(ReviewService);

  protected readonly items = signal<Review[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    // Sin filtro ni paginación todavía: el backend devuelve la primera página (20 reseñas, las más recientes).
    this.reviews.list().subscribe({
      next: (page) => {
        this.items.set(page.content);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(describeApiError(err));
        this.loading.set(false);
      },
    });
  }
}
