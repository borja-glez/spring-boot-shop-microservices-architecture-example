import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { UserStore } from '../user/user-store';

/**
 * Adds the headers every shop request carries: the simulated user and a fresh correlation id,
 * so a request can be followed through the gateway and the services' logs.
 */
export const shopHeadersInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) {
    return next(request);
  }
  const user = inject(UserStore).current();
  return next(
    request.clone({
      setHeaders: {
        'X-Shop-User': user.id,
        'X-Correlation-Id': crypto.randomUUID(),
      },
    }),
  );
};
