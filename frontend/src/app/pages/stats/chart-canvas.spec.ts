import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import type { ChartConfiguration } from 'chart.js';
import { CHART_FACTORY, ChartCanvas } from './chart-canvas';

const configWith = (value: number): ChartConfiguration => ({
  type: 'bar',
  data: { labels: ['a'], datasets: [{ data: [value] }] },
});

@Component({
  imports: [ChartCanvas],
  template: `<app-chart [config]="config()" label="Gráfico de prueba" />`,
})
class Host {
  readonly config = signal(configWith(1));
}

describe('ChartCanvas', () => {
  let created: { canvas: HTMLCanvasElement; config: ChartConfiguration }[];
  let destroyed: number;
  let fixture: ComponentFixture<Host>;

  beforeEach(async () => {
    created = [];
    destroyed = 0;
    TestBed.configureTestingModule({
      imports: [Host],
      providers: [
        {
          provide: CHART_FACTORY,
          useValue: (canvas: HTMLCanvasElement, config: ChartConfiguration) => {
            created.push({ canvas, config });
            return { destroy: () => destroyed++ };
          },
        },
      ],
    });
    fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
  });

  it('dibuja la gráfica en su canvas con la configuración recibida', () => {
    expect(created.length).toBe(1);
    expect(created[0].canvas).toBe(fixture.nativeElement.querySelector('canvas'));
    expect(created[0].config).toBe(fixture.componentInstance.config());
  });

  it('describe la gráfica con role="img" y un texto alternativo', () => {
    const canvas = fixture.nativeElement.querySelector('canvas') as HTMLCanvasElement;

    expect(canvas.getAttribute('role')).toBe('img');
    expect(canvas.getAttribute('aria-label')).toBe('Gráfico de prueba');
  });

  it('destruye la gráfica anterior y crea otra cuando cambia la configuración', async () => {
    fixture.componentInstance.config.set(configWith(2));
    await fixture.whenStable();

    expect(created.length).toBe(2);
    expect(destroyed).toBe(1);
    expect(created[1].config.data.datasets[0].data).toEqual([2]);
  });

  it('destruye la gráfica al desaparecer el componente', () => {
    fixture.destroy();

    expect(destroyed).toBe(1);
  });
});
