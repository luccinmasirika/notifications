import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';
import { AdminService } from '../../services/admin.service';
@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {
  loginForm: FormGroup;
  loading = false;
  hidePassword = true;
  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private adminService: AdminService,
    private router: Router,
    private snackBar: MatSnackBar,
    private translate: TranslateService
  ) {
    this.loginForm = this.fb.group({
      username: ['', [Validators.required]],
      password: ['', [Validators.required]]
    });
  }
  ngOnInit(): void {
    if (this.authService.isLoggedIn()) {
      this.router.navigate(['/admin']);
    }
  }
  onSubmit(): void {
    if (this.loginForm.valid) {
      this.loading = true;
      const { username, password } = this.loginForm.value;
      this.adminService.testAuth(username, password).subscribe({
        next: () => {
          this.authService.login(username, password);
          this.translate.get('login.loginSuccessful').subscribe((msg) => {
            this.snackBar.open(msg, this.translate.instant('common.close'), { duration: 3000 });
          });
          this.router.navigate(['/admin']);
          this.loading = false;
        },
        error: (error) => {
          console.error('Login error:', error);
          this.translate.get('login.invalidCredentials').subscribe((msg) => {
            this.snackBar.open(msg, this.translate.instant('common.close'), { duration: 3000 });
          });
          this.loading = false;
        }
      });
    }
  }
  getErrorMessage(fieldName: string): string {
    const field = this.loginForm.get(fieldName);
    if (field?.hasError('required')) {
      return `${fieldName} is required`;
    }
    return '';
  }
}
