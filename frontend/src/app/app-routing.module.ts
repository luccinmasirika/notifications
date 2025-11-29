import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LoginComponent } from './components/login/login.component';
import { ClientListComponent } from './components/client-list/client-list.component';
import { ClientTesterComponent } from './components/client-tester/client-tester.component';
import { DashboardLayoutComponent } from './components/dashboard-layout/dashboard-layout.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { ClientFormComponent } from './components/client-form/client-form.component';
import { ClientLimitsComponent } from './components/client-limits/client-limits.component';
import { ClientDetailsComponent } from './components/client-details/client-details.component';
import { SystemLimitsComponent } from './components/system-limits/system-limits.component';
import { SystemLimitFormComponent } from './components/system-limit-form/system-limit-form.component';
import { AuthGuard } from './guards/auth.guard';
const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: DashboardLayoutComponent,
    canActivate: [AuthGuard],
    children: [
      { path: 'admin', component: DashboardComponent },
      { path: 'client-test', component: ClientTesterComponent },
      { path: 'clients/new', component: ClientFormComponent },
      { path: 'clients/:id/edit', component: ClientFormComponent },
      { path: 'clients/:id/limits', component: ClientLimitsComponent },
      { path: 'clients/:id', component: ClientDetailsComponent },
      { path: 'settings', component: SystemLimitsComponent },
      { path: 'settings/new', component: SystemLimitFormComponent },
      { path: 'settings/:name/edit', component: SystemLimitFormComponent },
      { path: '', redirectTo: 'admin', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: '/admin' }
];
@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
