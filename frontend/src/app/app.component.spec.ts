import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of } from 'rxjs';
import { vi, beforeEach } from 'vitest';
import { AppComponent } from './app.component';
import { AuthService } from './services/auth.service';

beforeEach(() => {
  TestBed.resetTestingModule();
});

describe('AppComponent', () => {
  let component: AppComponent;
  let fixture: ComponentFixture<AppComponent>;
  let authService: ReturnType<typeof vi.fn>;
  let router: ReturnType<typeof vi.fn>;
  let translateService: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    const authServiceSpy = {
      logout: vi.fn(),
      isAuthenticated$: of(false)
    };
    const routerSpy = {
      navigate: vi.fn()
    };
    const translateServiceSpy = {
      setDefaultLang: vi.fn(),
      use: vi.fn(),
      getBrowserLang: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [AppComponent, TranslateModule.forRoot()],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: TranslateService, useValue: translateServiceSpy }
      ],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    authService = TestBed.inject(AuthService) as any;
    router = TestBed.inject(Router) as any;
    translateService = TestBed.inject(TranslateService) as any;

    authService.isAuthenticated$ = of(false);
    translateService.getBrowserLang.mockReturnValue('en');
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should set default language to English', () => {
    expect(translateService.setDefaultLang).toHaveBeenCalledWith('en');
  });

  it('should use browser language if available', () => {
    translateService.getBrowserLang.mockReturnValue('fr');
    component = new AppComponent(authService, router, translateService);

    expect(translateService.use).toHaveBeenCalledWith('fr');
  });

  it('should default to English if browser language is not supported', () => {
    translateService.getBrowserLang.mockReturnValue('de');
    component = new AppComponent(authService, router, translateService);

    expect(translateService.use).toHaveBeenCalledWith('en');
  });

  it('should subscribe to authentication state', () => {
    expect(authService.isAuthenticated$).toBeDefined();
  });

  it('should update isAuthenticated when auth state changes', () => {
    authService.isAuthenticated$ = of(true);
    component.ngOnInit();

    expect(component.isAuthenticated).toBe(true);
  });

  it('should logout and navigate to login', () => {
    component.logout();

    expect(authService.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });
});

