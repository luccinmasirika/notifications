import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminService } from '../../services/admin.service';
import { Client, CreateClientRequest, UpdateClientRequest } from '../../models/client.model';
@Component({
  selector: 'app-client-form',
  templateUrl: './client-form.component.html',
  styleUrls: ['./client-form.component.css']
})
export class ClientFormComponent implements OnInit {
  clientForm: FormGroup;
  isEditMode = false;
  loading = false;
  loadingClient = false;
  clientId: number | null = null;
  constructor(
    private fb: FormBuilder,
    private adminService: AdminService,
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar
  ) {
    this.clientForm = this.fb.group({
      apiKey: ['', [Validators.required, Validators.minLength(16), Validators.maxLength(255)]],
      name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(255)]],
      priority: [0, [Validators.required, Validators.min(0)]],
      active: [true]
    });
  }
  ngOnInit(): void {
    this.route.params.subscribe(params => {
      const id = params['id'];
      if (id) {
        this.isEditMode = true;
        this.clientId = +id;
        this.loadClient();
      }
    });
  }
  loadClient(): void {
    if (this.clientId) {
      this.loadingClient = true;
      this.adminService.getClients().subscribe({
        next: (clients) => {
          const client = clients.find(c => c.id === this.clientId);
          if (client) {
            this.clientForm.patchValue({
              apiKey: client.apiKey,
              name: client.name,
              priority: client.priority,
              active: client.active
            });
          }
          this.loadingClient = false;
        },
        error: (error) => {
          console.error('Error loading client:', error);
          this.snackBar.open('Error loading client', 'Close', { duration: 3000 });
          this.loadingClient = false;
        }
      });
    }
  }
  onSubmit(): void {
    if (this.clientForm.valid) {
      this.loading = true;
      const formValue = this.clientForm.value;
      if (this.isEditMode && this.clientId) {
        const updateRequest: UpdateClientRequest = {
          apiKey: formValue.apiKey,
          name: formValue.name,
          priority: formValue.priority,
          active: formValue.active
        };
        this.adminService.updateClient(this.clientId, updateRequest).subscribe({
          next: () => {
            this.snackBar.open('Client updated successfully', 'Close', { duration: 3000 });
            this.router.navigate(['/admin']);
            this.loading = false;
          },
          error: (error) => {
            console.error('Error updating client:', error);
            this.snackBar.open('Error updating client', 'Close', { duration: 3000 });
            this.loading = false;
          }
        });
      } else {
        const createRequest: CreateClientRequest = {
          apiKey: formValue.apiKey,
          name: formValue.name,
          priority: formValue.priority,
          active: formValue.active
        };
        this.adminService.createClient(createRequest).subscribe({
          next: () => {
            this.snackBar.open('Client created successfully', 'Close', { duration: 3000 });
            this.router.navigate(['/admin']);
            this.loading = false;
          },
          error: (error) => {
            console.error('Error creating client:', error);
            const errorMessage = error.error?.message || 'Error creating client';
            this.snackBar.open(errorMessage, 'Close', { duration: 3000 });
            this.loading = false;
          }
        });
      }
    }
  }
  onCancel(): void {
    this.router.navigate(['/admin']);
  }
  generateApiKey(): void {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    let apiKey = 'LM'; 
    const segments = [
      { length: 3 }, 
      { length: 6 }, 
      { length: 6 },
      { length: 6 },
    ];
    for (let segIndex = 0; segIndex < segments.length; segIndex++) {
      const seg = segments[segIndex];
      if (segIndex > 0) {
        apiKey += '-';
      }
      const segmentArray = new Uint8Array(seg.length);
      crypto.getRandomValues(segmentArray);
      for (let i = 0; i < seg.length; i++) {
        apiKey += chars[segmentArray[i] % chars.length];
      }
    }
    this.clientForm.patchValue({ apiKey });
    this.snackBar.open('API key generated successfully', 'Close', { duration: 2000 });
  }
  getErrorMessage(fieldName: string): string {
    const field = this.clientForm.get(fieldName);
    if (field?.hasError('required')) {
      return `${fieldName} is required`;
    }
    if (field?.hasError('minlength')) {
      return `${fieldName} is too short`;
    }
    if (field?.hasError('maxlength')) {
      return `${fieldName} is too long`;
    }
    if (field?.hasError('min')) {
      return `${fieldName} must be at least ${field.errors?.['min'].min}`;
    }
    return '';
  }
}
