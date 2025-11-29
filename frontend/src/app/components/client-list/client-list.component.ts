import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminService } from '../../services/admin.service';
import { Client } from '../../models/client.model';
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
    private router: Router,
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
  formatDate(dateString?: string): string {
    if (!dateString) return '-';
    return new Date(dateString).toLocaleString();
  }
}
