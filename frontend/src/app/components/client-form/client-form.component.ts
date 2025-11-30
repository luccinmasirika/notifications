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
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AdminService } from '../../services/admin.service';
import { Client, CreateClientRequest, UpdateClientRequest } from '../../models/client.model';

@Component({
  selector: 'app-client-form',
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
    MatProgressSpinnerModule,
    MatTooltipModule,
    TranslateModule
  ],
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
    private snackBar: MatSnackBar,
    private translate: TranslateService
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
          this.snackBar.open(this.translate.instant('client.errorLoadingClient'), this.translate.instant('common.close'), { duration: 3000 });
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
            this.snackBar.open(this.translate.instant('client.clientUpdated'), this.translate.instant('common.close'), { duration: 3000 });
            this.router.navigate(['/admin']);
            this.loading = false;
          },
          error: (error) => {
            console.error('Error updating client:', error);
            this.snackBar.open(this.translate.instant('client.errorSavingClient'), this.translate.instant('common.close'), { duration: 3000 });
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
            this.snackBar.open(this.translate.instant('client.clientCreated'), this.translate.instant('common.close'), { duration: 3000 });
            this.router.navigate(['/admin']);
            this.loading = false;
          },
          error: (error) => {
            console.error('Error creating client:', error);
            const errorMessage = error.error?.message || this.translate.instant('client.errorSavingClient');
            this.snackBar.open(errorMessage, this.translate.instant('common.close'), { duration: 3000 });
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
    this.snackBar.open(this.translate.instant('client.apiKeyGenerated'), this.translate.instant('common.close'), { duration: 2000 });
  }
  getErrorMessage(fieldName: string): string {
    const field = this.clientForm.get(fieldName);
    if (field?.hasError('required')) {
      return this.translate.instant('errors.required');
    }
    if (field?.hasError('minlength')) {
      if (fieldName === 'name') {
        return this.translate.instant('client.nameMinLength');
      }
      if (fieldName === 'apiKey') {
        return this.translate.instant('client.apiKeyMinLength');
      }
      return `${fieldName} ${this.translate.instant('client.nameTooShort')}`;
    }
    if (field?.hasError('maxlength')) {
      return `${fieldName} ${this.translate.instant('client.nameTooLong')}`;
    }
    if (field?.hasError('min')) {
      if (fieldName === 'priority') {
        return this.translate.instant('client.priorityMin');
      }
      return `${fieldName} ${this.translate.instant('common.min', { min: field.errors?.['min'].min })}`;
    }
    return '';
  }
}
