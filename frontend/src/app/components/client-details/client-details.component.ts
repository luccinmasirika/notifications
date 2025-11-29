import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminService } from '../../services/admin.service';
import { ClientDetailsResponse } from '../../models/client.model';

@Component({
  selector: 'app-client-details',
  templateUrl: './client-details.component.html',
  styleUrls: ['./client-details.component.css']
})
export class ClientDetailsComponent implements OnInit {
  details: ClientDetailsResponse | null = null;
  loading = false;
  clientId: number | null = null;

  constructor(
    private adminService: AdminService,
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
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

  onTest(): void {
    if (this.details?.client.apiKey) {
      // Navigate to test page with API key as query parameter
      this.router.navigate(['/client-test'], {
        queryParams: { apiKey: this.details.client.apiKey }
      });
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

  Math = Math; // Expose Math to template
}

