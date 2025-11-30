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
import { AdminService } from '../../services/admin.service';
import { ClientDetailsResponse } from '../../models/client.model';

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
    MatProgressBarModule
  ],
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
  formatDate(dateString?: string | null): string {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString();
  }
  getStatusIcon(status: any): string {
    if (status.isBlocked) return 'block';
    if (status.isSoftThrottled) return 'warning';
    return 'check_circle';
  }
  Math = Math; 
}
