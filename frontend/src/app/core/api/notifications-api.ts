import { HttpClient } from '@angular/common/http';
import { Injectable, NgZone, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { PageResponse } from './models';

export type NotificationKind =
  'ORDER_CONFIRMED' | 'ORDER_REJECTED' | 'ORDER_CANCELLED' | 'PAYMENT_REFUNDED';

/** A notice (see notifications-service `NotificationView`). */
export interface NotificationView {
  id: string;
  orderId: string;
  kind: NotificationKind;
  title: string;
  body: string;
  occurredAt: string;
  read: boolean;
}

const BASE = '/api/notifications';

/** The notifications service, the only one on Spring Boot 3. */
@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly http = inject(HttpClient);
  private readonly zone = inject(NgZone);

  mine(unreadOnly = false, size = 20): Observable<PageResponse<NotificationView>> {
    return this.http.get<PageResponse<NotificationView>>(
      `${BASE}?unread=${unreadOnly}&size=${size}`,
    );
  }

  unreadCount(): Observable<number> {
    return this.http.get<{ unread: number }>(`${BASE}/unread-count`).pipe(map((r) => r.unread));
  }

  markRead(id: string): Observable<void> {
    return this.http.post<void>(`${BASE}/${encodeURIComponent(id)}/read`, null);
  }

  /**
   * Notices as they happen, over server-sent events. The user travels as a parameter because
   * EventSource cannot send headers.
   *
   * EventSource reconnects by itself after a network drop, but gives up for good when the server
   * answers with an error (a 502 or 503 while the service restarts). Then the stream is opened
   * again with growing waits. {@code onOpen} runs on every (re)connection, so the caller can catch
   * up with the notices sent while it was away.
   */
  stream(userId: string, onOpen: () => void = () => undefined): Observable<NotificationView> {
    return new Observable<NotificationView>((subscriber) => {
      let source: EventSource | null = null;
      let retry: ReturnType<typeof setTimeout> | null = null;
      let failures = 0;
      const open = () => {
        source = new EventSource(`${BASE}/stream?user=${encodeURIComponent(userId)}`);
        source.addEventListener('open', () => {
          failures = 0;
          this.zone.run(onOpen);
        });
        source.addEventListener('notification', (event) => {
          const notice = JSON.parse((event as MessageEvent<string>).data) as NotificationView;
          this.zone.run(() => subscriber.next(notice));
        });
        source.addEventListener('error', () => {
          if (source?.readyState === EventSource.CLOSED) {
            source.close();
            failures++;
            retry = setTimeout(open, Math.min(30_000, 1000 * 2 ** Math.min(failures, 5)));
          }
        });
      };
      open();
      return () => {
        if (retry) {
          clearTimeout(retry);
        }
        source?.close();
      };
    });
  }
}
