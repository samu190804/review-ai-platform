import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { describeApiError } from '../../reviews/api-error';
import { PageResponse, Review, ReviewStatus } from '../../reviews/review.model';
import { ReviewResult } from '../../reviews/review-result/review-result';
import { ReviewService } from '../../reviews/review.service';

const PAGE_SIZE = 10;

const STATUS_FILTERS: { value: ReviewStatus | null; label: string }[] = [
  { value: null, label: 'Todas' },
  { value: 'APPROVED', label: 'Aprobadas' },
  { value: 'REJECTED', label: 'Rechazadas' },
  { value: 'PENDING', label: 'Pendientes' },
];

@Component({
  selector: 'app-history-page',
  imports: [ReviewResult],
  templateUrl: './history-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoryPage implements OnInit, OnDestroy {
  private readonly reviews = inject(ReviewService);
  private request?: Subscription;

  protected readonly filters = STATUS_FILTERS;

  protected readonly status = signal<ReviewStatus | null>(null);
  /** Página actual; empieza en 0, como en la API. */
  protected readonly page = signal(0);
  protected readonly data = signal<PageResponse<Review> | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly items = computed(() => this.data()?.content ?? []);
  protected readonly totalPages = computed(() => this.data()?.totalPages ?? 0);

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.request?.unsubscribe();
  }

  protected selectStatus(status: ReviewStatus | null): void {
    if (status === this.status()) return;
    this.status.set(status);
    this.page.set(0); // otra lista, otra primera página
    this.load();
  }

  protected goTo(page: number): void {
    if (this.loading() || page < 0 || page >= this.totalPages()) return;
    this.page.set(page);
    this.load();
  }

  private load(): void {
    // Si el usuario cambia de filtro mientras hay una petición en vuelo, se descarta la anterior
    // para que una respuesta lenta no pise a la nueva.
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);

    this.request = this.reviews
      .list({ status: this.status() ?? undefined, page: this.page(), size: PAGE_SIZE })
      .subscribe({
        next: (data) => {
          this.data.set(data);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.data.set(null);
          this.error.set(describeApiError(err));
          this.loading.set(false);
        },
      });
  }
}
