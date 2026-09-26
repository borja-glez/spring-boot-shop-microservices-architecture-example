import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { FilterQuery } from '../filters/filter-model';
import { toQueryParams } from '../filters/filter-serializer';
import { PageResponse } from './models';

/** A product in the warehouse (see inventory-service `StockView`). */
export interface StockView {
  productId: string;
  sku: string;
  name: string;
  onHand: number;
  reserved: number;
  available: number;
  updatedAt: string;
}

export type PaymentStatus = 'AUTHORIZED' | 'DECLINED' | 'REFUNDED';

/** A payment (see payments-service `PaymentSummary`). */
export interface PaymentSummary {
  orderId: string;
  paymentId: string;
  customerId: string | null;
  amount: number;
  currency: string | null;
  status: PaymentStatus;
  reason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PaymentEvent {
  version: number;
  eventType: string;
  occurredAt: string;
  publishedAt: string | null;
  event: Record<string, unknown>;
}

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  AUTHORIZED: 'Authorized',
  DECLINED: 'Declined',
  REFUNDED: 'Refunded',
};

function withQuery(path: string, query: FilterQuery): string {
  const queryString = toQueryParams(query);
  return queryString ? `${path}?${queryString}` : path;
}

/** The inventory and payments backoffice APIs. */
@Injectable({ providedIn: 'root' })
export class BackofficeApi {
  private readonly http = inject(HttpClient);

  stock(query: FilterQuery): Observable<PageResponse<StockView>> {
    return this.http.get<PageResponse<StockView>>(withQuery('/api/inventory/stock', query));
  }

  /** Sets the units counted in the warehouse. */
  adjustStock(productId: string, onHand: number): Observable<void> {
    return this.http.put<void>(`/api/inventory/stock/${encodeURIComponent(productId)}`, {
      onHand,
    });
  }

  payments(query: FilterQuery): Observable<PageResponse<PaymentSummary>> {
    return this.http.get<PageResponse<PaymentSummary>>(withQuery('/api/payments', query));
  }

  paymentHistory(orderId: string): Observable<PaymentEvent[]> {
    return this.http.get<PaymentEvent[]>(`/api/payments/${encodeURIComponent(orderId)}/history`);
  }
}
