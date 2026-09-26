import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { NotificationsStore } from '../../core/notifications/notifications-store';
import { UserStore } from '../../core/user/user-store';

/** The current user's notices, newest first; they arrive live from notifications-service. */
@Component({
  selector: 'app-notifications-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink],
  template: `
    <h1>Notifications</h1>
    <p class="intro">
      Sent by <code>notifications-service</code>, the only service on Spring Boot 3 (Jackson 2),
      from the events the other services publish to Kafka. They arrive live over server-sent events.
    </p>
    @if (store.notices().length === 0) {
      <p class="panel empty">{{ users.current().name }} has no notifications yet.</p>
    } @else {
      <ul class="list">
        @for (notice of store.notices(); track notice.id) {
          <li class="panel" [class.unread]="!notice.read">
            <div class="head">
              <strong>{{ notice.title }}</strong>
              <time [attr.datetime]="notice.occurredAt">{{
                notice.occurredAt | date: 'medium'
              }}</time>
            </div>
            <p>{{ notice.body }}</p>
            <div class="actions">
              <a [routerLink]="['/orders', notice.orderId]" (click)="store.markRead(notice)"
                >View order</a
              >
              @if (!notice.read) {
                <button type="button" class="button secondary" (click)="store.markRead(notice)">
                  Mark as read
                </button>
              }
            </div>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
      max-width: 760px;
    }
    h1,
    .intro {
      margin: 0;
    }
    .intro {
      color: var(--ink-soft);
    }
    .empty {
      padding: 24px;
      margin: 0;
    }
    .list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .list li {
      padding: 12px 16px;
      display: flex;
      flex-direction: column;
      gap: 6px;
      border-left: 4px solid transparent;
    }
    .list li.unread {
      border-left-color: var(--cobalt);
    }
    .head {
      display: flex;
      justify-content: space-between;
      gap: 12px;
    }
    time {
      color: var(--ink-soft);
      font-size: var(--step--1);
    }
    p {
      margin: 0;
    }
    .actions {
      display: flex;
      gap: 12px;
      align-items: center;
    }
  `,
})
export class NotificationsPage {
  protected readonly store = inject(NotificationsStore);
  protected readonly users = inject(UserStore);
}
