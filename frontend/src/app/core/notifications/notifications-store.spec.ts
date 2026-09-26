import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { NotificationView, NotificationsApi } from '../api/notifications-api';
import { UserStore } from '../user/user-store';
import { NotificationsStore } from './notifications-store';

function notice(id: string, read = false): NotificationView {
  return {
    id,
    orderId: 'o-1',
    kind: 'ORDER_CONFIRMED',
    title: 'Order confirmed',
    body: 'Your order is confirmed.',
    occurredAt: '2026-09-26T10:00:00Z',
    read,
  };
}

describe('NotificationsStore', () => {
  let live: Record<string, Subject<NotificationView>>;
  let api: {
    mine: ReturnType<typeof vi.fn>;
    unreadCount: ReturnType<typeof vi.fn>;
    markRead: ReturnType<typeof vi.fn>;
    stream: (user: string, onOpen?: () => void) => Observable<NotificationView>;
  };

  beforeEach(() => {
    localStorage.clear();
    live = {};
    api = {
      mine: vi.fn(() =>
        of({ content: [notice('old', true)], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
      ),
      unreadCount: vi.fn().mockReturnValueOnce(of(2)).mockReturnValue(of(3)),
      markRead: vi.fn(() => of(undefined)),
      stream: (user: string) => (live[user] ??= new Subject<NotificationView>()),
    };
    TestBed.configureTestingModule({ providers: [{ provide: NotificationsApi, useValue: api }] });
  });

  it('loads the notices of the current user and listens for new ones', () => {
    const store = TestBed.inject(NotificationsStore);
    TestBed.tick();

    expect(store.notices().map((n) => n.id)).toEqual(['old']);
    expect(store.unread()).toBe(2);

    live['cliente-lucia'].next(notice('new'));
    live['cliente-lucia'].next(notice('new'));

    expect(store.notices().map((n) => n.id)).toEqual(['new', 'old']);
    // The count comes from the server again, not from adding one per notice.
    expect(store.unread()).toBe(3);
    expect(api.unreadCount).toHaveBeenCalledTimes(2);
    expect(store.justArrived()?.id).toBe('new');
    store.dismiss();
    expect(store.justArrived()).toBeNull();
  });

  it('marks a notice as read once', () => {
    const store = TestBed.inject(NotificationsStore);
    TestBed.tick();
    live['cliente-lucia'].next(notice('new'));

    store.markRead(store.notices()[0]);
    store.markRead(store.notices()[0]);

    expect(api.markRead).toHaveBeenCalledTimes(1);
    expect(store.unread()).toBe(2);
    expect(store.notices()[0].read).toBe(true);
  });

  it('follows the user that is selected', () => {
    const store = TestBed.inject(NotificationsStore);
    TestBed.tick();

    TestBed.inject(UserStore).select('cliente-mateo');
    TestBed.tick();
    live['cliente-lucia'].next(notice('for-lucia'));
    live['cliente-mateo'].next(notice('for-mateo'));

    expect(store.notices().map((n) => n.id)).toEqual(['for-mateo', 'old']);
    expect(live['cliente-lucia'].observed).toBe(false);
  });

  it('drops answers that arrive for the previous user', () => {
    const late = new Subject<{
      content: NotificationView[];
      page: number;
      size: number;
      totalElements: number;
      totalPages: number;
    }>();
    api.mine.mockReturnValueOnce(late);
    const store = TestBed.inject(NotificationsStore);
    TestBed.tick();

    TestBed.inject(UserStore).select('cliente-mateo');
    TestBed.tick();
    late.next({ content: [notice('lucias')], page: 0, size: 20, totalElements: 1, totalPages: 1 });

    expect(store.notices().map((n) => n.id)).toEqual(['old']);
  });
});
