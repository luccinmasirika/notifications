import { Component, Inject, OnInit } from '@angular/core';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminService } from '../../services/admin.service';
import { Client, ClientDetailsResponse } from '../../models/client.model';

@Component({
  selector: 'app-client-details-dialog',
  templateUrl: './client-details-dialog.component.html',
  styleUrls: ['./client-details-dialog.component.css']
})
export class ClientDetailsDialogComponent implements OnInit {
  details: ClientDetailsResponse | null = null;
  loading = false;

  constructor(
    private adminService: AdminService,
    private dialogRef: MatDialogRef<ClientDetailsDialogComponent>,
    private snackBar: MatSnackBar,
    @Inject(MAT_DIALOG_DATA) public data: { client: Client }
  ) {}

  ngOnInit(): void {
    this.loadClientDetails();
  }

  loadClientDetails(): void {
    if (this.data.client.id) {
      this.loading = true;
      this.adminService.getClientDetails(this.data.client.id).subscribe({
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

  onClose(): void {
    this.dialogRef.close();
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

