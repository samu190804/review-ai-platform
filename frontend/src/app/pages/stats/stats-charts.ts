import type { ChartConfiguration } from 'chart.js';
import { ReviewStats, ReviewStatus } from '../../reviews/review.model';

/*
 * Datos y configuración de las gráficas del panel de estadísticas, sin Angular ni canvas:
 * son funciones puras, así que se prueban sin dibujar nada.
 *
 * Colores: los tres primeros de la paleta categórica validada (azul, naranja, aqua). Pasan la
 * comprobación de daltonismo entre todos los pares, cosa que el verde y el rojo habituales de
 * "aprobada" y "rechazada" no hacen. La identidad nunca depende solo del color: cada segmento
 * va con su nombre y su cifra en la leyenda, y hay una tabla con los mismos datos.
 */

const SURFACE = '#ffffff';
const GRID = '#e2e8f0'; // slate-200
const AXIS = '#cbd5e1'; // slate-300
const MUTED = '#64748b'; // slate-500
const INK = '#0f172a'; // slate-900
const INK_SECONDARY = '#334155'; // slate-700

/** Una reseña aprobada y las barras de estrellas comparten el azul: son la misma entidad. */
const STARS_COLOR = '#2a78d6';

const STATUS_VIEW: { status: ReviewStatus; label: string; color: string }[] = [
  { status: 'APPROVED', label: 'Aprobadas', color: '#2a78d6' },
  { status: 'REJECTED', label: 'Rechazadas', color: '#eb6834' },
  { status: 'PENDING', label: 'Pendientes', color: '#1baf7a' },
];

export interface StatusSlice {
  status: ReviewStatus;
  label: string;
  color: string;
  count: number;
  /** De 0 a 1 sobre el total; 0 si no hay reseñas. */
  share: number;
}

export interface StarsRow {
  stars: number;
  count: number;
}

const numberFormat = new Intl.NumberFormat('es-ES');
const percentFormat = new Intl.NumberFormat('es-ES', { style: 'percent', maximumFractionDigits: 0 });

export const formatCount = (value: number): string => numberFormat.format(value);
export const formatPercent = (share: number): string => percentFormat.format(share);

const reviewsWord = (count: number): string => (count === 1 ? 'reseña' : 'reseñas');
const starsWord = (stars: number): string => (stars === 1 ? 'estrella' : 'estrellas');

export function statusSlices(stats: ReviewStats): StatusSlice[] {
  return STATUS_VIEW.map(({ status, label, color }) => {
    const count = stats.byStatus[status] ?? 0;
    return { status, label, color, count, share: stats.total > 0 ? count / stats.total : 0 };
  });
}

export function starsRows(stats: ReviewStats): StarsRow[] {
  return [1, 2, 3, 4, 5].map((stars) => ({ stars, count: stats.starsDistribution[String(stars)] ?? 0 }));
}

/** Texto alternativo del canvas: quien no ve la gráfica recibe los mismos datos. */
export function describeStatus(slices: StatusSlice[]): string {
  const parts = slices.map((s) => `${s.label} ${formatCount(s.count)} (${formatPercent(s.share)})`);
  return `Gráfico de anillo con el estado de las reseñas: ${parts.join(', ')}.`;
}

export function describeStars(rows: StarsRow[]): string {
  const parts = rows.map((r) => `${r.stars} ${starsWord(r.stars)}: ${formatCount(r.count)}`);
  return `Gráfico de barras con las reseñas aprobadas por puntuación: ${parts.join(', ')}.`;
}

/** Quien pide menos movimiento en su sistema no recibe animaciones. */
function prefersReducedMotion(): boolean {
  return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
}

const TOOLTIP = {
  backgroundColor: SURFACE,
  titleColor: INK,
  bodyColor: INK_SECONDARY,
  borderColor: GRID,
  borderWidth: 1,
  padding: 10,
  cornerRadius: 8,
  boxPadding: 4,
};

export function statusChartConfig(slices: StatusSlice[]): ChartConfiguration<'doughnut'> {
  return {
    type: 'doughnut',
    data: {
      labels: slices.map((s) => s.label),
      datasets: [
        {
          data: slices.map((s) => s.count),
          backgroundColor: slices.map((s) => s.color),
          // El borde del color de la superficie hace de separación de 2px entre segmentos.
          borderColor: SURFACE,
          borderWidth: 2,
          hoverOffset: 4,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '68%',
      animation: prefersReducedMotion() ? false : { duration: 500 },
      plugins: {
        // La leyenda es HTML (nombre, cifra y porcentaje), no la del canvas.
        legend: { display: false },
        tooltip: {
          ...TOOLTIP,
          callbacks: {
            label: (context) => {
              const slice = slices[context.dataIndex];
              return ` ${slice.label}: ${formatCount(slice.count)} (${formatPercent(slice.share)})`;
            },
          },
        },
      },
    },
  };
}

export function starsChartConfig(rows: StarsRow[]): ChartConfiguration<'bar'> {
  return {
    type: 'bar',
    data: {
      labels: rows.map((r) => String(r.stars)),
      datasets: [
        {
          data: rows.map((r) => r.count),
          backgroundColor: STARS_COLOR,
          maxBarThickness: 24,
          // Esquinas redondeadas solo en el extremo del dato; la base queda recta sobre el eje.
          borderRadius: { topLeft: 4, topRight: 4 },
          borderSkipped: false,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      animation: prefersReducedMotion() ? false : { duration: 500 },
      scales: {
        x: {
          grid: { display: false },
          border: { color: AXIS },
          ticks: { color: MUTED },
          title: { display: true, text: 'Estrellas', color: MUTED },
        },
        y: {
          beginAtZero: true,
          border: { display: false },
          grid: { color: GRID, lineWidth: 1 },
          ticks: { color: MUTED, precision: 0 },
        },
      },
      plugins: {
        // Una sola serie: el título de la tarjeta ya dice qué se muestra.
        legend: { display: false },
        tooltip: {
          ...TOOLTIP,
          displayColors: false,
          callbacks: {
            title: (items) => {
              const row = rows[items[0].dataIndex];
              return `${row.stars} ${starsWord(row.stars)}`;
            },
            label: (context) => {
              const count = rows[context.dataIndex].count;
              return `${formatCount(count)} ${reviewsWord(count)}`;
            },
          },
        },
      },
    },
  };
}
