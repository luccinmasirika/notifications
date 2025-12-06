import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AdminService } from '../../services/admin.service';
import { ClientDetailsResponse, ApiSecretResponse } from '../../models/client.model';

@Component({
  selector: 'app-client-details',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatProgressBarModule,
    MatTooltipModule
  ],
  templateUrl: './client-details.component.html',
  styleUrls: ['./client-details.component.css']
})
export class ClientDetailsComponent implements OnInit {
  details: ClientDetailsResponse | null = null;
  loading = false;
  clientId: number | null = null;
  newCredentials: { apiKey: string; apiSecret: string } | null = null;
  
  constructor(
    private adminService: AdminService,
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar
  ) {}
  
  ngOnInit(): void {
    const navigation = this.router.getCurrentNavigation();
    if (navigation?.extras?.state?.['credentials']) {
      this.newCredentials = navigation.extras.state['credentials'];
    }
    
    this.route.params.subscribe(params => {
      const id = params['id'];
      if (id) {
        this.clientId = +id;
        this.loadClientDetails();
      }
    });
  }
  loadClientDetails(): void {
    if (this.clientId) {
      this.loading = true;
      this.adminService.getClientDetails(this.clientId).subscribe({
        next: (details) => {
          this.details = details;
          this.loading = false;
        },
        error: (error) => {
          console.error('Error loading client details:', error);
          this.snackBar.open('Error loading client details', 'Close', { duration: 3000 });
          this.loading = false;
        }
      });
    }
  }
  onBack(): void {
    this.router.navigate(['/admin']);
  }
  onEdit(): void {
    if (this.clientId) {
      this.router.navigate(['/clients', this.clientId, 'edit']);
    }
  }
  onEditLimits(): void {
    if (this.clientId) {
      this.router.navigate(['/clients', this.clientId, 'limits']);
    }
  }
  formatDate(dateString?: string | null): string {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString();
  }
  getStatusIcon(status: any): string {
    if (status.isBlocked) return 'block';
    if (status.isSoftThrottled) return 'warning';
    return 'check_circle';
  }

  getAuthMethodLabel(authMethod?: string): string {
    return 'HMAC-SHA256';
  }

  getStatusLabel(status?: string): string {
    return status || 'ACTIVE';
  }

  getStatusColor(status?: string): string {
    switch (status) {
      case 'ACTIVE': return 'primary';
      case 'SUSPENDED': return 'warn';
      case 'REVOKED': return 'warn';
      default: return 'primary';
    }
  }

  rotatedSecret: string | null = null;
  regeneratedApiKey: string | null = null;

  onRotateSecret(): void {
    if (!this.clientId || !confirm('Rotate API secret? The old secret will no longer work. Make sure to update your client applications.')) {
      return;
    }

    this.loading = true;
    this.adminService.rotateApiSecret(this.clientId).subscribe({
      next: (response: ApiSecretResponse) => {
        this.loading = false;
        this.rotatedSecret = response.apiSecret;
        this.snackBar.open('Secret rotated. Save it below - it will not be shown again.', 'Close', { duration: 5000 });
        this.loadClientDetails();
      },
      error: (error) => {
        console.error('Error rotating API secret:', error);
        this.snackBar.open('Error rotating API secret: ' + (error.error?.error || error.message), 'Close', { duration: 5000 });
        this.loading = false;
      }
    });
  }

  copyToClipboard(text: string, label: string): void {
    navigator.clipboard.writeText(text).then(() => {
      this.snackBar.open(`${label} copied to clipboard`, 'Close', { duration: 2000 });
    });
  }

  onCloseRotatedSecret(): void {
    this.rotatedSecret = null;
  }

  onRegenerateApiKey(): void {
    if (!this.clientId || !confirm('Regenerate API key? The old API key will no longer work. Make sure to update your client applications.')) {
      return;
    }

    this.loading = true;
    this.adminService.regenerateApiKey(this.clientId).subscribe({
      next: (response) => {
        this.loading = false;
        if (response.apiKey) {
          this.regeneratedApiKey = response.apiKey;
          this.snackBar.open('API key regenerated. Save it below - it will not be shown again.', 'Close', { duration: 5000 });
          this.loadClientDetails();
        }
      },
      error: (error) => {
        console.error('Error regenerating API key:', error);
        this.snackBar.open('Error regenerating API key: ' + (error.error?.error || error.message), 'Close', { duration: 5000 });
        this.loading = false;
      }
    });
  }

  onCloseRegeneratedApiKey(): void {
    this.regeneratedApiKey = null;
  }

  onUpdateStatus(newStatus: 'ACTIVE' | 'SUSPENDED' | 'REVOKED'): void {
    if (!this.clientId) return;

    const statusLabels: Record<string, string> = {
      'ACTIVE': 'activate',
      'SUSPENDED': 'suspend',
      'REVOKED': 'revoke'
    };

    if (!confirm(`Are you sure you want to ${statusLabels[newStatus]} this client?`)) {
      return;
    }

    this.loading = true;
    this.adminService.updateClientStatus(this.clientId, newStatus).subscribe({
      next: () => {
        this.loading = false;
        this.snackBar.open(`Client status updated to ${newStatus}`, 'Close', { duration: 3000 });
        this.loadClientDetails();
      },
      error: (error) => {
        console.error('Error updating client status:', error);
        this.snackBar.open('Error updating status: ' + (error.error?.error || error.message), 'Close', { duration: 5000 });
        this.loading = false;
      }
    });
  }


  Math = Math; 
}
