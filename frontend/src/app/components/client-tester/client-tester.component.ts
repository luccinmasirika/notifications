import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription, interval, Subject } from 'rxjs';
import { takeUntil, finalize } from 'rxjs/operators';
import { MatTableDataSource } from '@angular/material/table';
import { ClientTesterService, TestResponseData } from '../../services/client-tester.service';
import { NotificationRequest } from '../../models/notification.model';

@Component({
  selector: 'app-client-tester',
  templateUrl: './client-tester.component.html',
  styleUrls: ['./client-tester.component.scss']
})
export class ClientTesterComponent implements OnInit, OnDestroy {
  testForm: FormGroup;
  isRunning = false;
  private destroy$ = new Subject<void>();
  private subscription?: Subscription;

  // Statistics
  totalSent = 0;
  accepted = 0;
  rejected = 0;
  softThrottles = 0;
  consecutive429 = 0;
  
  // Current rate limit info (from last response)
  currentLimit?: number;
  currentRemaining?: number;
  currentUsagePercent?: number;

  // Response history (last 50)
  responses: TestResponseData[] = [];
  dataSource = new MatTableDataSource<TestResponseData>([]);
  readonly maxResponses = 50;

  constructor(
    private fb: FormBuilder,
    private testerService: ClientTesterService,
    private cdr: ChangeDetectorRef
  ) {
    this.testForm = this.fb.group({
      apiKey: ['', [Validators.required]],
      channel: ['SMS', [Validators.required]],
      destination: ['', [Validators.required]],
      message: ['', [Validators.required]],
      totalRequests: [50, [Validators.required, Validators.min(1), Validators.max(500)]],
      intervalMs: [200, [Validators.required]]
    });
  }

  ngOnInit(): void {
    console.log('ClientTesterComponent initialized');
    // Pre-fill with example values
    this.testForm.patchValue({
      apiKey: 'test-api-key-123',
      destination: '+250700000001',
      message: 'Test notification message'
    });
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
        this.sendRequest(requestNumber, formValue.apiKey, request);
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

  private sendRequest(
    requestNumber: number,
    apiKey: string,
    request: NotificationRequest
  ): void {
    this.testerService.sendNotification(apiKey, request).subscribe({
      next: (response) => {
        this.totalSent++;
        const status = response.status;
        const timestamp = new Date();

        const rateLimitHeaders = this.testerService.extractRateLimitHeaders(response);
        const usagePercent = this.testerService.calculateUsagePercent(rateLimitHeaders);
        
        // Check for soft throttling from header or calculated usage
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

        // Update current rate limit info
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

        // Extract headers and body from HttpErrorResponse
        if (error.headers || error.error) {
          rateLimitHeaders = this.testerService.extractRateLimitHeaders(error);
          usagePercent = this.testerService.calculateUsagePercent(rateLimitHeaders, error.error);
          
          // Check for soft throttling from header or calculated usage
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

        // Update current rate limit info
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
  }

  private addResponse(response: TestResponseData): void {
    this.responses.unshift(response);
    if (this.responses.length > this.maxResponses) {
      this.responses = this.responses.slice(0, this.maxResponses);
    }
    // Update the MatTableDataSource to trigger change detection
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

