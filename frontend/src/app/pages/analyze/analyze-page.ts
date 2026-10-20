import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { describeApiError } from '../../reviews/api-error';
import { REVIEW_TEXT_MAX, REVIEW_TEXT_MIN, Review } from '../../reviews/review.model';
import { ReviewResult } from '../../reviews/review-result/review-result';
import { ReviewService } from '../../reviews/review.service';

@Component({
  selector: 'app-analyze-page',
  imports: [ReviewResult],
  templateUrl: './analyze-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AnalyzePage {
  private readonly reviews = inject(ReviewService);

  protected readonly min = REVIEW_TEXT_MIN;
  protected readonly max = REVIEW_TEXT_MAX;

  protected readonly text = signal('');
  protected readonly sending = signal(false);
  protected readonly result = signal<Review | null>(null);
  protected readonly error = signal<string | null>(null);

  // Se mide el texto sin espacios en los extremos, que es también lo que se envía.
  // string.length cuenta unidades UTF-16, igual que Java en el backend.
  protected readonly length = computed(() => this.text().trim().length);
  protected readonly tooShort = computed(() => this.length() > 0 && this.length() < this.min);
  protected readonly tooLong = computed(() => this.length() > this.max);
  protected readonly canSubmit = computed(
    () => this.length() >= this.min && this.length() <= this.max && !this.sending(),
  );

  protected onInput(event: Event): void {
    this.text.set((event.target as HTMLTextAreaElement).value);
  }

  protected submit(): void {
    if (!this.canSubmit()) return;

    this.sending.set(true);
    this.error.set(null);
    this.result.set(null);

    this.reviews.create(this.text().trim()).subscribe({
      next: (review) => {
        this.result.set(review);
        this.sending.set(false);
      },
      error: (err: unknown) => {
        this.error.set(describeApiError(err));
        this.sending.set(false);
      },
    });
  }

  protected reset(): void {
    this.text.set('');
    this.result.set(null);
    this.error.set(null);
  }
}
