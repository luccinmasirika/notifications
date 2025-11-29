import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
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
    private snackBar: MatSnackBar
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
    this.adminService.getClients().subscribe({
      next: (clients) => {
        this.clients = clients;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading clients:', error);
        this.snackBar.open('Error loading clients', 'Close', { duration: 3000 });
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
    this.stats = [
      {
        label: 'Total Clients',
        value: stats.totalClients,
        subtitle: 'All clients'
      },
      {
        label: 'Active',
        value: stats.activeClients,
        subtitle: `${stats.totalClients > 0 ? Math.round((stats.activeClients / stats.totalClients) * 100) : 0}% of total`
      },
      {
        label: 'With Limits',
        value: stats.clientLimits,
        subtitle: 'Configured'
      },
      {
        label: 'System Limits',
        value: stats.systemLimits,
        subtitle: 'Active rules'
      }
    ];
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

  copyApiKey(apiKey: string): void {
    navigator.clipboard.writeText(apiKey).then(() => {
      this.snackBar.open('API key copied to clipboard', 'Close', { duration: 2000 });
    }).catch(() => {
      this.snackBar.open('Failed to copy API key', 'Close', { duration: 2000 });
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
