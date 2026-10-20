import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PageResponse, Review } from '../../reviews/review.model';
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

const pageOf = (content: Review[], page = 0, totalPages = 1, totalElements = content.length): PageResponse<Review> => ({
  content,
  page,
  size: 10,
  totalElements,
  totalPages,
});

describe('HistoryPage', () => {
  let fixture: ComponentFixture<HistoryPage>;
  let el: HTMLElement;
  let http: HttpTestingController;

  const expectList = (): TestRequest => http.expectOne((r) => r.method === 'GET' && r.url === '/api/reviews');
  const buttonByText = (text: string) =>
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === text) as HTMLButtonElement;

  async function answer(body: PageResponse<Review>): Promise<void> {
    expectList().flush(body);
    await fixture.whenStable();
  }

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

  it('muestra "Cargando" y pide la primera página de 10 sin filtro', async () => {
    expect(el.textContent).toContain('Cargando reseñas');
    const req = expectList();
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.has('status')).toBe(false);
    req.flush(pageOf([]));
  });

  it('lista las reseñas recibidas', async () => {
    await answer(pageOf([review(2, 'Segunda reseña de prueba'), review(1, 'Primera reseña de prueba')]));

    expect(el.querySelectorAll('app-review-result').length).toBe(2);
    expect(el.textContent).toContain('Segunda reseña de prueba');
    expect(el.textContent).not.toContain('Cargando');
  });

  it('explica que no hay reseñas cuando la lista está vacía', async () => {
    await answer(pageOf([]));
    expect(el.textContent).toContain('Todavía no hay reseñas');
  });

  it('muestra el error si el backend falla', async () => {
    expectList().flush({ detail: 'Algo falló' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Algo falló');
  });

  it('"Reintentar" repite la consulta con el mismo filtro y recupera la lista', async () => {
    await answer(pageOf([]));
    buttonByText('Rechazadas').click();
    await fixture.whenStable();
    expectList().flush({ detail: 'Algo falló' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();
    expect(el.querySelector('[role="alert"]')).not.toBeNull();

    buttonByText('Reintentar').click();
    await fixture.whenStable();
    const req = expectList();
    expect(req.request.params.get('status')).toBe('REJECTED');
    req.flush(pageOf([review(7, 'Reseña recuperada tras el error')]));
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.textContent).toContain('Reseña recuperada tras el error');
  });

  it('"Actualizar" repite la consulta con el filtro y la página actuales', async () => {
    await answer(pageOf([review(1, 'Una reseña cualquiera')], 0, 3, 25));
    buttonByText('Siguiente').click();
    await fixture.whenStable();
    await answer(pageOf([review(2, 'Otra reseña cualquiera')], 1, 3, 25));

    buttonByText('Actualizar').click();
    await fixture.whenStable();

    const req = expectList();
    expect(req.request.params.get('page')).toBe('1');
    req.flush(pageOf([review(2, 'Otra reseña cualquiera')], 1, 3, 25));
  });

  it('"Actualizar" se bloquea mientras carga', async () => {
    expect(buttonByText('Actualizar').disabled).toBe(true);
    await answer(pageOf([]));
    expect(buttonByText('Actualizar').disabled).toBe(false);
  });

  describe('filtro por estado', () => {
    it('al elegir un estado vuelve a pedir la lista con ese estado', async () => {
      await answer(pageOf([review(1, 'Una reseña cualquiera')]));

      buttonByText('Rechazadas').click();
      await fixture.whenStable();

      const req = expectList();
      expect(req.request.params.get('status')).toBe('REJECTED');
      expect(req.request.params.get('page')).toBe('0');
      req.flush(pageOf([]));
      await fixture.whenStable();

      expect(buttonByText('Rechazadas').getAttribute('aria-pressed')).toBe('true');
      expect(buttonByText('Todas').getAttribute('aria-pressed')).toBe('false');
      expect(el.textContent).toContain('No hay reseñas con este estado');
    });

    it('al cambiar de filtro vuelve a la primera página', async () => {
      await answer(pageOf([review(1, 'Una reseña cualquiera')], 0, 3, 25));
      buttonByText('Siguiente').click();
      await fixture.whenStable();
      await answer(pageOf([review(2, 'Otra reseña cualquiera')], 1, 3, 25));

      buttonByText('Aprobadas').click();
      await fixture.whenStable();

      expect(expectList().request.params.get('page')).toBe('0');
    });

    it('volver a pulsar el filtro activo no repite la petición', async () => {
      await answer(pageOf([review(1, 'Una reseña cualquiera')]));
      buttonByText('Todas').click();
      await fixture.whenStable();
      // afterEach comprueba que no hay peticiones pendientes
    });

    it('descarta la respuesta lenta de un filtro anterior', async () => {
      await answer(pageOf([]));

      buttonByText('Aprobadas').click();
      await fixture.whenStable();
      const slow = expectList();

      buttonByText('Rechazadas').click();
      await fixture.whenStable();
      const fast = expectList();

      expect(slow.cancelled).toBe(true);
      fast.flush(pageOf([review(9, 'Reseña rechazada de prueba')]));
      await fixture.whenStable();

      expect(el.textContent).toContain('Reseña rechazada de prueba');
    });
  });

  describe('paginación', () => {
    it('no aparece si todo cabe en una página', async () => {
      await answer(pageOf([review(1, 'Una reseña cualquiera')]));
      expect(el.querySelector('nav[aria-label="Paginación"]')).toBeNull();
    });

    it('indica la página y el total, y bloquea "Anterior" en la primera', async () => {
      await answer(pageOf([review(1, 'Una reseña cualquiera')], 0, 3, 25));

      expect(el.textContent).toContain('Página 1 de 3');
      expect(el.textContent).toContain('25 reseñas');
      expect(buttonByText('Anterior').disabled).toBe(true);
      expect(buttonByText('Siguiente').disabled).toBe(false);
    });

    it('"Siguiente" pide la página 1 y "Anterior" la 0', async () => {
      await answer(pageOf([review(1, 'Una reseña cualquiera')], 0, 3, 25));

      buttonByText('Siguiente').click();
      await fixture.whenStable();
      const next = expectList();
      expect(next.request.params.get('page')).toBe('1');
      next.flush(pageOf([review(2, 'Otra reseña cualquiera')], 1, 3, 25));
      await fixture.whenStable();
      expect(el.textContent).toContain('Página 2 de 3');

      buttonByText('Anterior').click();
      await fixture.whenStable();
      const prev = expectList();
      expect(prev.request.params.get('page')).toBe('0');
      prev.flush(pageOf([review(1, 'Una reseña cualquiera')], 0, 3, 25));
    });

    it('bloquea "Siguiente" en la última página', async () => {
      await answer(pageOf([review(3, 'Última reseña de prueba')], 2, 3, 25));
      expect(buttonByText('Siguiente').disabled).toBe(true);
      expect(buttonByText('Anterior').disabled).toBe(false);
    });

    it('conserva el filtro al cambiar de página', async () => {
      await answer(pageOf([]));
      buttonByText('Aprobadas').click();
      await fixture.whenStable();
      expectList().flush(pageOf([review(1, 'Una reseña cualquiera')], 0, 2, 12));
      await fixture.whenStable();

      buttonByText('Siguiente').click();
      await fixture.whenStable();

      const req = expectList();
      expect(req.request.params.get('status')).toBe('APPROVED');
      expect(req.request.params.get('page')).toBe('1');
      req.flush(pageOf([]));
    });
  });
});
