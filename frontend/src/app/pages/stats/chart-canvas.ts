import { ChangeDetectionStrategy, Component, ElementRef, InjectionToken, afterRenderEffect, inject, input, viewChild } from '@angular/core';
import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  type ChartConfiguration,
  DoughnutController,
  LinearScale,
  Tooltip,
} from 'chart.js';

// Solo se registra lo que usan las gráficas del panel: el resto de Chart.js no entra en el paquete.
Chart.register(ArcElement, BarController, BarElement, CategoryScale, DoughnutController, LinearScale, Tooltip);

export interface ChartHandle {
  destroy(): void;
}

export type ChartFactory = (canvas: HTMLCanvasElement, config: ChartConfiguration) => ChartHandle;

/**
 * Cómo se dibuja una gráfica. Es un token para poder sustituirlo en los tests: jsdom no tiene
 * canvas, así que allí se inyecta una fábrica falsa y se comprueba qué configuración recibe.
 */
export const CHART_FACTORY = new InjectionToken<ChartFactory>('CHART_FACTORY', {
  providedIn: 'root',
  factory: () => (canvas, config) => new Chart(canvas, config),
});

/** Envuelve un canvas de Chart.js: lo dibuja con la configuración recibida y lo destruye al irse. */
@Component({
  selector: 'app-chart',
  template: `<canvas #canvas role="img" [attr.aria-label]="label()"></canvas>`,
  // Chart.js se adapta al tamaño de este contenedor, que debe ser relativo y tener la altura de su padre.
  host: { class: 'relative block h-full w-full' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChartCanvas {
  readonly config = input.required<ChartConfiguration>();
  /** Texto alternativo: lo que diría la gráfica a quien no puede verla. */
  readonly label = input.required<string>();

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private readonly createChart = inject(CHART_FACTORY);

  constructor() {
    // Se repite cada vez que cambia la configuración; la gráfica anterior se destruye antes de crear la nueva.
    afterRenderEffect((onCleanup) => {
      const chart = this.createChart(this.canvas().nativeElement, this.config());
      onCleanup(() => chart.destroy());
    });
  }
}
