import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { UserStore } from '../user/user-store';
import { CatalogApi } from './catalog-api';
import { shopHeadersInterceptor } from './shop-headers.interceptor';
import { toProblem } from './problem';
import { HttpErrorResponse } from '@angular/common/http';

describe('CatalogApi', () => {
  let http: HttpTestingController;
  let api: CatalogApi;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([shopHeadersInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(CatalogApi);
  });

  afterEach(() => http.verify());

  it('sends the filters of a search in the query string', () => {
    api
      .search({ all: [{ field: 'name', operator: 'contains', value: 'café' }], anyOf: [], page: 1 })
      .subscribe();

    const request = http.expectOne((r) => r.url.startsWith('/api/catalog/products?'));
    const params = new URLSearchParams(request.request.url.split('?')[1]);
    expect(params.getAll('filter')).toEqual(['name:contains:café']);
    expect(params.get('page')).toBe('1');
    request.flush({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('identifies the user and correlates every request', () => {
    TestBed.inject(UserStore).select('seller-ana');

    api.categories().subscribe();

    const request = http.expectOne('/api/catalog/categories');
    expect(request.request.headers.get('X-Shop-User')).toBe('seller-ana');
    expect(request.request.headers.get('X-Correlation-Id')).toMatch(/^[0-9a-f-]{36}$/);
    request.flush([]);
  });

  it('keeps the raw response of lab requests, errors included', () => {
    let status = 0;
    api
      .raw('/api/catalog/products', 'filter=status:EQ:ACTIVE')
      .subscribe((r) => (status = r.status));

    http
      .expectOne('/api/catalog/products?filter=status:EQ:ACTIVE')
      .flush({ code: 'internal-error' }, { status: 500, statusText: 'Server Error' });

    expect(status).toBe(500);
  });
});

describe('toProblem', () => {
  it('reads RFC 9457 problems', () => {
    const problem = toProblem(
      new HttpErrorResponse({
        status: 400,
        error: {
          title: 'Bad Request',
          detail: 'bad filter',
          code: 'invalid-filter',
          correlationId: 'c-1',
        },
      }),
    );

    expect(problem).toMatchObject({
      status: 400,
      code: 'invalid-filter',
      detail: 'bad filter',
      correlationId: 'c-1',
    });
  });

  it('explains network failures', () => {
    const problem = toProblem(
      new HttpErrorResponse({ status: 0, error: new ProgressEvent('error') }),
    );

    expect(problem.code).toBe('network-error');
  });
});
