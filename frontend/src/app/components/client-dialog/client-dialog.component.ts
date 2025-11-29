import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminService } from '../../services/admin.service';
import { Client, CreateClientRequest, UpdateClientRequest } from '../../models/client.model';

@Component({
  selector: 'app-client-dialog',
  templateUrl: './client-dialog.component.html',
  styleUrls: ['./client-dialog.component.css']
})
export class ClientDialogComponent implements OnInit {
  clientForm: FormGroup;
  isEditMode = false;
  loading = false;

  constructor(
    private fb: FormBuilder,
    private adminService: AdminService,
    private dialogRef: MatDialogRef<ClientDialogComponent>,
    private snackBar: MatSnackBar,
    @Inject(MAT_DIALOG_DATA) public data: { client: Client | null }
  ) {
    this.isEditMode = !!data.client;
    this.clientForm = this.fb.group({
      apiKey: ['', [Validators.required, Validators.minLength(16), Validators.maxLength(255)]],
      name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(255)]],
      priority: [0, [Validators.required, Validators.min(0)]],
      active: [true]
    });
  }

  ngOnInit(): void {
    if (this.isEditMode && this.data.client) {
      this.clientForm.patchValue({
        apiKey: this.data.client.apiKey,
        name: this.data.client.name,
        priority: this.data.client.priority,
        active: this.data.client.active
      });
    }
  }

  onSubmit(): void {
    if (this.clientForm.valid) {
      this.loading = true;
      const formValue = this.clientForm.value;

      if (this.isEditMode && this.data.client?.id) {
        const updateRequest: UpdateClientRequest = {
          apiKey: formValue.apiKey,
          name: formValue.name,
          priority: formValue.priority,
          active: formValue.active
        };

        this.adminService.updateClient(this.data.client.id, updateRequest).subscribe({
          next: () => {
            this.snackBar.open('Client updated successfully', 'Close', { duration: 3000 });
            this.dialogRef.close(true);
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
            this.dialogRef.close(true);
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
    this.dialogRef.close(false);
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

