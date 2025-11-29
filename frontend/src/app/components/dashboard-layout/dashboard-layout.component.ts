import { Component, OnInit } from '@angular/core';
import { Router, NavigationEnd, ActivatedRoute } from '@angular/router';
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
    { path: 'admin', icon: 'dashboard', label: 'Overview' },
    { path: 'client-test', icon: 'speed', label: 'Testing' },
    { path: 'settings', icon: 'settings', label: 'Settings' }
  ];

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private authService: AuthService,
    private adminService: AdminService
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

    // Always start with Overview
    tempBreadcrumbs.push({ label: 'Overview', route: '/admin' });

    const urlParts = this.currentRoute.split('/').filter(part => part);
    
    if (urlParts.length === 0) {
      this.breadcrumbs = tempBreadcrumbs;
      return;
    }

    // Handle /admin route
    if (urlParts[0] === 'admin' && urlParts.length === 1) {
      this.breadcrumbs = tempBreadcrumbs;
      return;
    }

    // Handle /client-test route
    if (urlParts[0] === 'client-test') {
      tempBreadcrumbs.push({ label: 'Testing' });
      this.breadcrumbs = tempBreadcrumbs;
      return;
    }

    // Handle /settings route
    if (urlParts[0] === 'settings') {
      tempBreadcrumbs.push({ label: 'Settings', route: '/settings' });
      
      if (urlParts.length === 1) {
        // Just /settings
        this.breadcrumbs = tempBreadcrumbs;
        return;
      }
      
      if (urlParts[1] === 'new') {
        // /settings/new
        tempBreadcrumbs.push({ label: 'New System Limit' });
        this.breadcrumbs = tempBreadcrumbs;
        return;
      }
      
      if (urlParts.length === 3 && urlParts[2] === 'edit') {
        // /settings/:name/edit
        const limitName = decodeURIComponent(urlParts[1]);
        // Format the name for display (replace underscores with spaces and capitalize)
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

    // Handle client routes
    if (urlParts[0] === 'clients') {
      if (urlParts[1] === 'new') {
        tempBreadcrumbs.push({ label: 'New Client' });
        this.breadcrumbs = tempBreadcrumbs;
        return;
      }

      // For routes with client ID, we need to fetch the client name
      const clientId = urlParts[1];
      if (clientId && !isNaN(+clientId)) {
        // Show breadcrumbs immediately with placeholder
        if (urlParts.length === 2) {
          // /clients/:id - Details
          tempBreadcrumbs.push({ 
            label: 'Client Details',
            route: `/clients/${clientId}`
          });
        } else if (urlParts[2] === 'edit') {
          // /clients/:id/edit
          tempBreadcrumbs.push({ 
            label: 'Client',
            route: `/clients/${clientId}`
          });
          tempBreadcrumbs.push({ label: 'Edit' });
        } else if (urlParts[2] === 'limits') {
          // /clients/:id/limits
          tempBreadcrumbs.push({ 
            label: 'Client',
            route: `/clients/${clientId}`
          });
          tempBreadcrumbs.push({ label: 'Rate Limits' });
        }
        
        this.breadcrumbs = tempBreadcrumbs;
        
        // Load client name and update breadcrumbs
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
        // Update breadcrumbs with client name
        const urlParts = this.currentRoute.split('/').filter(part => part);
        if (urlParts[0] === 'clients' && urlParts[1] === clientId.toString()) {
          const newBreadcrumbs: BreadcrumbItem[] = [
            { label: 'Overview', route: '/admin' }
          ];
          
          if (urlParts.length === 2) {
            // /clients/:id - Details
            newBreadcrumbs.push({ 
              label: this.clientName,
              route: `/clients/${clientId}`
            });
          } else if (urlParts[2] === 'edit') {
            // /clients/:id/edit
            newBreadcrumbs.push({ 
              label: this.clientName,
              route: `/clients/${clientId}`
            });
            newBreadcrumbs.push({ label: 'Edit' });
          } else if (urlParts[2] === 'limits') {
            // /clients/:id/limits
            newBreadcrumbs.push({ 
              label: this.clientName,
              route: `/clients/${clientId}`
            });
            newBreadcrumbs.push({ label: 'Rate Limits' });
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
