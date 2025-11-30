import { Component, OnInit } from '@angular/core';
import { Router, NavigationEnd, ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { AdminService } from '../../services/admin.service';
import { filter } from 'rxjs/operators';
interface BreadcrumbItem {
  label: string;
  route?: string;
}
@Component({
  selector: 'app-dashboard-layout',
  templateUrl: './dashboard-layout.component.html',
  styleUrls: ['./dashboard-layout.component.css']
})
export class DashboardLayoutComponent implements OnInit {
  currentRoute = '';
  sidebarOpen = true;
  breadcrumbs: BreadcrumbItem[] = [];
  clientName: string | null = null;
  menuItems = [
    { path: 'admin', icon: 'dashboard', labelKey: 'navigation.overview' },
    { path: 'client-test', icon: 'speed', labelKey: 'navigation.testing' },
    { path: 'settings', icon: 'settings', labelKey: 'navigation.settings' }
  ];
  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService,
    private adminService: AdminService,
    public translate: TranslateService
  ) {}
  ngOnInit(): void {
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe((event: any) => {
        this.currentRoute = event.url;
        this.updateBreadcrumbs();
      });
    this.currentRoute = this.router.url;
    this.updateBreadcrumbs();
  }
  updateBreadcrumbs(): void {
    const tempBreadcrumbs: BreadcrumbItem[] = [];
    this.clientName = null;
    tempBreadcrumbs.push({ label: this.translate.instant('navigation.overview'), route: '/admin' });
    const urlParts = this.currentRoute.split('/').filter(part => part);
    if (urlParts.length === 0) {
      this.breadcrumbs = tempBreadcrumbs;
      return;
    }
    if (urlParts[0] === 'admin' && urlParts.length === 1) {
      this.breadcrumbs = tempBreadcrumbs;
      return;
    }
    if (urlParts[0] === 'client-test') {
      tempBreadcrumbs.push({ label: this.translate.instant('navigation.testing') });
      this.breadcrumbs = tempBreadcrumbs;
      return;
    }
    if (urlParts[0] === 'settings') {
      tempBreadcrumbs.push({ label: this.translate.instant('navigation.settings'), route: '/settings' });
      if (urlParts.length === 1) {
        this.breadcrumbs = tempBreadcrumbs;
        return;
      }
      if (urlParts[1] === 'new') {
        tempBreadcrumbs.push({ label: this.translate.instant('breadcrumbs.newSystemLimit') });
        this.breadcrumbs = tempBreadcrumbs;
        return;
      }
      if (urlParts.length === 3 && urlParts[2] === 'edit') {
        const limitName = decodeURIComponent(urlParts[1]);
        const formattedName = limitName
          .split('_')
          .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
          .join(' ');
        tempBreadcrumbs.push({ label: formattedName });
        tempBreadcrumbs.push({ label: 'Edit' });
        this.breadcrumbs = tempBreadcrumbs;
        return;
      }
      this.breadcrumbs = tempBreadcrumbs;
      return;
    }
    if (urlParts[0] === 'clients') {
      if (urlParts[1] === 'new') {
        tempBreadcrumbs.push({ label: this.translate.instant('client.newClient') });
        this.breadcrumbs = tempBreadcrumbs;
        return;
      }
      const clientId = urlParts[1];
      if (clientId && !isNaN(+clientId)) {
        if (urlParts.length === 2) {
          tempBreadcrumbs.push({ 
            label: this.translate.instant('client.details'),
            route: `/clients/${clientId}`
          });
        } else if (urlParts[2] === 'edit') {
          tempBreadcrumbs.push({ 
            label: this.clientName || this.translate.instant('client.name'),
            route: `/clients/${clientId}`
          });
          tempBreadcrumbs.push({ label: this.translate.instant('breadcrumbs.edit') });
        } else if (urlParts[2] === 'limits') {
          tempBreadcrumbs.push({ 
            label: this.clientName || this.translate.instant('client.name'),
            route: `/clients/${clientId}`
          });
          tempBreadcrumbs.push({ label: this.translate.instant('clientLimits.rateLimits') });
        }
        this.breadcrumbs = tempBreadcrumbs;
        this.loadClientName(+clientId);
      } else {
        this.breadcrumbs = tempBreadcrumbs;
      }
    } else {
      this.breadcrumbs = tempBreadcrumbs;
    }
  }
  loadClientName(clientId: number): void {
    this.adminService.getClient(clientId).subscribe({
      next: (client) => {
        this.clientName = client.name;
        const urlParts = this.currentRoute.split('/').filter(part => part);
        if (urlParts[0] === 'clients' && urlParts[1] === clientId.toString()) {
          const newBreadcrumbs: BreadcrumbItem[] = [
            { label: this.translate.instant('navigation.overview'), route: '/admin' }
          ];
          if (urlParts.length === 2) {
            newBreadcrumbs.push({ 
              label: this.clientName,
              route: `/clients/${clientId}`
            });
          } else if (urlParts[2] === 'edit') {
            newBreadcrumbs.push({ 
              label: this.clientName,
              route: `/clients/${clientId}`
            });
            newBreadcrumbs.push({ label: this.translate.instant('breadcrumbs.edit') });
          } else if (urlParts[2] === 'limits') {
            newBreadcrumbs.push({ 
              label: this.clientName,
              route: `/clients/${clientId}`
            });
            newBreadcrumbs.push({ label: this.translate.instant('clientLimits.rateLimits') });
          }
          this.breadcrumbs = newBreadcrumbs;
        }
      },
      error: (error) => {
        console.error('Error loading client name:', error);
      }
    });
  }
  navigateBreadcrumb(item: BreadcrumbItem): void {
    if (item.route) {
      this.router.navigate([item.route]);
    }
  }
  isActiveRoute(path: string): boolean {
    const fullPath = `/${path}`;
    return this.currentRoute === fullPath || this.currentRoute.endsWith(`/${path}`) || 
           (path === 'admin' && (this.currentRoute === '/admin' || this.currentRoute.startsWith('/admin'))) ||
           (path === 'settings' && this.currentRoute.startsWith('/settings'));
  }
  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
