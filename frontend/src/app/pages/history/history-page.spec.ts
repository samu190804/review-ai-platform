import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Review } from '../../reviews/review.model';
import { HistoryPage } from './history-page';

const review = (id: number, text: string): Review => ({
  id,
  text,
  status: 'APPROVED',
  stars: 4,
  confidence: 0.6,
  modelVersion: 'nlptown-bert-v1',
  moderationReasons: [],
  moderationScores: {},
  createdAt: '2026-10-18T14:02:11.381722Z',
});

describe('HistoryPage', () => {
  let fixture: ComponentFixture<HistoryPage>;
  let el: HTMLElement;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [HistoryPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HistoryPage);
    el = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  it('muestra "Cargando" mientras espera al backend', () => {
    expect(el.textContent).toContain('Cargando reseñas');
    http.expectOne('/api/reviews').flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('lista las reseñas recibidas', async () => {
    http.expectOne('/api/reviews').flush({
      content: [review(2, 'Segunda reseña de prueba'), review(1, 'Primera reseña de prueba')],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    });
    await fixture.whenStable();

    expect(el.querySelectorAll('app-review-result').length).toBe(2);
    expect(el.textContent).toContain('Segunda reseña de prueba');
    expect(el.textContent).not.toContain('Cargando');
  });

  it('explica que no hay reseñas cuando la lista está vacía', async () => {
    http.expectOne('/api/reviews').flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    await fixture.whenStable();

    expect(el.textContent).toContain('Todavía no hay reseñas');
  });

  it('muestra el error si el backend falla', async () => {
    http.expectOne('/api/reviews').flush({ detail: 'Algo falló' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Algo falló');
  });
});
