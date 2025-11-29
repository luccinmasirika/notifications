import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminService } from '../../services/admin.service';
import { SystemLimit, SystemLimitRequest } from '../../models/client.model';

@Component({
  selector: 'app-system-limit-dialog',
  templateUrl: './system-limit-dialog.component.html',
  styleUrls: ['./system-limit-dialog.component.css']
})
export class SystemLimitDialogComponent implements OnInit {
  limitForm: FormGroup;
  isEditMode = false;
  loading = false;

  constructor(
    private fb: FormBuilder,
    private adminService: AdminService,
    private dialogRef: MatDialogRef<SystemLimitDialogComponent>,
    private snackBar: MatSnackBar,
    @Inject(MAT_DIALOG_DATA) public data: { limit: SystemLimit | null }
  ) {
    this.isEditMode = !!data.limit;
    this.limitForm = this.fb.group({
      name: ['', [
        Validators.required, 
        Validators.minLength(1), 
        Validators.maxLength(255),
        // Pattern must match backend: only letters, numbers, and underscores
        Validators.pattern(/^[a-zA-Z0-9_]+$/)
      ]],
      windowSizeSeconds: [60, [Validators.required, Validators.min(1)]],
      maxRequestsPerWindow: [1000, [Validators.required, Validators.min(1)]],
      active: [true]
    });
  }

  ngOnInit(): void {
    if (this.isEditMode && this.data.limit) {
      this.limitForm.patchValue({
        name: this.data.limit.name,
        windowSizeSeconds: this.data.limit.windowSizeSeconds,
        maxRequestsPerWindow: this.data.limit.maxRequestsPerWindow,
        active: this.data.limit.active
      });
    }
  }

  onSubmit(): void {
    if (this.limitForm.valid) {
      this.loading = true;
      const formValue = this.limitForm.value;

      // Build request - backend finds by name, but include ID if editing for clarity
      const request: SystemLimit = {
        name: formValue.name,
        windowSizeSeconds: formValue.windowSizeSeconds,
        maxRequestsPerWindow: formValue.maxRequestsPerWindow,
        active: formValue.active
      };

      // Include ID if in edit mode (backend will find by name, but ID ensures correct update)
      if (this.isEditMode && this.data.limit?.id) {
        request.id = this.data.limit.id;
      }

      this.adminService.createOrUpdateSystemLimit(request).subscribe({
        next: () => {
          const message = this.isEditMode ? 'System limit updated successfully' : 'System limit created successfully';
          this.snackBar.open(message, 'Close', { duration: 3000 });
          this.dialogRef.close(true);
          this.loading = false;
        },
        error: (error) => {
          console.error('Error saving system limit:', error);
          const errorMessage = error.error?.message || 'Error saving system limit';
          this.snackBar.open(errorMessage, 'Close', { duration: 3000 });
          this.loading = false;
        }
      });
    }
  }

  onCancel(): void {
    this.dialogRef.close(false);
  }

  getErrorMessage(fieldName: string): string {
    const field = this.limitForm.get(fieldName);
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
    if (field?.hasError('pattern')) {
      return `${fieldName} must contain only letters, numbers, and underscores (format: limit_123)`;
    }
    return '';
  }

  formatWindowHint(seconds: number): string {
    if (seconds < 60) {
      return `${seconds} seconds`;
    } else if (seconds < 3600) {
      return `${Math.floor(seconds / 60)} minutes`;
    } else if (seconds < 86400) {
      return `${Math.floor(seconds / 3600)} hours`;
    } else {
      return `${Math.floor(seconds / 86400)} days`;
    }
  }
}

