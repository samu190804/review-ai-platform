import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Review } from './review.model';
import { ReviewService } from './review.service';

const review: Review = {
  id: 1,
  text: 'La comida estaba fría y tardaron una hora.',
  status: 'APPROVED',
  stars: 1,
  confidence: 0.44,
  modelVersion: 'nlptown-bert-v1',
  moderationReasons: [],
  moderationScores: { toxicity: 0.001 },
  createdAt: '2026-10-09T11:14:03.030235Z',
};

describe('ReviewService', () => {
  let service: ReviewService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ReviewService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('crea una reseña con POST /api/reviews y el texto en el cuerpo', () => {
    let result: Review | undefined;
    service.create(review.text).subscribe((r) => (result = r));

    const req = http.expectOne('/api/reviews');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ text: review.text });
    req.flush(review);

    expect(result).toEqual(review);
  });

  it('pide las estadísticas con GET /api/reviews/stats', () => {
    const body = {
      total: 3,
      byStatus: { APPROVED: 2, REJECTED: 1, PENDING: 0 },
      starsDistribution: { '1': 0, '2': 1, '3': 0, '4': 1, '5': 0 },
      averageConfidence: 0.7,
    };
    let result: unknown;
    service.stats().subscribe((r) => (result = r));

    const req = http.expectOne('/api/reviews/stats');
    expect(req.request.method).toBe('GET');
    req.flush(body);

    expect(result).toEqual(body);
  });

  it('lista sin parámetros cuando no se pide nada', () => {
    service.list().subscribe();

    const req = http.expectOne('/api/reviews');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('envía status, page y size al listar, incluida la página 0', () => {
    service.list({ status: 'APPROVED', page: 0, size: 5 }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/reviews');
    expect(req.request.params.get('status')).toBe('APPROVED');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('5');
    req.flush({ content: [review], page: 0, size: 5, totalElements: 1, totalPages: 1 });
  });

  it('pide una reseña por id con GET /api/reviews/{id}', () => {
    service.get(7).subscribe();

    const req = http.expectOne('/api/reviews/7');
    expect(req.request.method).toBe('GET');
    req.flush(review);
  });
});
