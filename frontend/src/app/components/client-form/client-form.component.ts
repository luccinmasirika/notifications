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
import { Client, CreateClientRequest, UpdateClientRequest, ClientResponse } from '../../models/client.model';

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
  createdCredentials: { apiKey: string; apiSecret: string | null } | null = null; // Credentials après création
  showCredentials = false; // Afficher les credentials sur la page
  constructor(
    private fb: FormBuilder,
    private adminService: AdminService,
    private router: Router,
    private route: ActivatedRoute,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) {
    // API key is generated automatically on backend, no need for form field in create mode
    this.clientForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(1), Validators.maxLength(255)]],
      priority: [0, [Validators.required, Validators.min(0)]],
      active: [true],
      // API key only needed in edit mode if user wants to change it
      apiKey: ['']
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
              apiKey: client.apiKey || '', // API key not returned by backend after V8 (hashed)
              name: client.name,
              priority: client.priority,
              active: client.active
            });
            // Make API key field optional when editing (since we don't have the original)
            if (!client.apiKey) {
              this.clientForm.get('apiKey')?.clearValidators();
              this.clientForm.get('apiKey')?.updateValueAndValidity();
            }
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
          apiKey: formValue.apiKey || undefined, // Only send if user provided a new API key
          name: formValue.name,
          priority: formValue.priority,
          active: formValue.active
        };
        this.adminService.updateClient(this.clientId, updateRequest).subscribe({
          next: (response: ClientResponse) => {
            if (response.apiKey) {
              // API key was updated - display on page
              this.createdCredentials = {
                apiKey: response.apiKey,
                apiSecret: null // Secret not returned on update
              };
              this.showCredentials = true;
              this.loading = false;
            } else {
              // No API key change
              this.snackBar.open(this.translate.instant('client.clientUpdated'), this.translate.instant('common.close'), { duration: 3000 });
              this.router.navigate(['/admin']);
              this.loading = false;
            }
          },
          error: (error) => {
            console.error('Error updating client:', error);
            this.snackBar.open(this.translate.instant('client.errorSavingClient'), this.translate.instant('common.close'), { duration: 3000 });
            this.loading = false;
          }
        });
      } else {
        // API key will be auto-generated on backend if not provided
        const createRequest: CreateClientRequest = {
          // No apiKey - backend will generate it automatically
          name: formValue.name,
          priority: formValue.priority,
          active: formValue.active
        };
        this.adminService.createClient(createRequest).subscribe({
          next: (response: ClientResponse) => {
            // Display API key and secret directly on page
            if (response.apiKey) {
              const apiSecret = response.apiSecret || null;
              this.createdCredentials = {
                apiKey: response.apiKey,
                apiSecret: apiSecret
              };
              this.showCredentials = true;
              this.loading = false;
              
              if (apiSecret) {
                this.snackBar.open(
                  this.getTranslation('client.clientCreatedWithCredentials', 'Client created. Save both API Key and Secret below.'),
                  this.getTranslation('common.close', 'Close'),
                  { duration: 5000 }
                );
              } else {
                this.snackBar.open(
                  this.getTranslation('client.clientCreated', 'Client created successfully'),
                  this.getTranslation('common.close', 'Close'),
                  { duration: 3000 }
                );
              }
            } else {
              this.snackBar.open(this.translate.instant('client.clientCreated'), this.translate.instant('common.close'), { duration: 3000 });
              this.router.navigate(['/admin']);
              this.loading = false;
            }
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
    this.loading = true;
    this.adminService.generateApiKey().subscribe({
      next: (response) => {
        this.clientForm.patchValue({ apiKey: response.apiKey });
        this.snackBar.open(this.translate.instant('client.apiKeyGenerated'), this.translate.instant('common.close'), { duration: 2000 });
        this.loading = false;
      },
      error: (error) => {
        console.error('Error generating API key:', error);
        const errorMessage = error.error?.message || this.translate.instant('client.errorGeneratingApiKey') || 'Error generating API key';
        this.snackBar.open(errorMessage, this.translate.instant('common.close'), { duration: 3000 });
        this.loading = false;
      }
    });
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

  getTranslation(key: string, defaultValue: string): string {
    const translation = this.translate.instant(key);
    return translation !== key ? translation : defaultValue;
  }

  copyToClipboard(text: string, label?: string): void {
    navigator.clipboard.writeText(text).then(() => {
      const message = label 
        ? `${label} ${this.getTranslation('common.copied', 'copied to clipboard')}`
        : this.getTranslation('common.copied', 'Copied to clipboard');
      this.snackBar.open(
        message,
        this.getTranslation('common.close', 'Close'),
        { duration: 2000 }
      );
    });
  }

  onContinue(): void {
    this.router.navigate(['/admin']);
  }
}
