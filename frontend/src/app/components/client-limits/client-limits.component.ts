import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { AdminService } from '../../services/admin.service';
import { ClientLimitRequest, ClientLimit } from '../../models/client.model';

@Component({
  selector: 'app-client-limits',
  templateUrl: './client-limits.component.html',
  styleUrls: ['./client-limits.component.css']
})
export class ClientLimitsComponent implements OnInit {
  limitsForm: FormGroup;
  loading = false;
  loadingLimits = false;
  clientId: number | null = null;

  constructor(
    private fb: FormBuilder,
    private adminService: AdminService,
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar
  ) {
    this.limitsForm = this.fb.group({
      windowSizeSeconds: [60, [Validators.required, Validators.min(1)]],
      maxRequestsPerWindow: [100, [Validators.required, Validators.min(1)]],
      monthlyQuota: [10000, [Validators.required, Validators.min(1)]],
      softThrottleThresholdPercent: [80, [Validators.min(0), Validators.max(100)]],
      hardRejectThresholdPercent: [100, [Validators.min(0), Validators.max(100)]]
    });
  }

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      const id = params['id'];
      if (id) {
        this.clientId = +id;
        this.loadExistingLimits();
      }
    });
  }

  loadExistingLimits(): void {
    if (this.clientId) {
      this.loadingLimits = true;
      this.adminService.getClientLimit(this.clientId).subscribe({
        next: (limit: ClientLimit) => {
          this.limitsForm.patchValue({
            windowSizeSeconds: limit.windowSizeSeconds,
            maxRequestsPerWindow: limit.maxRequestsPerWindow,
            monthlyQuota: limit.monthlyQuota,
            softThrottleThresholdPercent: limit.softThrottleThreshold ? (limit.softThrottleThreshold * 100) : 80,
            hardRejectThresholdPercent: limit.hardRejectThreshold ? (limit.hardRejectThreshold * 100) : 100
          });
          this.loadingLimits = false;
        },
        error: (error) => {
          // If limit doesn't exist, use defaults (already set in form)
          if (error.status === 404) {
            console.log('No existing limits found, using defaults');
          } else {
            console.error('Error loading limits:', error);
            this.snackBar.open('Error loading limits', 'Close', { duration: 3000 });
          }
          this.loadingLimits = false;
        }
      });
    }
  }

  onSubmit(): void {
    if (this.limitsForm.valid && this.clientId) {
      this.loading = true;
      const formValue = this.limitsForm.value;
      const request: ClientLimitRequest = {
        windowSizeSeconds: formValue.windowSizeSeconds,
        maxRequestsPerWindow: formValue.maxRequestsPerWindow,
        monthlyQuota: formValue.monthlyQuota,
        softThrottleThreshold: (formValue.softThrottleThresholdPercent ?? 80) / 100,
        hardRejectThreshold: (formValue.hardRejectThresholdPercent ?? 100) / 100
      };

      this.adminService.updateClientLimits(this.clientId, request).subscribe({
        next: () => {
          this.snackBar.open('Limits updated successfully', 'Close', { duration: 3000 });
          this.router.navigate(['/admin']);
          this.loading = false;
        },
        error: (error) => {
          console.error('Error updating limits:', error);
          const errorMessage = error.error?.message || 'Error updating limits';
          this.snackBar.open(errorMessage, 'Close', { duration: 3000 });
          this.loading = false;
        }
      });
    }
  }

  onCancel(): void {
    this.router.navigate(['/admin']);
  }

  getErrorMessage(fieldName: string): string {
    const field = this.limitsForm.get(fieldName);
    if (field?.hasError('required')) {
      return `${fieldName} is required`;
    }
    if (field?.hasError('min')) {
      return `${fieldName} must be at least ${field.errors?.['min'].min}`;
    }
    if (field?.hasError('max')) {
      return `${fieldName} must not exceed ${field.errors?.['max'].max}`;
    }
    return '';
  }
}

