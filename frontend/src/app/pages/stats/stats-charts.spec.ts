import { ReviewStats } from '../../reviews/review.model';
import {
  describeStars,
  describeStatus,
  formatPercent,
  starsChartConfig,
  starsRows,
  statusChartConfig,
  statusSlices,
} from './stats-charts';

// Intl en español escribe el porcentaje con un espacio que no separa líneas (U+00A0).
const NBSP = ' ';

const stats: ReviewStats = {
  total: 40,
  byStatus: { APPROVED: 30, REJECTED: 8, PENDING: 2 },
  starsDistribution: { '1': 4, '2': 5, '3': 6, '4': 9, '5': 6 },
  averageConfidence: 0.71,
};

describe('statusSlices', () => {
  it('devuelve los tres estados en un orden fijo con su cantidad y su proporción', () => {
    const slices = statusSlices(stats);

    expect(slices.map((s) => [s.status, s.count])).toEqual([
      ['APPROVED', 30],
      ['REJECTED', 8],
      ['PENDING', 2],
    ]);
    expect(slices[0].share).toBeCloseTo(0.75);
    expect(slices[2].share).toBeCloseTo(0.05);
  });

  it('da proporción 0 cuando no hay reseñas, sin dividir entre cero', () => {
    const empty: ReviewStats = { ...stats, total: 0, byStatus: { APPROVED: 0, REJECTED: 0, PENDING: 0 } };

    expect(statusSlices(empty).map((s) => s.share)).toEqual([0, 0, 0]);
  });

  it('usa colores distintos para cada estado', () => {
    const colors = statusSlices(stats).map((s) => s.color);
    expect(new Set(colors).size).toBe(3);
  });
});

describe('starsRows', () => {
  it('devuelve siempre las cinco puntuaciones en orden', () => {
    expect(starsRows(stats)).toEqual([
      { stars: 1, count: 4 },
      { stars: 2, count: 5 },
      { stars: 3, count: 6 },
      { stars: 4, count: 9 },
      { stars: 5, count: 6 },
    ]);
  });

  it('rellena con 0 las puntuaciones que falten', () => {
    const partial: ReviewStats = { ...stats, starsDistribution: { '5': 2 } };

    expect(starsRows(partial).map((r) => r.count)).toEqual([0, 0, 0, 0, 2]);
  });
});

describe('statusChartConfig', () => {
  const slices = statusSlices(stats);
  const config = statusChartConfig(slices);

  it('es un anillo con un dato y un color por estado', () => {
    expect(config.type).toBe('doughnut');
    expect(config.data.labels).toEqual(['Aprobadas', 'Rechazadas', 'Pendientes']);
    expect(config.data.datasets[0].data).toEqual([30, 8, 2]);
    expect(config.data.datasets[0].backgroundColor).toEqual(slices.map((s) => s.color));
  });

  it('separa los segmentos con un borde de 2px del color de la superficie', () => {
    expect(config.data.datasets[0].borderWidth).toBe(2);
    expect(config.data.datasets[0].borderColor).toBe('#ffffff');
  });

  it('oculta la leyenda del canvas, porque la leyenda es HTML', () => {
    expect(config.options?.plugins?.legend?.display).toBe(false);
  });

  it('el tooltip muestra el nombre, la cantidad y el porcentaje', () => {
    const label = config.options?.plugins?.tooltip?.callbacks?.label as (c: { dataIndex: number }) => string;

    expect(label({ dataIndex: 1 })).toBe(` Rechazadas: 8 (20${NBSP}%)`);
  });
});

describe('starsChartConfig', () => {
  const rows = starsRows(stats);
  const config = starsChartConfig(rows);

  it('es de barras, con las cinco puntuaciones y su cantidad', () => {
    expect(config.type).toBe('bar');
    expect(config.data.labels).toEqual(['1', '2', '3', '4', '5']);
    expect(config.data.datasets[0].data).toEqual([4, 5, 6, 9, 6]);
  });

  it('usa barras finas con el extremo redondeado y la base recta', () => {
    const dataset = config.data.datasets[0];

    expect(dataset.maxBarThickness).toBe(24);
    expect(dataset.borderRadius).toEqual({ topLeft: 4, topRight: 4 });
    expect(dataset.borderSkipped).toBe(false);
  });

  it('empieza en cero y muestra solo enteros en el eje de reseñas', () => {
    const y = config.options?.scales?.['y'] as { beginAtZero: boolean; ticks: { precision: number } };

    expect(y.beginAtZero).toBe(true);
    expect(y.ticks.precision).toBe(0);
  });

  it('el tooltip concuerda en singular y plural', () => {
    const callbacks = config.options?.plugins?.tooltip?.callbacks as {
      title: (items: { dataIndex: number }[]) => string;
      label: (c: { dataIndex: number }) => string;
    };
    const one = starsChartConfig([{ stars: 1, count: 1 }]).options?.plugins?.tooltip?.callbacks as typeof callbacks;

    expect(callbacks.title([{ dataIndex: 4 }])).toBe('5 estrellas');
    expect(callbacks.label({ dataIndex: 3 })).toBe('9 reseñas');
    expect(one.title([{ dataIndex: 0 }])).toBe('1 estrella');
    expect(one.label({ dataIndex: 0 })).toBe('1 reseña');
  });
});

describe('textos alternativos', () => {
  it('describen todos los datos del anillo', () => {
    const text = describeStatus(statusSlices(stats));

    expect(text).toContain(`Aprobadas 30 (75${NBSP}%)`);
    expect(text).toContain(`Rechazadas 8 (20${NBSP}%)`);
    expect(text).toContain(`Pendientes 2 (5${NBSP}%)`);
  });

  it('describen todos los datos de las barras', () => {
    const text = describeStars(starsRows(stats));

    expect(text).toContain('1 estrella: 4');
    expect(text).toContain('5 estrellas: 6');
  });
});

describe('formatPercent', () => {
  it('usa el formato español', () => {
    expect(formatPercent(0.714)).toBe(`71${NBSP}%`);
  });
});
