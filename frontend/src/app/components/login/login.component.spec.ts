import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of, throwError } from 'rxjs';
import { vi, beforeEach } from 'vitest';
import { LoginComponent } from './login.component';
import { AuthService } from '../../services/auth.service';
import { AdminService } from '../../services/admin.service';

// Ensure TestBed is reset before each test
beforeEach(() => {
  TestBed.resetTestingModule();
});

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authService: ReturnType<typeof vi.fn>;
  let adminService: ReturnType<typeof vi.fn>;
  let router: ReturnType<typeof vi.fn>;
  let snackBar: ReturnType<typeof vi.fn>;
  let translateService: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    TestBed.resetTestingModule();
    const authServiceSpy = {
      isLoggedIn: vi.fn(),
      login: vi.fn()
    };
    const adminServiceSpy = {
      testAuth: vi.fn()
    };
    const routerSpy = {
      navigate: vi.fn()
    };
    const snackBarSpy = {
      open: vi.fn()
    };
    const translateServiceSpy = {
      get: vi.fn(),
      instant: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [
        LoginComponent,
        ReactiveFormsModule,
        TranslateModule.forRoot(),
        BrowserAnimationsModule
      ],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: AdminService, useValue: adminServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: MatSnackBar, useValue: snackBarSpy },
        { provide: TranslateService, useValue: translateServiceSpy }
      ]
    }).compileComponents();

    authService = TestBed.inject(AuthService) as any;
    adminService = TestBed.inject(AdminService) as any;
    router = TestBed.inject(Router) as any;
    snackBar = TestBed.inject(MatSnackBar) as any;
    translateService = TestBed.inject(TranslateService) as any;

    translateService.get.mockReturnValue(of('translated message'));
    translateService.instant.mockReturnValue('Close');
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    authService.isLoggedIn.mockReturnValue(false);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize form with empty values', () => {
    expect(component.loginForm.get('username')?.value).toBe('');
    expect(component.loginForm.get('password')?.value).toBe('');
  });

  it('should have required validators on username and password', () => {
    const usernameControl = component.loginForm.get('username');
    const passwordControl = component.loginForm.get('password');

    expect(usernameControl?.hasError('required')).toBe(true);
    expect(passwordControl?.hasError('required')).toBe(true);
  });

  it('should redirect to admin if already logged in', () => {
    authService.isLoggedIn.mockReturnValue(true);
    component.ngOnInit();

    expect(router.navigate).toHaveBeenCalledWith(['/admin']);
  });

  it('should not submit if form is invalid', () => {
    component.onSubmit();

    expect(adminService.testAuth).not.toHaveBeenCalled();
    expect(component.loading).toBe(false);
  });

  it('should call testAuth on valid form submission', () => {
    adminService.testAuth.mockReturnValue(of([]));
    component.loginForm.patchValue({
      username: 'testuser',
      password: 'testpass'
    });

    component.onSubmit();

    expect(adminService.testAuth).toHaveBeenCalledWith('testuser', 'testpass');
  });

  it('should set loading to true during authentication', () => {
    adminService.testAuth.and.returnValue(of([]));
    component.loginForm.patchValue({
      username: 'testuser',
      password: 'testpass'
    });

    component.onSubmit();

    expect(component.loading).toBe(true);
  });

  it('should login and navigate on successful authentication', () => {
    adminService.testAuth.and.returnValue(of([]));
    component.loginForm.patchValue({
      username: 'testuser',
      password: 'testpass'
    });

    component.onSubmit();

    expect(authService.login).toHaveBeenCalledWith('testuser', 'testpass');
    expect(router.navigate).toHaveBeenCalledWith(['/admin']);
    expect(component.loading).toBe(false);
  });

  it('should show error message on authentication failure', () => {
    adminService.testAuth.mockReturnValue(throwError(() => new Error('Unauthorized')));
    component.loginForm.patchValue({
      username: 'testuser',
      password: 'wrongpass'
    });

    component.onSubmit();

    expect(authService.login).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
    expect(component.loading).toBe(false);
    expect(translateService.get).toHaveBeenCalledWith('login.invalidCredentials');
  });

  it('should toggle password visibility', () => {
    expect(component.hidePassword).toBe(true);

    component.hidePassword = false;
    expect(component.hidePassword).toBe(false);
  });

  it('should return error message for required field', () => {
    const error = component.getErrorMessage('username');
    expect(error).toBe('username is required');
  });

  it('should return empty string for field without errors', () => {
    component.loginForm.patchValue({ username: 'test' });
    const error = component.getErrorMessage('username');
    expect(error).toBe('');
  });
});

