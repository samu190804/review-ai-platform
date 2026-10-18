import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreateReviewRequest, PageResponse, Review, ReviewStatus } from './review.model';

/**
 * Único punto de la app que conoce las URLs del backend.
 * Las rutas son relativas (/api/...): en desarrollo las reenvía el proxy (proxy.conf.json).
 */
@Injectable({ providedIn: 'root' })
export class ReviewService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/reviews';

  create(text: string): Observable<Review> {
    const body: CreateReviewRequest = { text };
    return this.http.post<Review>(this.baseUrl, body);
  }

  list(options: { status?: ReviewStatus; page?: number; size?: number } = {}): Observable<PageResponse<Review>> {
    let params = new HttpParams();
    if (options.status) params = params.set('status', options.status);
    if (options.page !== undefined) params = params.set('page', options.page);
    if (options.size !== undefined) params = params.set('size', options.size);
    return this.http.get<PageResponse<Review>>(this.baseUrl, { params });
  }

  get(id: number): Observable<Review> {
    return this.http.get<Review>(`${this.baseUrl}/${id}`);
  }
}
