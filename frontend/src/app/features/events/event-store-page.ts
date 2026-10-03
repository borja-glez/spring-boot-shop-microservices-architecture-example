import { DatePipe, JsonPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';

import { EVENT_TYPES } from '../../core/api/order-models';
import { OrdersApi } from '../../core/api/orders-api';
import { toProblem } from '../../core/api/problem';
import { Condition, FilterQuery } from '../../core/filters/filter-model';
import { toQueryParams } from '../../core/filters/filter-serializer';
import { ProblemAlert } from '../../shared/problem-alert';
import { valueOf } from '../../shared/resource-value';

const PAGE_SIZE = 20;

/**
 * Explorer of the orders event store, which doubles as the outbox. Rows with a version are
 * event-sourced streams; the pending ones are still waiting for the relay.
 */
@Component({
  selector: 'app-event-store-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, JsonPipe, ProblemAlert],
  template: `
    <h1>Orders event store</h1>
    <p class="intro">
      Every order is a sequence of versioned events. The same table doubles as the outbox: a relay
      publishes pending rows to Kafka and stamps the publication time.
    </p>
    <form class="panel filters" (submit)="$event.preventDefault()">
      <label>
        Event type
        <select class="field" (change)="eventType.set($any($event.target).value); page.set(0)">
          <option value="">All</option>
          @for (type of eventTypes; track type) {
            <option [value]="type">{{ type }}</option>
          }
        </select>
      </label>
      <label>
        Stream (order id)
        <input
          class="field"
          [value]="streamId()"
          (change)="streamId.set($any($event.target).value.trim()); page.set(0)"
        />
      </label>
      <label class="check">
        <input
          type="checkbox"
          [checked]="pendingOnly()"
          (change)="pendingOnly.set($any($event.target).checked); page.set(0)"
        />
        Only pending publication
      </label>
      <button type="button" class="button secondary" (click)="events.reload()">Refresh</button>
      <p class="request">
        <code>GET /api/orders/events?{{ readableQuery() }}</code>
      </p>
    </form>

    @if (problem(); as problem) {
      <app-problem-alert [problem]="problem" />
    }

    @if (eventsPage(); as page) {
      <p class="count">
        @if (page.content.length === 0) {
          No events
        } @else {
          Events {{ page.page * page.size + 1 }}–{{ page.page * page.size + page.content.length }}
          @if (page.hasNext) {
            (more on the next page)
          }
        }
      </p>
      <div class="panel table">
        <table>
          <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">Stream</th>
              <th scope="col">Version</th>
              <th scope="col">Type</th>
              <th scope="col">Occurred</th>
              <th scope="col">Published</th>
            </tr>
          </thead>
          <tbody>
            @for (row of page.content; track row.globalPosition) {
              <tr>
                <td class="num">{{ row.globalPosition }}</td>
                <td>
                  <button
                    type="button"
                    class="link"
                    (click)="streamId.set(row.streamId); this.page.set(0)"
                  >
                    {{ row.streamType }}/{{ row.streamId.slice(0, 8) }}
                  </button>
                </td>
                <td class="num">{{ row.version ?? '—' }}</td>
                <td>
                  <details>
                    <summary>
                      <code>{{ shortType(row.eventType) }}</code>
                    </summary>
                    <pre>{{ row.payload | json }}</pre>
                    <pre>{{ row.metadata | json }}</pre>
                  </details>
                </td>
                <td>{{ row.occurredAt | date: 'medium' }}</td>
                <td [class.pending]="!row.publishedAt">
                  @if (row.publishedAt) {
                    {{ row.publishedAt | date: 'mediumTime' }}
                  } @else {
                    Pending
                    @if (row.publishAttempts) {
                      ({{ row.publishAttempts }} attempts: {{ row.lastError }})
                    }
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      @if (page.page > 0 || page.hasNext) {
        <nav class="pages" aria-label="Pages">
          <button
            type="button"
            class="button secondary"
            [disabled]="page.page === 0"
            (click)="this.page.set(page.page - 1)"
          >
            Previous
          </button>
          <span>Page {{ page.page + 1 }}</span>
          <button
            type="button"
            class="button secondary"
            [disabled]="!page.hasNext"
            (click)="this.page.set(page.page + 1)"
          >
            Next
          </button>
        </nav>
      }
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    h1,
    .intro,
    .count {
      margin: 0;
    }
    .intro {
      max-width: 70ch;
      color: var(--ink-soft);
    }
    .filters {
      display: flex;
      flex-wrap: wrap;
      gap: 12px 24px;
      padding: 16px;
      align-items: end;
    }
    .filters label {
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: var(--step--1);
      color: var(--ink-soft);
    }
    .filters label.check {
      flex-direction: row;
      align-items: center;
    }
    .request {
      flex-basis: 100%;
      margin: 0;
      font-size: var(--step--1);
      overflow-wrap: anywhere;
    }
    .table {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: var(--step--1);
    }
    th,
    td {
      padding: 8px;
      border-bottom: 1px solid var(--line);
      text-align: left;
      vertical-align: top;
    }
    .num {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .pending {
      color: var(--amber);
      font-weight: 600;
    }
    .link {
      border: 0;
      background: none;
      padding: 0;
      color: var(--cobalt);
      font: inherit;
      font-family: var(--font-code);
      cursor: pointer;
      text-decoration: underline;
    }
    pre {
      max-width: 60ch;
      max-height: 240px;
      overflow: auto;
      font-size: 0.75rem;
    }
    .pages {
      display: flex;
      gap: 12px;
      align-items: center;
    }
  `,
})
export class EventStorePage {
  private readonly api = inject(OrdersApi);

  protected readonly eventTypes = [
    EVENT_TYPES.orderPlaced,
    EVENT_TYPES.orderConfirmed,
    EVENT_TYPES.orderRejected,
    EVENT_TYPES.orderCancelled,
  ];
  protected readonly eventType = signal('');
  protected readonly streamId = signal('');
  protected readonly pendingOnly = signal(false);
  protected readonly page = signal(0);

  protected readonly query = computed<FilterQuery>(() => {
    const all: Condition[] = [];
    if (this.eventType()) {
      all.push({ field: 'eventType', operator: 'eq', value: this.eventType() });
    }
    if (this.streamId()) {
      all.push({ field: 'streamId', operator: 'eq', value: this.streamId() });
    }
    if (this.pendingOnly()) {
      all.push({ field: 'publishedAt', operator: 'isnull' });
    }
    return {
      all,
      anyOf: [],
      sort: [{ field: 'globalPosition', direction: 'desc' }],
      page: this.page(),
      size: PAGE_SIZE,
    };
  });
  protected readonly readableQuery = computed(() =>
    decodeURIComponent(toQueryParams(this.query())),
  );

  protected readonly events = rxResource({
    params: () => this.query(),
    stream: ({ params }) => this.api.events(params),
  });

  protected readonly eventsPage = computed(() => valueOf(this.events));

  protected readonly problem = computed(() => {
    const error = this.events.error();
    return error ? toProblem(error) : null;
  });

  protected shortType(eventType: string): string {
    return eventType.replace(/^shop\./, '');
  }
}
