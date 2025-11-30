import { Routes } from '@angular/router';
import { AuthGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./components/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
    loadComponent: () => import('./components/dashboard-layout/dashboard-layout.component').then(m => m.DashboardLayoutComponent),
    canActivate: [AuthGuard],
    children: [
      {
        path: 'admin',
        loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'client-test',
        loadComponent: () => import('./components/client-tester/client-tester.component').then(m => m.ClientTesterComponent)
      },
      {
        path: 'clients/new',
        loadComponent: () => import('./components/client-form/client-form.component').then(m => m.ClientFormComponent)
      },
      {
        path: 'clients/:id/edit',
        loadComponent: () => import('./components/client-form/client-form.component').then(m => m.ClientFormComponent)
      },
      {
        path: 'clients/:id/limits',
        loadComponent: () => import('./components/client-limits/client-limits.component').then(m => m.ClientLimitsComponent)
      },
      {
        path: 'clients/:id',
        loadComponent: () => import('./components/client-details/client-details.component').then(m => m.ClientDetailsComponent)
      },
      {
        path: 'settings',
        loadComponent: () => import('./components/system-limits/system-limits.component').then(m => m.SystemLimitsComponent)
      },
      {
        path: 'settings/new',
        loadComponent: () => import('./components/system-limit-form/system-limit-form.component').then(m => m.SystemLimitFormComponent)
      },
      {
        path: 'settings/:name/edit',
        loadComponent: () => import('./components/system-limit-form/system-limit-form.component').then(m => m.SystemLimitFormComponent)
      },
      {
        path: '',
        redirectTo: 'admin',
        pathMatch: 'full'
      }
    ]
  },
  {
    path: '**',
    redirectTo: '/admin'
  }
];

