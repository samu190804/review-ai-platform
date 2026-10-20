import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'analizar' },
  {
    path: 'analizar',
    title: 'Analizar reseña · Review AI',
    loadComponent: () => import('./pages/analyze/analyze-page').then((m) => m.AnalyzePage),
  },
  { path: '**', redirectTo: 'analizar' },
];
