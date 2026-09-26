import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { FilterQuery } from '../filters/filter-model';
import { toQueryParams } from '../filters/filter-serializer';

export interface StatusCount {
  status: 'PLACED' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED';
  orders: number;
}

export interface DailySales {
  day: string;
  currency: string;
  orders: number;
  customers: number;
  revenue: number;
}

export interface ProductSales {
  sku: string;
  name: string;
  currency: string;
  units: number;
  orders: number;
  revenue: number;
}

export interface RejectionCount {
  reason: string;
  orders: number;
}

export interface CustomerSales {
  customerId: string;
  currency: string;
  orders: number;
  spent: number;
}

export interface RebuildStatus {
  running: boolean;
  startedAt: string | null;
  finishedAt: string | null;
  error: string | null;
}

const BASE = '/api/reporting';

/** Report days are filtered on the order's placement day (`placedDay`). */
export function dayRange(from: string, to: string): FilterQuery {
  return {
    all: [
      ...(from ? [{ field: 'placedDay', operator: 'gte' as const, value: from }] : []),
      ...(to ? [{ field: 'placedDay', operator: 'lte' as const, value: to }] : []),
    ],
    anyOf: [],
  };
}

/** The same range for the product report, whose rows are order lines. */
export function lineDayRange(from: string, to: string): FilterQuery {
  const range = dayRange(from, to);
  return { ...range, all: range.all.map((c) => ({ ...c, field: 'order.placedDay' })) };
}

function withQuery(path: string, query: FilterQuery, extra = ''): string {
  const queryString = [toQueryParams(query), extra].filter(Boolean).join('&');
  return queryString ? `${path}?${queryString}` : path;
}

@Injectable({ providedIn: 'root' })
export class ReportingApi {
  private readonly http = inject(HttpClient);

  summary(query: FilterQuery): Observable<StatusCount[]> {
    return this.http.get<StatusCount[]>(withQuery(`${BASE}/summary`, query));
  }

  salesByDay(query: FilterQuery): Observable<DailySales[]> {
    return this.http.get<DailySales[]>(withQuery(`${BASE}/sales-by-day`, query));
  }

  topProducts(query: FilterQuery, minUnits: number): Observable<ProductSales[]> {
    return this.http.get<ProductSales[]>(
      withQuery(`${BASE}/top-products`, query, `minUnits=${minUnits}`),
    );
  }

  rejections(query: FilterQuery): Observable<RejectionCount[]> {
    return this.http.get<RejectionCount[]>(withQuery(`${BASE}/rejections`, query));
  }

  customers(query: FilterQuery): Observable<CustomerSales[]> {
    return this.http.get<CustomerSales[]>(withQuery(`${BASE}/customers`, query));
  }

  rebuild(): Observable<RebuildStatus> {
    return this.http.post<RebuildStatus>(`${BASE}/rebuild`, null);
  }
}
