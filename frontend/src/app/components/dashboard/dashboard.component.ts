import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AdminService } from '../../services/admin.service';
import { Client } from '../../models/client.model';

interface SystemStatus {
  status: string;
  statistics: {
    totalClients: number;
    activeClients: number;
    clientLimits: number;
    systemLimits: number;
  };
}

interface StatCard {
  label: string;
  value: string | number;
  subtitle?: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatMenuModule,
    MatTooltipModule,
    TranslateModule
  ],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  clients: Client[] = [];
  loading = false;
  systemStatus: SystemStatus | null = null;
  loadingStatus = false;
  stats: StatCard[] = [];
  constructor(
    private adminService: AdminService,
    private router: Router,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) {}
  ngOnInit(): void {
    this.loadDashboardData();
  }
  loadDashboardData(): void {
    this.loadClients();
    this.loadSystemStatus();
  }
  loadClients(): void {
    this.loading = true;
    this.adminService.getClients(0, 100).subscribe({
      next: (response) => {
        this.clients = response.content;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading clients:', error);
        this.translate.get('dashboard.errorLoadingClients').subscribe((msg) => {
          this.snackBar.open(msg, this.translate.instant('common.close'), { duration: 3000 });
        });
        this.loading = false;
      }
    });
  }
  loadSystemStatus(): void {
    this.loadingStatus = true;
    this.adminService.getSystemStatus().subscribe({
      next: (status) => {
        this.systemStatus = status;
        this.updateStats();
        this.loadingStatus = false;
      },
      error: (error) => {
        console.error('Error loading system status:', error);
        this.loadingStatus = false;
        this.updateStats();
      }
    });
  }
  updateStats(): void {
    const stats = this.getStatistics();
    this.translate.get([
      'dashboard.totalClients',
      'dashboard.activeClients',
      'dashboard.totalRequests',
      'systemLimits.title'
    ]).subscribe(translations => {
      this.stats = [
        {
          label: translations['dashboard.totalClients'],
          value: stats.totalClients,
          subtitle: this.translate.instant('dashboard.totalClients')
        },
        {
          label: translations['dashboard.activeClients'],
          value: stats.activeClients,
          subtitle: `${stats.totalClients > 0 ? Math.round((stats.activeClients / stats.totalClients) * 100) : 0}% ${this.translate.instant('dashboard.ofTotal')}`
        },
        {
          label: this.translate.instant('dashboard.withLimits'),
          value: stats.clientLimits,
          subtitle: this.translate.instant('dashboard.configured')
        },
        {
          label: translations['systemLimits.title'],
          value: stats.systemLimits,
          subtitle: this.translate.instant('dashboard.activeRules')
        }
      ];
    });
  }
  openAddClientDialog(): void {
    this.router.navigate(['/clients/new']);
  }
  openEditClientDialog(client: Client): void {
    if (client.id) {
      this.router.navigate(['/clients', client.id, 'edit']);
    }
  }
  openLimitsDialog(client: Client): void {
    if (client.id) {
      this.router.navigate(['/clients', client.id, 'limits']);
    }
  }
  openDetailsDialog(client: Client): void {
    if (client.id) {
      this.router.navigate(['/clients', client.id]);
    }
  }
  copyApiKey(apiKey: string | undefined): void {
    if (!apiKey) {
      return;
    }
    navigator.clipboard.writeText(apiKey).then(() => {
      this.translate.get('client.apiKeyCopied').subscribe((msg) => {
        this.snackBar.open(msg, this.translate.instant('common.close'), { duration: 2000 });
      });
    }).catch(() => {
      this.translate.get('client.apiKeyCopyFailed').subscribe((msg) => {
        this.snackBar.open(msg, this.translate.instant('common.close'), { duration: 2000 });
      });
    });
  }
  formatDate(dateString?: string): string {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', { 
      year: 'numeric', 
      month: 'short', 
      day: 'numeric' 
    });
  }
  getStatistics() {
    return this.systemStatus?.statistics || {
      totalClients: 0,
      activeClients: 0,
      clientLimits: 0,
      systemLimits: 0
    };
  }
}
