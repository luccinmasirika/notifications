import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminService } from '../../services/admin.service';
import { SystemLimit } from '../../models/client.model';

@Component({
  selector: 'app-system-limits',
  templateUrl: './system-limits.component.html',
  styleUrls: ['./system-limits.component.css']
})
export class SystemLimitsComponent implements OnInit {
  systemLimits: SystemLimit[] = [];
  displayedColumns: string[] = ['name', 'windowSizeSeconds', 'maxRequestsPerWindow', 'active', 'createdAt', 'actions'];
  loading = false;

  constructor(
    private adminService: AdminService,
    private router: Router,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadSystemLimits();
  }

  loadSystemLimits(): void {
    this.loading = true;
    this.adminService.getSystemLimits().subscribe({
      next: (limits) => {
        this.systemLimits = limits;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading system limits:', error);
        this.snackBar.open('Error loading system limits', 'Close', { duration: 3000 });
        this.loading = false;
      }
    });
  }

  openAddLimitPage(): void {
    this.router.navigate(['/settings/new']);
  }

  openEditLimitPage(limit: SystemLimit): void {
    // Encode the limit name for the URL
    const encodedName = encodeURIComponent(limit.name);
    this.router.navigate(['/settings', encodedName, 'edit']);
  }

  formatDate(dateString?: string): string {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString();
  }

  formatWindowSize(seconds: number): string {
    if (seconds < 60) {
      return `${seconds}s`;
    } else if (seconds < 3600) {
      return `${Math.floor(seconds / 60)}min`;
    } else if (seconds < 86400) {
      return `${Math.floor(seconds / 3600)}h`;
    } else {
      return `${Math.floor(seconds / 86400)}d`;
    }
  }

  formatRequestsPerWindow(requests: number): string {
    if (requests >= 1000000) {
      return `${(requests / 1000000).toFixed(1)}M`;
    } else if (requests >= 1000) {
      return `${(requests / 1000).toFixed(1)}K`;
    }
    return requests.toString();
  }

  formatLimitName(name: string): string {
    if (!name) return '';
    // Replace underscores with spaces and capitalize first letter of each word
    return name
      .split('_')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }
}

