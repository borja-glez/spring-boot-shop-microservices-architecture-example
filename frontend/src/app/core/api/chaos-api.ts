import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';

/** Services that expose `/api/<service>/chaos` when `SHOP_CHAOS_ENABLED=true`. */
export const CHAOS_SERVICES = ['payments', 'inventory', 'orders', 'catalog'] as const;
export type ChaosService = (typeof CHAOS_SERVICES)[number];

/** A fault of a service (see service-support `ChaosController.FaultView`). */
export interface ChaosFault {
  key: string;
  description: string;
  kind: 'TOGGLE' | 'DELAY';
  value: number;
}

/** What the demo explains for each fault it knows. */
export interface FaultGuide {
  label: string;
  effect: string;
}

const REPLY_TIMEOUT = 'the saga reply timeout (5 s)';

export const FAULT_GUIDE: Record<string, FaultGuide> = {
  'payments.decline-all': {
    label: 'Decline every card',
    effect:
      'The card limit drops to zero: new orders are rejected (card-limit-exceeded) and the saga releases the reserved stock.',
  },
  'AuthorizePayment.fail': {
    label: 'Payments down',
    effect:
      'The saga retries the payment with growing back-off. Turn it off in time and the order is confirmed; once retries run out, the saga undoes the reservation and nobody is charged.',
  },
  'AuthorizePayment.delay-ms': {
    label: 'Slow payments',
    effect: `If it takes longer than ${REPLY_TIMEOUT}, the saga sees a timeout and retries the payment; since it is idempotent per order, nobody pays twice. If the slowness outlasts the retries, the saga gives up, refunds the late payment and rejects the order.`,
  },
  'RefundPayment.fail': {
    label: 'Refunds down',
    effect:
      'Cancellations and rejections stay in compensation (and the saga turns STUCK) until it comes back; then the refund happens exactly once.',
  },
  'RefundPayment.delay-ms': {
    label: 'Slow refunds',
    effect: `If it takes longer than ${REPLY_TIMEOUT}, the compensation is retried; the refund is idempotent.`,
  },
  'ReserveStock.fail': {
    label: 'Reservations down',
    effect:
      'New orders wait at “Reserve stock” while the saga retries; nothing is charged before stock is reserved.',
  },
  'ReserveStock.delay-ms': {
    label: 'Slow reservations',
    effect: `If it takes longer than ${REPLY_TIMEOUT}, the saga retries the reservation; inventory applies it only once.`,
  },
  'ReleaseStock.fail': {
    label: 'Stock releases down',
    effect:
      'Stock of rejected or cancelled orders stays set aside until it comes back; the saga keeps trying.',
  },
  'ReleaseStock.delay-ms': {
    label: 'Slow stock releases',
    effect: `If it takes longer than ${REPLY_TIMEOUT}, the saga retries the release, which is idempotent.`,
  },
  'relay.paused': {
    label: 'Kafka down (relay paused)',
    effect:
      'Events pile up unpublished in event_store: reports, notifications and projections freeze. On resume they all go out, in order.',
  },
  'relay.duplicate': {
    label: 'Duplicate delivery',
    effect:
      'Every event is published twice. Idempotent consumers drop the copy: reports do not double count and no notification arrives twice.',
  },
};

/** The faults of one service; `null` when its chaos endpoint is off (404). */
@Injectable({ providedIn: 'root' })
export class ChaosApi {
  private readonly http = inject(HttpClient);

  faults(service: ChaosService): Observable<ChaosFault[] | null> {
    return this.http
      .get<ChaosFault[]>(`/api/${service}/chaos`)
      .pipe(
        catchError((error: unknown) =>
          error instanceof HttpErrorResponse && error.status === 404
            ? of(null)
            : throwError(() => error),
        ),
      );
  }

  set(service: ChaosService, key: string, value: number): Observable<ChaosFault> {
    return this.http.put<ChaosFault>(`/api/${service}/chaos/${encodeURIComponent(key)}`, { value });
  }

  /** Turns every fault of a service off; a service without chaos has nothing to turn off. */
  reset(service: ChaosService): Observable<void> {
    return this.http
      .delete<void>(`/api/${service}/chaos`)
      .pipe(
        catchError((error: unknown) =>
          error instanceof HttpErrorResponse && error.status === 404
            ? of(undefined)
            : throwError(() => error),
        ),
      );
  }
}
