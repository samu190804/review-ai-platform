import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Review } from '../review.model';
import { ReviewResult } from './review-result';

const base: Review = {
  id: 1,
  text: 'La comida estaba fría y tardaron una hora.',
  status: 'APPROVED',
  stars: 2,
  confidence: 0.42,
  modelVersion: 'nlptown-bert-v1',
  moderationReasons: [],
  moderationScores: {},
  createdAt: '2026-10-18T14:02:11.381722Z',
};

describe('ReviewResult', () => {
  let fixture: ComponentFixture<ReviewResult>;

  async function render(review: Review): Promise<HTMLElement> {
    fixture = TestBed.createComponent(ReviewResult);
    fixture.componentRef.setInput('review', review);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => TestBed.configureTestingModule({ imports: [ReviewResult] }));

  it('APPROVED: muestra las estrellas y la confianza', async () => {
    const el = await render(base);
    expect(el.textContent).toContain('Aprobada');
    expect(el.textContent).toContain('★★☆☆☆');
    expect(el.textContent).toContain('42%');
    expect(el.querySelector('[aria-label="2 de 5 estrellas"]')).not.toBeNull();
  });

  it('REJECTED: muestra los motivos y ninguna estrella', async () => {
    const el = await render({
      ...base,
      status: 'REJECTED',
      stars: null,
      confidence: null,
      moderationReasons: ['lenguaje tóxico', 'insulto'],
    });
    expect(el.textContent).toContain('Rechazada');
    expect(el.textContent).toContain('lenguaje tóxico');
    expect(el.textContent).toContain('insulto');
    expect(el.textContent).not.toContain('★');
  });

  it('PENDING: explica que se reintentará y no inventa datos', async () => {
    const el = await render({
      ...base,
      status: 'PENDING',
      stars: null,
      confidence: null,
      modelVersion: null,
      moderationReasons: null,
      moderationScores: null,
    });
    expect(el.textContent).toContain('Pendiente de análisis');
    expect(el.textContent).toContain('se reintentará');
    expect(el.textContent).not.toContain('★');
    expect(el.textContent).not.toContain('%');
  });
});
