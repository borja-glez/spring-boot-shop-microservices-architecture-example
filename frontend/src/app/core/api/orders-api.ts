import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
  EMPTY,
  Observable,
  catchError,
  exhaustMap,
  map,
  retry,
  takeWhile,
  throwError,
  timer,
} from 'rxjs';

import { FilterQuery } from '../filters/filter-model';
import { toQueryParams } from '../filters/filter-serializer';
import { PageResponse } from './models';
import {
  CheckoutView,
  HistoryEntry,
  OrderDetail,
  OrderItem,
  OrderStatus,
  OrderSummary,
  StoredEventView,
} from './order-models';

const BASE = '/api/orders';

/** How long to wait for the read model to catch up with a command: 20 × 250 ms. */
const CATCH_UP_ATTEMPTS = 20;
const CATCH_UP_DELAY_MS = 250;

const CHECKOUT_POLL_MS = 1000;

/** The read model is not there yet, or not in the expected state: worth asking again. */
class NotCaughtUp extends Error {}

@Injectable({ providedIn: 'root' })
export class OrdersApi {
  private readonly http = inject(HttpClient);

  myOrders(query: FilterQuery): Observable<PageResponse<OrderSummary>> {
    const queryString = toQueryParams(query);
    return this.http.get<PageResponse<OrderSummary>>(queryString ? `${BASE}?${queryString}` : BASE);
  }

  /**
   * The order from the read model. Kafka updates it a moment after each command, so right after
   * placing or cancelling, pass what to wait for and the request is repeated until it shows:
   * `'exists'` for a new order (whatever its status by now), or the status a command leads to.
   */
  order(id: string, expected?: OrderStatus | 'exists'): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`${BASE}/${encodeURIComponent(id)}`).pipe(
      map((order) => {
        if (expected && expected !== 'exists' && order.status !== expected) {
          throw new NotCaughtUp();
        }
        return order;
      }),
      retry({
        count: CATCH_UP_ATTEMPTS,
        delay: (error: unknown) =>
          error instanceof NotCaughtUp ||
          (error instanceof HttpErrorResponse && error.status === 404 && expected)
            ? timer(CATCH_UP_DELAY_MS)
            : throwError(() => error),
      }),
    );
  }

  /**
   * The checkout saga, asked every second until it completes. A request is never cut short by the
   * next tick, and a failed one (the gateway restarting, say) is simply asked again; only a 404,
   * an order without saga, ends the watch with an error.
   */
  watchCheckout(id: string): Observable<CheckoutView> {
    return timer(0, CHECKOUT_POLL_MS).pipe(
      exhaustMap(() =>
        this.http
          .get<CheckoutView>(`${BASE}/${encodeURIComponent(id)}/checkout`)
          .pipe(
            catchError((error: unknown) =>
              error instanceof HttpErrorResponse && error.status === 404
                ? throwError(() => error)
                : EMPTY,
            ),
          ),
      ),
      takeWhile((checkout) => checkout.state !== 'COMPLETED', true),
    );
  }

  /** The event stream of the order, straight from the event store: always current. */
  history(id: string): Observable<HistoryEntry[]> {
    return this.http.get<HistoryEntry[]>(`${BASE}/${encodeURIComponent(id)}/history`);
  }

  place(items: OrderItem[]): Observable<string> {
    return this.http.post<{ id: string }>(BASE, { items }).pipe(map((created) => created.id));
  }

  cancel(id: string, reason?: string): Observable<void> {
    return this.http.post<void>(`${BASE}/${encodeURIComponent(id)}/cancel`, {
      reason: reason?.trim() || null,
    });
  }

  events(query: FilterQuery): Observable<PageResponse<StoredEventView>> {
    const queryString = toQueryParams(query);
    return this.http.get<PageResponse<StoredEventView>>(
      queryString ? `${BASE}/events?${queryString}` : `${BASE}/events`,
    );
  }
}
