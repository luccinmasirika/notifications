import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminService } from '../../services/admin.service';
import { Client, ClientLimit } from '../../models/client.model';
import { ClientDialogComponent } from '../client-dialog/client-dialog.component';
import { ClientLimitsDialogComponent } from '../client-limits-dialog/client-limits-dialog.component';
import { ClientDetailsDialogComponent } from '../client-details-dialog/client-details-dialog.component';

@Component({
  selector: 'app-client-list',
  templateUrl: './client-list.component.html',
  styleUrls: ['./client-list.component.css']
})
export class ClientListComponent implements OnInit {
  clients: Client[] = [];
  displayedColumns: string[] = ['id', 'name', 'apiKey', 'priority', 'active', 'createdAt', 'actions'];
  loading = false;

  constructor(
    private adminService: AdminService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadClients();
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

  openAddClientDialog(): void {
    const dialogRef = this.dialog.open(ClientDialogComponent, {
      width: '500px',
      data: { client: null }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.loadClients();
      }
    });
  }

  openEditClientDialog(client: Client): void {
    const dialogRef = this.dialog.open(ClientDialogComponent, {
      width: '500px',
      data: { client: { ...client } }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.loadClients();
      }
    });
  }

  openLimitsDialog(client: Client): void {
    const dialogRef = this.dialog.open(ClientLimitsDialogComponent, {
      width: '500px',
      data: { clientId: client.id! }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.snackBar.open('Limits updated successfully', 'Close', { duration: 3000 });
      }
    });
  }

  openDetailsDialog(client: Client): void {
    this.dialog.open(ClientDetailsDialogComponent, {
      width: '600px',
      data: { client: client }
    });
  }

  formatDate(dateString?: string): string {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString();
  }
}

