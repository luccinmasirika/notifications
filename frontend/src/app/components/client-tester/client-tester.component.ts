import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription, interval, Subject } from 'rxjs';
import { takeUntil, finalize } from 'rxjs/operators';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatChipsModule } from '@angular/material/chips';
import { ClientTesterService, TestResponseData } from '../../services/client-tester.service';
import { HmacService } from '../../services/hmac.service';
import { NotificationRequest } from '../../models/notification.model';

@Component({
  selector: 'app-client-tester',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    MatChipsModule
  ],
  templateUrl: './client-tester.component.html',
  styleUrls: ['./client-tester.component.scss']
})
export class ClientTesterComponent implements OnInit, OnDestroy {
  testForm: FormGroup;
  isRunning = false;
  private destroy$ = new Subject<void>();
  private subscription?: Subscription;
  totalSent = 0;
  accepted = 0;
  rejected = 0;
  softThrottles = 0;
  consecutive429 = 0;
  currentLimit?: number;
  currentRemaining?: number;
  currentUsagePercent?: number;
  responses: TestResponseData[] = [];
  dataSource = new MatTableDataSource<TestResponseData>([]);
  readonly maxResponses = 50;
  constructor(
    private fb: FormBuilder,
    private testerService: ClientTesterService,
    private hmacService: HmacService,
    private cdr: ChangeDetectorRef,
    private route: ActivatedRoute
  ) {
    this.testForm = this.fb.group({
      apiKey: ['', [Validators.required]],
      apiSecret: ['', [Validators.required]],
      channel: ['SMS', [Validators.required]],
      destination: ['', [Validators.required]],
      message: ['', [Validators.required]],
      totalRequests: [50, [Validators.required, Validators.min(1), Validators.max(500)]],
      intervalMs: [200, [Validators.required]]
    });
  }
  ngOnInit(): void {
    console.log('ClientTesterComponent initialized');
    this.route.queryParams.subscribe(params => {
      const apiKey = params['apiKey'];
      const apiSecret = params['apiSecret'];
      
      if (apiKey) {
        this.testForm.patchValue({
          apiKey: apiKey,
          apiSecret: apiSecret || '',
          destination: '+250700000001',
          message: 'Test notification message'
        });
      } else {
        this.testForm.patchValue({
          apiKey: 'test-api-key-123',
          apiSecret: '',
          destination: '+250700000001',
          message: 'Test notification message'
        });
      }
      
      if (apiSecret) {
        setTimeout(() => this.generateDefaultSignature(), 100);
      }
    });
    
    this.testForm.get('apiSecret')?.valueChanges.subscribe(apiSecret => {
      if (apiSecret) {
        this.generateDefaultSignature();
      }
    });
    
    this.testForm.get('channel')?.valueChanges.subscribe(() => {
      if (this.testForm.get('apiSecret')?.value) {
        this.generateDefaultSignature();
      }
    });
    this.testForm.get('destination')?.valueChanges.subscribe(() => {
      if (this.testForm.get('apiSecret')?.value) {
        this.generateDefaultSignature();
      }
    });
    this.testForm.get('message')?.valueChanges.subscribe(() => {
      if (this.testForm.get('apiSecret')?.value) {
        this.generateDefaultSignature();
      }
    });
  }

