import { HttpClient, HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';

import { FilterQuery } from '../filters/filter-model';
import { toQueryParams } from '../filters/filter-serializer';
import { CatalogFacets, CategoryView, PageResponse, ProductCard, ProductDetail } from './models';

/** A response kept as is, for the filter lab. */
export interface RawResponse {
  url: string;
  status: number;
  correlationId: string | null;
  body: unknown;
  durationMs: number;
}

const BASE = '/api/catalog';

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  search(query: FilterQuery): Observable<PageResponse<ProductCard>> {
    return this.http.get<PageResponse<ProductCard>>(withQuery(`${BASE}/products`, query));
  }

  facets(query: FilterQuery): Observable<CatalogFacets> {
    return this.http.get<CatalogFacets>(
      withQuery(`${BASE}/products/facets`, { all: query.all, anyOf: query.anyOf }),
    );
  }

  product(slug: string): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(`${BASE}/products/${encodeURIComponent(slug)}`);
  }

  categories(): Observable<CategoryView[]> {
    return this.http.get<CategoryView[]>(`${BASE}/categories`);
  }

  /** Performs a GET and returns status, headers and body whatever the outcome. */
  raw(path: string, queryString: string): Observable<RawResponse> {
    const url = queryString ? `${path}?${queryString}` : path;
    const started = performance.now();
    return this.http.get(url, { observe: 'response' }).pipe(
      map((response: HttpResponse<unknown>) => toRaw(url, response, started)),
      catchError((error: HttpErrorResponse) => of(toRaw(url, error, started))),
    );
  }
}

function withQuery(path: string, query: FilterQuery): string {
  const queryString = toQueryParams(query);
  return queryString ? `${path}?${queryString}` : path;
}

function toRaw(
  url: string,
  response: HttpResponse<unknown> | HttpErrorResponse,
  started: number,
): RawResponse {
  return {
    url,
    status: response.status,
    correlationId: response.headers?.get('X-Correlation-Id') ?? null,
    body: response instanceof HttpResponse ? response.body : response.error,
    durationMs: Math.round(performance.now() - started),
  };
}
