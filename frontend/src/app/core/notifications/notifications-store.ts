import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { NotificationView, NotificationsApi } from '../api/notifications-api';
import { UserStore } from '../user/user-store';

const LATEST = 20;

/**
 * The current user's notices: the latest ones, how many are unread, and the ones that just
 * arrived. Switching user reloads everything and reopens the live stream for the new one.
 */
@Injectable({ providedIn: 'root' })
export class NotificationsStore {
  private readonly api = inject(NotificationsApi);
  private readonly users = inject(UserStore);

  private readonly items = signal<NotificationView[]>([]);
  private readonly unreadCount = signal(0);
  private readonly fresh = signal<NotificationView | null>(null);
  private stream: Subscription | null = null;
  /** Bumped on every user switch; answers for an earlier user are dropped. */
  private generation = 0;

  readonly notices = this.items.asReadonly();
  readonly unread = this.unreadCount.asReadonly();
  /** The last notice received live, for a toast; cleared by {@link dismiss}. */
  readonly justArrived = this.fresh.asReadonly();
  readonly hasUnread = computed(() => this.unreadCount() > 0);

  constructor() {
    effect(() => this.follow(this.users.current().id));
    inject(DestroyRef).onDestroy(() => this.stream?.unsubscribe());
  }

  /** Adds a notice received live, once. */
  receive(notice: NotificationView): void {
    if (this.items().some((n) => n.id === notice.id)) {
      return;
    }
    this.items.update((list) => [notice, ...list].slice(0, LATEST));
    this.fresh.set(notice);
    // Ask the server rather than adding one: a notice pushed while the first load was on its way
    // may already be in the count it returned.
    this.refreshCount();
  }

  markRead(notice: NotificationView): void {
    if (notice.read) {
      return;
    }
    this.items.update((list) => list.map((n) => (n.id === notice.id ? { ...n, read: true } : n)));
    this.unreadCount.update((n) => Math.max(0, n - 1));
    this.api.markRead(notice.id).subscribe({ error: () => this.reload() });
  }

  dismiss(): void {
    this.fresh.set(null);
  }

  reload(): void {
    const generation = this.generation;
    this.api.mine(false, LATEST).subscribe({
      next: (page) => this.ifCurrent(generation, () => this.items.set(page.content)),
      error: () => this.ifCurrent(generation, () => this.items.set([])),
    });
    this.refreshCount();
  }

  private refreshCount(): void {
    const generation = this.generation;
    this.api.unreadCount().subscribe({
      next: (count) => this.ifCurrent(generation, () => this.unreadCount.set(count)),
      error: () => this.ifCurrent(generation, () => this.unreadCount.set(0)),
    });
  }

  private ifCurrent(generation: number, apply: () => void): void {
    if (generation === this.generation) {
      apply();
    }
  }

  private follow(userId: string): void {
    this.generation++;
    this.stream?.unsubscribe();
    this.items.set([]);
    this.unreadCount.set(0);
    this.fresh.set(null);
    this.reload();
    // Every reconnection catches up with what was sent while the stream was down.
    this.stream = this.api
      .stream(userId, () => this.reload())
      .subscribe((notice) => this.receive(notice));
  }
}
