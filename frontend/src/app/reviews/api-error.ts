import { HttpErrorResponse } from '@angular/common/http';

/**
 * Convierte un error de HttpClient en un mensaje para el usuario.
 * El backend responde con ProblemDetail (docs/backend-api.md): `detail` y, en validación, `errors`.
 */
export function describeApiError(err: unknown): string {
  if (!(err instanceof HttpErrorResponse)) {
    return 'Ocurrió un error inesperado.';
  }
  if (err.status === 0) {
    return 'No se pudo conectar con el backend. Comprueba que está encendido.';
  }

  const body: unknown = err.error;
  if (body && typeof body === 'object') {
    const { errors, detail } = body as { errors?: unknown; detail?: unknown };
    if (errors && typeof errors === 'object') {
      const messages = Object.values(errors).filter((m): m is string => typeof m === 'string');
      if (messages.length > 0) return messages.join(' ');
    }
    if (typeof detail === 'string' && detail.length > 0) return detail;
  }

  if (err.status >= 500) {
    return `El backend no responde o falló (código ${err.status}).`;
  }
  return `La petición falló (código ${err.status}).`;
}
