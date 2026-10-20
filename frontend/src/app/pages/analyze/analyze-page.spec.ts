import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Review } from '../../reviews/review.model';
import { AnalyzePage } from './analyze-page';

const approved: Review = {
  id: 5,
  text: 'La comida estaba fría y tardaron una hora en servirnos.',
  status: 'APPROVED',
  stars: 2,
  confidence: 0.42,
  modelVersion: 'nlptown-bert-v1',
  moderationReasons: [],
  moderationScores: {},
  createdAt: '2026-10-18T14:02:11.381722Z',
};

describe('AnalyzePage', () => {
  let fixture: ComponentFixture<AnalyzePage>;
  let el: HTMLElement;
  let http: HttpTestingController;

  const textarea = () => el.querySelector('textarea') as HTMLTextAreaElement;
  const submitButton = () => el.querySelector('button[type="submit"]') as HTMLButtonElement;

  async function type(value: string): Promise<void> {
    textarea().value = value;
    textarea().dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function submit(): Promise<void> {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [AnalyzePage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AnalyzePage);
    el = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
  });

  afterEach(() => http.verify());

  it('deshabilita el botón con el campo vacío', () => {
    expect(submitButton().disabled).toBe(true);
  });

  it('avisa si el texto es demasiado corto y no permite enviar', async () => {
    await type('corto');
    expect(el.textContent).toContain('Escribe al menos 10 caracteres');
    expect(submitButton().disabled).toBe(true);
  });

  it('avisa si el texto supera el máximo', async () => {
    await type('a'.repeat(2001));
    expect(el.textContent).toContain('El máximo es de 2000');
    expect(submitButton().disabled).toBe(true);
  });

  it('no cuenta los espacios de los extremos', async () => {
    await type('   corto   ');
    expect(el.textContent).toContain('5 / 2000');
  });

  it('envía el texto sin espacios y muestra el resultado', async () => {
    await type(`  ${approved.text}  `);
    expect(submitButton().disabled).toBe(false);

    await submit();
    const req = http.expectOne('/api/reviews');
    expect(req.request.body).toEqual({ text: approved.text });
    expect(submitButton().textContent).toContain('Analizando');
    expect(submitButton().disabled).toBe(true);

    req.flush(approved);
    await fixture.whenStable();

    expect(el.textContent).toContain('Resultado');
    expect(el.textContent).toContain('★★☆☆☆');
    expect(submitButton().textContent).toContain('Analizar');
  });

  it('muestra el mensaje del backend cuando falla', async () => {
    await type(approved.text);
    await submit();

    http
      .expectOne('/api/reviews')
      .flush(
        { detail: 'La petición no es válida', errors: { text: 'El texto debe tener entre 10 y 2000 caracteres' } },
        { status: 400, statusText: 'Bad Request' },
      );
    await fixture.whenStable();

    expect(el.querySelector('[role="alert"]')?.textContent).toContain('El texto debe tener entre 10 y 2000 caracteres');
    expect(el.textContent).not.toContain('Resultado');
  });

  it('no envía dos veces mientras espera la respuesta', async () => {
    await type(approved.text);
    await submit();
    await submit();

    http.expectOne('/api/reviews').flush(approved);
  });

  it('Limpiar vacía el campo y el resultado', async () => {
    await type(approved.text);
    await submit();
    http.expectOne('/api/reviews').flush(approved);
    await fixture.whenStable();

    (Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.includes('Limpiar')) as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(textarea().value).toBe('');
    expect(el.textContent).not.toContain('Resultado');
  });
});
