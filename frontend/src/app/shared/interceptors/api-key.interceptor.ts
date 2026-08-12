import { HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { environment } from '../../../environments/environment';

/**
 * Injects the shared {@code X-API-Key} header on mutating requests only
 * (POST/PATCH/PUT/DELETE), matching the backend's authentication gate.
 *
 * <p>The key is sourced from the build-time environment ({@code environment.apiKey})
 * and is never hardcoded in component logic. Read-only requests (GET/HEAD/OPTIONS)
 * are left untouched because the audit/ledger endpoints remain public.
 */
const MUTATING_METHODS = new Set(['POST', 'PATCH', 'PUT', 'DELETE']);

export const apiKeyInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
) => {
  if (MUTATING_METHODS.has(req.method) && environment.apiKey) {
    const cloned = req.clone({
      setHeaders: { 'X-API-Key': environment.apiKey },
    });
    return next(cloned);
  }
  return next(req);
};