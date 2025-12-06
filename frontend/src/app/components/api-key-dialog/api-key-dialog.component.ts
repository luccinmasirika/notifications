import { Component, Inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Clipboard } from '@angular/cdk/clipboard';

@Component({
  selector: 'app-api-key-dialog',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatTooltipModule,
    TranslateModule
  ],
  template: `
    <h2 mat-dialog-title>
      <mat-icon>vpn_key</mat-icon>
      {{ getTranslation('client.apiKeyGenerated', 'API Key Generated') }}
    </h2>
    <mat-dialog-content>
      <div class="api-key-container">
        <div class="warning-banner">
          <mat-icon class="warning-icon">warning</mat-icon>
          <div class="warning-content">
            <strong>{{ getTranslation('client.criticalWarning', 'CRITICAL: Save this API key now!') }}</strong>
            <p>{{ getTranslation('client.apiKeyInfo', 'This is the ONLY time this API key will be displayed. It cannot be recovered if lost.') }}</p>
          </div>
        </div>

        <div class="api-key-section">
          <label class="api-key-label">
            {{ getTranslation('client.apiKey', 'API Key') }}
            <button mat-icon-button 
                    (click)="toggleVisibility()" 
                    [matTooltip]="getTranslation(isVisible ? 'client.hide' : 'client.show', isVisible ? 'Hide' : 'Show')"
                    class="visibility-toggle">
              <mat-icon>{{ isVisible ? 'visibility_off' : 'visibility' }}</mat-icon>
            </button>
          </label>
          <div class="api-key-display" [class.copied]="copied">
            <code class="api-key-code" [class.masked]="!isVisible">
              {{ isVisible ? data.apiKey : '•'.repeat(data.apiKey.length) }}
            </code>
            <button mat-icon-button 
                    (click)="copyToClipboard()" 
                    [matTooltip]="getTranslation('common.copy', 'Copy')"
                    [class.copied]="copied">
              <mat-icon>{{ copied ? 'check_circle' : 'content_copy' }}</mat-icon>
            </button>
          </div>
          <p class="copy-status" *ngIf="copied">
            <mat-icon>check_circle</mat-icon>
            {{ getTranslation('common.copied', 'Copied to clipboard!') }}
          </p>
        </div>

        <div class="info-box">
          <mat-icon>info</mat-icon>
          <p>{{ getTranslation('client.apiKeyUsage', 'Use this key in the Authorization header: Authorization: Bearer YOUR_API_KEY') }}</p>
        </div>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="downloadAsFile()" [matTooltip]="getTranslation('client.downloadKey', 'Download API key')">
        <mat-icon>download</mat-icon>
        {{ getTranslation('client.download', 'Download') }}
      </button>
      <button mat-button (click)="copyToClipboard()">
        <mat-icon>{{ copied ? 'check_circle' : 'content_copy' }}</mat-icon>
        {{ copied ? getTranslation('common.copied', 'Copied') : getTranslation('common.copy', 'Copy') }}
      </button>
      <button mat-raised-button color="primary" (click)="onClose()">
        {{ getTranslation('common.close', 'Close') }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .api-key-container {
      padding: 16px 0;
      min-width: 500px;
    }
    
    .warning-banner {
      background: #fff3cd;
      border: 2px solid #ffc107;
      border-radius: 8px;
      padding: 16px;
      margin-bottom: 24px;
      display: flex;
      gap: 12px;
      align-items: flex-start;
    }
    .warning-icon {
      color: #f57c00;
      flex-shrink: 0;
    }
    .warning-content {
      flex: 1;
    }
    .warning-content strong {
      color: #d32f2f;
      display: block;
      margin-bottom: 8px;
      font-size: 16px;
    }
    .warning-content p {
      color: #666;
      margin: 0;
      font-size: 14px;
    }

    .api-key-section {
      margin-bottom: 24px;
    }
    .api-key-label {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-weight: 500;
      margin-bottom: 8px;
      color: #333;
    }
    .visibility-toggle {
      margin-left: auto;
    }
    .api-key-display {
      display: flex;
      align-items: center;
      gap: 8px;
      background: #f5f5f5;
      padding: 16px;
      border-radius: 8px;
      border: 2px solid #ddd;
      transition: all 0.3s ease;
    }
    .api-key-display.copied {
      border-color: #4caf50;
      background: #f1f8f4;
    }
    .api-key-code {
      flex: 1;
      font-family: 'Courier New', monospace;
      font-size: 15px;
      word-break: break-all;
      color: #333;
      user-select: all;
      cursor: text;
    }
    .api-key-code.masked {
      letter-spacing: 2px;
      color: #999;
    }
    .copy-status {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #4caf50;
      font-size: 14px;
      margin-top: 8px;
      font-weight: 500;
    }
    .copy-status mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }

    .info-box {
      background: #e3f2fd;
      border-left: 4px solid #2196f3;
      padding: 12px;
      border-radius: 4px;
      display: flex;
      gap: 12px;
      align-items: flex-start;
    }
    .info-box mat-icon {
      color: #2196f3;
      flex-shrink: 0;
    }
    .info-box p {
      margin: 0;
      color: #666;
      font-size: 13px;
      font-family: 'Courier New', monospace;
    }

    mat-icon {
      vertical-align: middle;
    }
  `]
})
export class ApiKeyDialogComponent implements OnInit {
  isVisible = true;
  copied = false;

  constructor(
    public dialogRef: MatDialogRef<ApiKeyDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { apiKey: string; warning?: string },
    private clipboard: Clipboard,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.copyToClipboard();
  }

  getTranslation(key: string, defaultValue: string): string {
    const translation = this.translate.instant(key);
    return translation !== key ? translation : defaultValue;
  }

  toggleVisibility(): void {
    this.isVisible = !this.isVisible;
  }

  copyToClipboard(): void {
    this.clipboard.copy(this.data.apiKey);
    this.copied = true;
    this.snackBar.open(
      this.getTranslation('common.copied', 'Copied to clipboard'),
      this.getTranslation('common.close', 'Close'),
      { duration: 3000, panelClass: ['success-snackbar'] }
    );
    
    setTimeout(() => {
      this.copied = false;
    }, 3000);
  }

  downloadAsFile(): void {
    const blob = new Blob([this.data.apiKey], { type: 'text/plain' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `api-key-${new Date().getTime()}.txt`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    
    this.snackBar.open(
      this.getTranslation('client.keyDownloaded', 'API key downloaded'),
      this.getTranslation('common.close', 'Close'),
      { duration: 2000 }
    );
  }

  onClose(): void {
    this.dialogRef.close();
  }
}

