import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { apiKeyInterceptor } from './api-key.interceptor';
import { environment } from '../../../environments/environment';

describe('apiKeyInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiKeyInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should add X-API-Key header on POST when apiKey is configured', () => {
    const original = environment.apiKey;
    environment.apiKey = 'test-key';
    try {
      http.post('/api/test', {}).subscribe();
      const req = httpMock.expectOne('/api/test');
      expect(req.request.headers.get('X-API-Key')).toBe('test-key');
      req.flush({});
    } finally {
      environment.apiKey = original;
    }
  });

  it('should not add X-API-Key header on GET requests', () => {
    http.get('/api/test').subscribe();
    const req = httpMock.expectOne('/api/test');
    expect(req.request.headers.has('X-API-Key')).toBeFalse();
    req.flush({});
  });
});