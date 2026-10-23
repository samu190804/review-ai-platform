# frontend

Aplicación web de la plataforma (Angular 22 + Tailwind CSS 4). Escucha en http://localhost:4200 y solo habla con el backend.

Tiene tres pantallas:

- **Analizar** (`/analizar`): se escribe el texto de una reseña y se muestra su resultado.
- **Historial** (`/historial`): reseñas ya analizadas, con filtro por estado y paginación.
- **Estadísticas** (`/estadisticas`): el total de reseñas, la confianza media del modelo, un anillo con el estado de las reseñas y barras con las puntuaciones de las aprobadas. Cada gráfica trae su texto alternativo y hay una tabla con los mismos datos.

La API que consume está en [../docs/backend-api.md](../docs/backend-api.md).

## Encender

Requisitos: Node.js y el backend encendido en http://localhost:8080 (ver su [README](../backend/README.md)). El `ai-service` es opcional: sin él las reseñas nuevas quedan `PENDING`.

Desde `frontend/`:

```powershell
npm install   # solo la primera vez
npm start
```

| Comando | Qué hace |
|---|---|
| `npm start` | Servidor de desarrollo con recarga y proxy a la API |
| `npx ng test --watch=false` | Ejecuta los tests una vez |
| `npm run build` | Compila para producción en `dist/` |

## Proxy a la API

El código llama a rutas relativas (`/api/reviews`). En desarrollo, `ng serve` reenvía `/api` a `http://localhost:8080` ([proxy.conf.json](proxy.conf.json)), así que no hace falta CORS en Spring. El proxy no existe en una compilación de producción: allí tendrá que haber un servidor delante que reenvíe `/api` (fase 5, con Docker Compose).

## Estructura

```
src/app/
  pages/analyze/   Pantalla Analizar
  pages/history/   Pantalla Historial
  pages/stats/     Pantalla Estadísticas: stats-charts.ts (datos y configuración de las gráficas,
                   funciones puras) y chart-canvas.ts (envuelve Chart.js)
  reviews/         Modelos, ReviewService (único sitio que conoce las URLs),
                   api-error.ts (errores HTTP a mensajes) y el componente ReviewResult
```

Sin librería de UI ni gestor de estado: Tailwind en las plantillas y servicios con *signals*. La única librería añadida es [Chart.js](https://www.chartjs.org/), para las gráficas: solo se registran las piezas que se usan y viaja en el trozo que se carga al abrir Estadísticas, no en el paquete inicial.

## Estados de una reseña

`ReviewResult` enseña solo lo que cada estado tiene:

| Estado | Qué se muestra |
|---|---|
| `APPROVED` | Estrellas y confianza |
| `REJECTED` | Motivos de moderación, sin estrellas |
| `PENDING` | Aviso de que se reintentará solo |

`PENDING` no es un error: el backend responde 201 y la reseña ya está guardada. Cuando se resuelve, en el historial hay que pulsar **Actualizar** para verla cambiar.

## En Docker

El `Dockerfile` compila Angular con Node 24 y sirve el resultado con nginx (`nginx.conf`), sin Node en la imagen final. nginx hace tres cosas: reenvía `/api` al backend (lo mismo que `proxy.conf.json` en desarrollo), devuelve `index.html` para cualquier ruta que no sea un archivo (así funcionan `/historial` o `/estadisticas` al recargar) y guarda un año en caché los archivos con hash en el nombre, pero no `index.html`. Se levanta con el resto del sistema: `docker compose up -d --build` desde la raíz (ver el [README raíz](../README.md)).

## Tests

No necesitan backend ni Docker: las peticiones se interceptan con `HttpTestingController`. Hay uno por cada pieza (servicio, errores, `ReviewResult`, las tres pantallas y la cabecera). jsdom no tiene canvas, así que los tests de las gráficas no las dibujan: `ChartCanvas` recibe su fábrica por un token (`CHART_FACTORY`), los tests inyectan una falsa y comprueban qué configuración se le pide.

## Cosas que conviene saber

- **Si un cambio no aparece en el navegador,** reinicia `npm start` antes de sospechar del código: el servidor de desarrollo puede quedarse con una compilación antigua.
- **El texto se valida sin espacios en los extremos,** y eso es lo que se envía. Un texto de hasta 2000 en pantalla no supera el límite del backend ni el del `ai-service`. El caso contrario no está cubierto: un texto de solo emojis puede pasar el mínimo de 10 en JavaScript y quedarse por debajo en Python (ver "Ideas pendientes" del README raíz).
- **Cambiar de filtro cancela la petición anterior,** para que una respuesta lenta no pise a la nueva.
