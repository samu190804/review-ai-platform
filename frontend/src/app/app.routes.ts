import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'analizar' },
  {
    path: 'analizar',
    title: 'Analizar reseña · Review AI',
    loadComponent: () => import('./pages/analyze/analyze-page').then((m) => m.AnalyzePage),
  },
  {
    path: 'historial',
    title: 'Historial · Review AI',
    loadComponent: () => import('./pages/history/history-page').then((m) => m.HistoryPage),
  },
  {
    path: 'estadisticas',
    title: 'Estadísticas · Review AI',
    loadComponent: () => import('./pages/stats/stats-page').then((m) => m.StatsPage),
  },
  { path: '**', redirectTo: 'analizar' },
];
