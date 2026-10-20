import { HttpErrorResponse } from '@angular/common/http';
import { describeApiError } from './api-error';

const httpError = (status: number, error: unknown = null) => new HttpErrorResponse({ status, error });

describe('describeApiError', () => {
  it('une los mensajes de validación de `errors`', () => {
    const err = httpError(400, {
      detail: 'La petición no es válida',
      errors: { text: 'El texto debe tener entre 10 y 2000 caracteres' },
    });
    expect(describeApiError(err)).toBe('El texto debe tener entre 10 y 2000 caracteres');
  });

  it('usa `detail` cuando no hay `errors`', () => {
    const err = httpError(404, { title: 'Not Found', detail: 'No existe la reseña con id 9999' });
    expect(describeApiError(err)).toBe('No existe la reseña con id 9999');
  });

  it('avisa de que no hay conexión cuando el estado es 0', () => {
    expect(describeApiError(httpError(0))).toContain('No se pudo conectar');
  });

  it('indica el código en un 5xx sin cuerpo útil, como el que devuelve el proxy', () => {
    expect(describeApiError(httpError(504, 'Gateway Timeout'))).toContain('504');
  });

  it('no falla con errores que no son HTTP', () => {
    expect(describeApiError(new Error('boom'))).toBe('Ocurrió un error inesperado.');
  });
});
