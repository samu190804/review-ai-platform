import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';
import { describeApiError } from '../../reviews/api-error';
import { ReviewStats } from '../../reviews/review.model';
import { ReviewService } from '../../reviews/review.service';
import { ChartCanvas } from './chart-canvas';
import {
  describeStars,
  describeStatus,
  formatCount,
  formatPercent,
  starsChartConfig,
  starsRows,
  statusChartConfig,
  statusSlices,
} from './stats-charts';

@Component({
  selector: 'app-stats-page',
  imports: [ChartCanvas],
  templateUrl: './stats-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatsPage implements OnInit, OnDestroy {
  private readonly reviews = inject(ReviewService);
  private request?: Subscription;

  protected readonly formatCount = formatCount;
  protected readonly formatPercent = formatPercent;

  protected readonly stats = signal<ReviewStats | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly slices = computed(() => {
    const current = this.stats();
    return current ? statusSlices(current) : [];
  });
  protected readonly statusChart = computed(() => statusChartConfig(this.slices()));
  protected readonly statusLabel = computed(() => describeStatus(this.slices()));

  protected readonly stars = computed(() => {
    const current = this.stats();
    return current ? starsRows(current) : [];
  });
  protected readonly starsChart = computed(() => starsChartConfig(this.stars()));
  protected readonly starsLabel = computed(() => describeStars(this.stars()));
  /** Reseñas con puntuación: sin ninguna, un gráfico de barras vacío no cuenta nada. */
  protected readonly approvedCount = computed(() => this.stats()?.byStatus.APPROVED ?? 0);

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.request?.unsubscribe();
  }

  /** Vuelve a pedir los datos: para reintentar tras un error o para actualizar. */
  protected reload(): void {
    this.load();
  }

  private load(): void {
    this.request?.unsubscribe();
    this.loading.set(true);
    this.error.set(null);

    this.request = this.reviews.stats().subscribe({
      next: (stats) => {
        this.stats.set(stats);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.stats.set(null);
        this.error.set(describeApiError(err));
        this.loading.set(false);
      },
    });
  }
}
