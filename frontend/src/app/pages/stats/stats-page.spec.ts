import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import type { ChartConfiguration } from 'chart.js';
import { ReviewStats } from '../../reviews/review.model';
import { CHART_FACTORY } from './chart-canvas';
import { StatsPage } from './stats-page';

const stats = (overrides: Partial<ReviewStats> = {}): ReviewStats => ({
  total: 40,
  byStatus: { APPROVED: 30, REJECTED: 8, PENDING: 2 },
  starsDistribution: { '1': 4, '2': 5, '3': 6, '4': 9, '5': 6 },
  averageConfidence: 0.71,
  ...overrides,
});

const EMPTY: ReviewStats = {
  total: 0,
  byStatus: { APPROVED: 0, REJECTED: 0, PENDING: 0 },
  starsDistribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 },
  averageConfidence: null,
};

// Intl en español escribe el porcentaje con un espacio que no separa líneas (U+00A0).
const NBSP = ' ';

/** Texto de cada hijo directo, separado por espacios: así no depende de los saltos del HTML. */
const cells = (parent: Element): string =>
  Array.from(parent.children)
    .map((c) => c.textContent?.trim())
    .filter(Boolean) // la muestra de color de la leyenda no tiene texto
    .join(' ');

describe('StatsPage', () => {
  let fixture: ComponentFixture<StatsPage>;
  let el: HTMLElement;
  let http: HttpTestingController;
  let charts: ChartConfiguration[];

  const expectStats = (): TestRequest => http.expectOne('/api/reviews/stats');
  const buttonByText = (text: string) =>
    Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === text) as HTMLButtonElement;

  async function answer(body: ReviewStats): Promise<void> {
    expectStats().flush(body);
    await fixture.whenStable();
  }

  beforeEach(async () => {
    charts = [];
    TestBed.configureTestingModule({
      imports: [StatsPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // jsdom no tiene canvas: la fábrica falsa anota qué gráficas se piden.
        {
          provide: CHART_FACTORY,
          useValue: (_canvas: HTMLCanvasElement, config: ChartConfiguration) => {
            charts.push(config);
            return { destroy: () => undefined };
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(StatsPage);
    el = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  it('muestra "Cargando" y pide las estadísticas al backend', () => {
    expect(el.textContent).toContain('Cargando estadísticas');
    expectStats().flush(stats());
  });

  it('muestra el total y la confianza media', async () => {
    await answer(stats());

    const tiles = el.querySelector('dl')?.textContent ?? '';
    expect(tiles).toContain('Reseñas analizadas');
    expect(tiles).toContain('40');
    expect(tiles).toContain(`71${NBSP}%`);
    expect(el.textContent).not.toContain('Cargando');
  });

  it('muestra un guion si todavía no hay confianza media', async () => {
    await answer(stats({ total: 2, byStatus: { APPROVED: 0, REJECTED: 2, PENDING: 0 }, averageConfidence: null }));

    expect(el.querySelector('dl')?.textContent).toContain('—');
  });

  it('lista cada estado con su nombre, cantidad y porcentaje', async () => {
    await answer(stats());

    const items = Array.from(el.querySelectorAll('section ul li')).map(cells);
    expect(items).toEqual([`Aprobadas 30 75${NBSP}%`, `Rechazadas 8 20${NBSP}%`, `Pendientes 2 5${NBSP}%`]);
  });

  it('dibuja dos gráficas: el anillo de estados y las barras de puntuaciones', async () => {
    await answer(stats());

    expect(el.querySelectorAll('app-chart').length).toBe(2);
    // El orden en que Angular crea las gráficas no importa: se busca cada una por su tipo.
    expect(charts.map((c) => c.type).sort()).toEqual(['bar', 'doughnut']);
    expect(charts.find((c) => c.type === 'doughnut')?.data.datasets[0].data).toEqual([30, 8, 2]);
    expect(charts.find((c) => c.type === 'bar')?.data.datasets[0].data).toEqual([4, 5, 6, 9, 6]);
  });

  it('las gráficas llevan un texto alternativo con sus datos', async () => {
    await answer(stats());

    const labels = Array.from(el.querySelectorAll('canvas')).map((c) => c.getAttribute('aria-label') ?? '');
    expect(labels.find((l) => l.includes('anillo'))).toContain('Aprobadas 30');
    expect(labels.find((l) => l.includes('barras'))).toContain('5 estrellas: 6');
  });

  it('ofrece los mismos datos en una tabla', async () => {
    await answer(stats());

    const [byStatus, byStars] = Array.from(el.querySelectorAll('details table')).map((table) =>
      Array.from(table.querySelectorAll('tbody tr')).map(cells),
    );
    expect(byStatus).toEqual([`Aprobadas 30 75${NBSP}%`, `Rechazadas 8 20${NBSP}%`, `Pendientes 2 5${NBSP}%`]);
    expect(byStars).toEqual(['1 4', '2 5', '3 6', '4 9', '5 6']);
  });

  it('sin reseñas aprobadas no dibuja las barras y lo explica', async () => {
    await answer(stats({ total: 2, byStatus: { APPROVED: 0, REJECTED: 2, PENDING: 0 }, averageConfidence: null,
      starsDistribution: { '1': 0, '2': 0, '3': 0, '4': 0, '5': 0 } }));

    expect(charts.map((c) => c.type)).toEqual(['doughnut']);
    expect(el.textContent).toContain('Todavía no hay reseñas aprobadas con puntuación');
  });

  it('explica que no hay reseñas cuando la base de datos está vacía', async () => {
    await answer(EMPTY);

    expect(el.textContent).toContain('Todavía no hay reseñas');
    expect(el.querySelectorAll('app-chart').length).toBe(0);
  });

  it('muestra el error si el backend falla y permite reintentar', async () => {
    expectStats().flush({ detail: 'Algo falló' }, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('Algo falló');

    buttonByText('Reintentar').click();
    await answer(stats());

    expect(el.querySelector('[role="alert"]')).toBeNull();
    expect(el.textContent).toContain('40');
  });

  it('"Actualizar" vuelve a pedir los datos y conserva lo anterior mientras tanto', async () => {
    await answer(stats());

    buttonByText('Actualizar').click();
    await fixture.whenStable();

    expect(buttonByText('Actualizar').disabled).toBe(true);
    expect(el.textContent).toContain('40'); // sigue visible, no salta a "Cargando"
    expect(el.textContent).not.toContain('Cargando');

    await answer(stats({ total: 41, byStatus: { APPROVED: 31, REJECTED: 8, PENDING: 2 } }));
    expect(el.querySelector('dl')?.textContent).toContain('41');
    expect(buttonByText('Actualizar').disabled).toBe(false);
  });
});