  async generateDefaultSignature(): Promise<void> {
    const apiSecret = this.testForm.get('apiSecret')?.value;
    if (!apiSecret) {
      return;
    }

    try {
      const timestamp = this.hmacService.getCurrentTimestamp();
      const method = 'POST';
      const path = '/api/notifications';
      const body = JSON.stringify({
        channel: this.testForm.get('channel')?.value || 'SMS',
        to: this.testForm.get('destination')?.value || '',
        message: this.testForm.get('message')?.value || ''
      });

      const signature = await this.hmacService.generateSignatureAsync(
        apiSecret,
        timestamp,
        method,
        path,
        body
      );

      console.log('Default signature generated:', {
        timestamp,
        signature: signature.substring(0, 20) + '...',
        payload: `${timestamp}\n${method}\n${path}\n${body}`
      });
    } catch (error) {
      console.error('Error generating default signature:', error);
    }
  }
  ngOnDestroy(): void {
    this.stop();
    this.destroy$.next();
    this.destroy$.complete();
  }
  start(): void {
    if (this.testForm.invalid) {
      return;
    }
    const formValue = this.testForm.value;
    this.isRunning = true;
    this.resetStats();
    const request: NotificationRequest = {
      channel: formValue.channel,
      to: formValue.destination,
      message: formValue.message
    };
    const totalRequests = formValue.totalRequests;
    const intervalMs = formValue.intervalMs;
    let requestNumber = 0;
    this.subscription = interval(intervalMs)
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => {
          this.isRunning = false;
        })
      )
      .subscribe(() => {
        if (requestNumber >= totalRequests) {
          this.stop();
          return;
        }
        if (this.consecutive429 >= 5) {
          this.stop();
          return;
        }
        requestNumber++;
        this.sendRequestAsync(requestNumber, formValue.apiKey, formValue.apiSecret, request);
      });
  }
  stop(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
      this.subscription = undefined;
    }
    this.isRunning = false;
    this.destroy$.next();
  }
  private async sendRequestAsync(
    requestNumber: number,
    apiKey: string,
    apiSecret: string,
    request: NotificationRequest
  ): Promise<void> {
    try {
      const observable = await this.testerService.sendNotification(apiKey, apiSecret, request);
      observable.subscribe({
      next: (response) => {
        this.totalSent++;
        const status = response.status;
        const timestamp = new Date();
        const rateLimitHeaders = this.testerService.extractRateLimitHeaders(response);
        const usagePercent = this.testerService.calculateUsagePercent(rateLimitHeaders);
        const isSoftThrottled = rateLimitHeaders.softThrottled !== undefined 
          ? rateLimitHeaders.softThrottled 
          : (usagePercent !== undefined && usagePercent >= 80 && usagePercent < 100);
        if (isSoftThrottled) {
          this.softThrottles++;
        }
        if (status === 202) {
          this.accepted++;
          this.consecutive429 = 0;
        } else if (status === 429) {
          this.rejected++;
          this.consecutive429++;
        } else {
          this.consecutive429 = 0;
        }
        if (rateLimitHeaders.limit !== undefined) {
          this.currentLimit = rateLimitHeaders.limit;
        }
        if (rateLimitHeaders.remaining !== undefined) {
          this.currentRemaining = rateLimitHeaders.remaining;
        }
        if (usagePercent !== undefined) {
          this.currentUsagePercent = usagePercent;
        }
        const testResponse: TestResponseData = {
          requestNumber,
          httpStatus: status,
          usagePercent,
          limit: rateLimitHeaders.limit,
          remaining: rateLimitHeaders.remaining,
          retryAfter: rateLimitHeaders.retryAfter,
          isSoftThrottled,
          timestamp,
          rateLimitHeaders
        };
        this.addResponse(testResponse);
      },
      error: (error: HttpErrorResponse) => {
        this.totalSent++;
        const status = error.status || 0;
        const timestamp = new Date();
        let rateLimitHeaders;
        let usagePercent;
        let isSoftThrottled = false;
        if (error.headers || error.error) {
          rateLimitHeaders = this.testerService.extractRateLimitHeaders(error);
          usagePercent = this.testerService.calculateUsagePercent(rateLimitHeaders, error.error);
          isSoftThrottled = rateLimitHeaders?.softThrottled !== undefined 
            ? rateLimitHeaders.softThrottled 
            : (usagePercent !== undefined && usagePercent >= 80 && usagePercent < 100);
        }
        if (isSoftThrottled) {
          this.softThrottles++;
        }
        if (status === 429) {
          this.rejected++;
          this.consecutive429++;
        } else {
          this.consecutive429 = 0;
        }
        if (rateLimitHeaders?.limit !== undefined) {
          this.currentLimit = rateLimitHeaders.limit;
        }
        if (rateLimitHeaders?.remaining !== undefined) {
          this.currentRemaining = rateLimitHeaders.remaining;
        }
        if (usagePercent !== undefined) {
          this.currentUsagePercent = usagePercent;
        }
        const testResponse: TestResponseData = {
          requestNumber,
          httpStatus: status,
          usagePercent,
          limit: rateLimitHeaders?.limit,
          remaining: rateLimitHeaders?.remaining,
          retryAfter: rateLimitHeaders?.retryAfter,
          isSoftThrottled,
          timestamp,
          rateLimitHeaders
        };
        this.addResponse(testResponse);
      }
      });
    } catch (error) {
      console.error('Error sending request:', error);
      this.totalSent++;
      const testResponse: TestResponseData = {
        requestNumber,
        httpStatus: 0,
        timestamp: new Date()
      };
      this.addResponse(testResponse);
    }
  }
  private addResponse(response: TestResponseData): void {
    this.responses.unshift(response);
    if (this.responses.length > this.maxResponses) {
      this.responses = this.responses.slice(0, this.maxResponses);
    }
    this.dataSource.data = [...this.responses];
  }
  private resetStats(): void {
    this.totalSent = 0;
    this.accepted = 0;
    this.rejected = 0;
    this.softThrottles = 0;
    this.consecutive429 = 0;
    this.responses = [];
    this.dataSource.data = [];
    this.currentLimit = undefined;
    this.currentRemaining = undefined;
    this.currentUsagePercent = undefined;
  }
  getProgress(): number {
    const totalRequests = this.testForm.get('totalRequests')?.value || 50;
    if (totalRequests === 0) return 0;
    return Math.min(100, (this.totalSent / totalRequests) * 100);
  }
  getErrorMessage(fieldName: string): string {
    const field = this.testForm.get(fieldName);
    if (field?.hasError('required')) {
      return `${fieldName} is required`;
    }
    if (field?.hasError('min')) {
      return `${fieldName} must be at least ${field.errors?.['min'].min}`;
    }
    if (field?.hasError('max')) {
      return `${fieldName} must be at most ${field.errors?.['max'].max}`;
    }
    return '';
  }
  getStatusClass(status: number): string {
    if (status === 202) return 'status-202';
    if (status === 429) return 'status-429';
    if (status === 0) return 'status-0';
    if (status >= 400) return 'status-error';
    if (status >= 300) return 'status-other';
    return '';
  }
  formatTimestamp(timestamp: Date): string {
    return timestamp.toLocaleTimeString() + '.' + timestamp.getMilliseconds().toString().padStart(3, '0');
  }
}
