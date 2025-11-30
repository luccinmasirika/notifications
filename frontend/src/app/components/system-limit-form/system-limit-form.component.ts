import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AdminService } from '../../services/admin.service';
import { SystemLimit, SystemLimitRequest } from '../../models/client.model';

@Component({
  selector: 'app-system-limit-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatCheckboxModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './system-limit-form.component.html',
  styleUrls: ['./system-limit-form.component.css']
})
export class SystemLimitFormComponent implements OnInit {
  limitForm: FormGroup;
  isEditMode = false;
  loading = false;
  loadingLimit = false;
  limitName: string | null = null;
  constructor(
    private fb: FormBuilder,
    private adminService: AdminService,
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar
  ) {
    this.limitForm = this.fb.group({
      name: ['', [
        Validators.required, 
        Validators.minLength(1), 
        Validators.maxLength(255),
        Validators.pattern(/^[a-zA-Z0-9_]+$/)
      ]],
      windowSizeSeconds: [60, [Validators.required, Validators.min(1)]],
      maxRequestsPerWindow: [1000, [Validators.required, Validators.min(1)]],
      active: [true]
    });
  }
  ngOnInit(): void {
    this.route.params.subscribe(params => {
      const name = params['name'];
      if (name) {
        this.isEditMode = true;
        this.limitName = decodeURIComponent(name);
        this.loadLimit();
      }
    });
  }
  loadLimit(): void {
    if (this.limitName) {
      this.loadingLimit = true;
      this.adminService.getSystemLimits().subscribe({
        next: (limits) => {
          const limit = limits.find(l => l.name === this.limitName);
          if (limit) {
            this.limitForm.patchValue({
              name: limit.name,
              windowSizeSeconds: limit.windowSizeSeconds,
              maxRequestsPerWindow: limit.maxRequestsPerWindow,
              active: limit.active
            });
            this.limitForm.get('name')?.disable();
          } else {
            this.snackBar.open('System limit not found', 'Close', { duration: 3000 });
            this.router.navigate(['/settings']);
          }
          this.loadingLimit = false;
        },
        error: (error) => {
          console.error('Error loading system limit:', error);
          this.snackBar.open('Error loading system limit', 'Close', { duration: 3000 });
          this.loadingLimit = false;
        }
      });
    }
  }
  onSubmit(): void {
    if (this.limitForm.valid) {
      this.loading = true;
      const formValue = this.limitForm.value;
      const rawValue = this.limitForm.getRawValue();
      const requestName = rawValue.name || this.limitName || '';
      const request: SystemLimit = {
        name: requestName,
        windowSizeSeconds: formValue.windowSizeSeconds,
        maxRequestsPerWindow: formValue.maxRequestsPerWindow,
        active: formValue.active
      };
      if (this.isEditMode && this.limitName) {
        this.adminService.getSystemLimits().subscribe({
          next: (limits) => {
            const existingLimit = limits.find(l => l.name === this.limitName);
            if (existingLimit?.id) {
              request.id = existingLimit.id;
            }
            this.saveLimit(request);
          },
          error: () => {
            this.saveLimit(request);
          }
        });
      } else {
        this.saveLimit(request);
      }
    }
  }
  private saveLimit(request: SystemLimit): void {
    this.adminService.createOrUpdateSystemLimit(request).subscribe({
      next: () => {
        const message = this.isEditMode ? 'System limit updated successfully' : 'System limit created successfully';
        this.snackBar.open(message, 'Close', { duration: 3000 });
        this.router.navigate(['/settings']);
        this.loading = false;
      },
      error: (error) => {
        console.error('Error saving system limit:', error);
        let errorMessage = 'Error saving system limit';
        if (error.error?.errors && Array.isArray(error.error.errors) && error.error.errors.length > 0) {
          errorMessage = error.error.errors[0].message || errorMessage;
        } else if (error.error?.message) {
          errorMessage = error.error.message;
        }
        this.snackBar.open(errorMessage, 'Close', { duration: 5000 });
        this.loading = false;
      }
    });
  }
  onCancel(): void {
    this.router.navigate(['/settings']);
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
