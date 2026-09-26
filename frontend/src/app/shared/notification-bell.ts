import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { NotificationsStore } from '../core/notifications/notifications-store';

const TOAST_MS = 6000;

/** Header link to the notices, with the unread count and a toast for each new one. */
@Component({
  selector: 'app-notification-bell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <a routerLink="/notifications" class="bell" [attr.aria-label]="label()">
      Notifications
      @if (store.unread()) {
        <span class="badge">{{ store.unread() }}</span>
      }
    </a>
    @if (store.justArrived(); as notice) {
      <div class="toast" role="status">
        <strong>{{ notice.title }}</strong>
        <span>{{ notice.body }}</span>
        <button type="button" class="close" aria-label="Close" (click)="store.dismiss()">×</button>
      </div>
    }
  `,
  styles: `
    :host {
      position: relative;
    }
    .bell {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      color: #dfe7f5;
      text-decoration: none;
      font-weight: 500;
    }
    .bell:hover {
      color: #fff;
    }
    .badge {
      min-width: 1.5em;
      padding: 0 6px;
      border-radius: 999px;
      background: var(--tomato);
      color: #fff;
      font-weight: 700;
      font-size: var(--step--1);
      text-align: center;
    }
    .toast {
      position: fixed;
      right: 16px;
      bottom: 16px;
      z-index: 10;
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 2px 12px;
      max-width: min(360px, calc(100vw - 32px));
      padding: 12px 16px;
      background: var(--surface);
      color: var(--ink);
      border: 1px solid var(--line);
      border-left: 4px solid var(--cobalt);
      border-radius: var(--radius-m);
      box-shadow: 0 6px 24px rgb(0 0 0 / 0.15);
    }
    .toast span {
      grid-column: 1;
      font-size: var(--step--1);
      color: var(--ink-soft);
    }
    .close {
      grid-row: 1 / span 2;
      grid-column: 2;
      border: 0;
      background: none;
      font-size: 1.25rem;
      cursor: pointer;
      color: var(--ink-soft);
    }
  `,
})
export class NotificationBell {
  protected readonly store = inject(NotificationsStore);

  constructor() {
    effect((onCleanup) => {
      if (this.store.justArrived()) {
        const timer = setTimeout(() => this.store.dismiss(), TOAST_MS);
        onCleanup(() => clearTimeout(timer));
      }
    });
  }

  protected label(): string {
    const unread = this.store.unread();
    return unread ? `Notifications, ${unread} unread` : 'Notifications';
  }
}
