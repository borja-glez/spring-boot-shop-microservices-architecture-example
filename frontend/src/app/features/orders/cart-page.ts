import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { QuoteLine } from '../../core/api/order-models';
import { OrdersApi } from '../../core/api/orders-api';
import { ApiProblem, toProblem } from '../../core/api/problem';
import { CartStore, MAX_QUANTITY } from '../../core/cart/cart-store';
import { UserStore } from '../../core/user/user-store';
import { PriceTag } from '../../shared/price-tag';
import { ProblemAlert } from '../../shared/problem-alert';
import { valueOf } from '../../shared/resource-value';

/** The cart and the checkout: review, adjust quantities and place the order. */
@Component({
  selector: 'app-cart-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PriceTag, ProblemAlert],
  template: `
    <h1>Cart</h1>
    @if (problem(); as problem) {
      <app-problem-alert [problem]="problem" />
    }
    @if (cart.lines().length === 0) {
      <p class="panel empty">
        {{ users.current().name }}’s cart is empty.
        <a routerLink="/">Go to the shop</a>
      </p>
    } @else {
      <section class="panel" aria-label="Products in the cart">
        <table>
          <thead>
            <tr>
              <th scope="col">Product</th>
              <th scope="col" class="num">Price</th>
              <th scope="col" class="num">Quantity</th>
              <th scope="col" class="num">Subtotal</th>
              <th scope="col"><span class="visually-hidden">Remove</span></th>
            </tr>
          </thead>
          <tbody>
            @for (line of cart.lines(); track line.productId) {
              <tr>
                <td>
                  <a [routerLink]="['/products', line.slug]">{{ line.name }}</a>
                  <code>{{ line.sku }}</code>
                  @if (quoted().get(line.productId); as q) {
                    @if (q.problem === 'NOT_FOR_SALE') {
                      <span class="warning">No longer for sale: remove it to order</span>
                    } @else if (q.problem === 'NOT_ENOUGH_STOCK') {
                      <span class="warning">Only {{ q.available }} available</span>
                    }
                    @if (q.unitPrice !== null && q.unitPrice !== line.price) {
                      <span class="warning"
                        >Price is now {{ money(q.unitPrice, line.currency) }}</span
                      >
                    }
                  }
                </td>
                <td class="num">{{ money(line.price, line.currency) }}</td>
                <td class="num">
                  <label class="visually-hidden" [for]="'qty-' + line.productId"
                    >Quantity of {{ line.name }}</label
                  >
                  <input
                    class="field qty"
                    type="number"
                    min="1"
                    [max]="maxQuantity"
                    [id]="'qty-' + line.productId"
                    [value]="line.quantity"
                    (change)="cart.setQuantity(line.productId, +$any($event.target).value)"
                  />
                </td>
                <td class="num">{{ money(line.price * line.quantity, line.currency) }}</td>
                <td>
                  <button
                    type="button"
                    class="button secondary"
                    (click)="cart.remove(line.productId)"
                  >
                    Remove
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
        <div class="summary">
          @if (quote(); as q) {
            <p>
              Total now
              <app-price-tag
                [amount]="q.total"
                [currency]="q.currency ?? cart.currency()"
                size="large"
              />
            </p>
            @if (!q.stockChecked) {
              <p class="warning" role="status">
                The inventory did not answer in time, so the stock was not checked. The checkout
                will reserve it.
              </p>
            }
          } @else {
            <p>
              Estimated total
              <app-price-tag [amount]="cart.total()" [currency]="cart.currency()" size="large" />
            </p>
          }
          <p class="note">
            The orders service prices the cart with its own copy of the catalog, kept up to date
            through Kafka, and asks the inventory service for the stock over RabbitMQ, request and
            reply. Nothing is reserved until you place the order.
          </p>
          <button
            type="button"
            class="button"
            [disabled]="placing() || quote()?.orderable === false"
            (click)="placeOrder()"
          >
            {{ placing() ? 'Placing order…' : 'Place order' }}
          </button>
        </div>
      </section>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
      max-width: 900px;
    }
    h1 {
      margin: 0;
    }
    .empty {
      padding: 24px;
      margin: 0;
    }
    section {
      padding: 16px;
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    th,
    td {
      padding: 8px;
      border-bottom: 1px solid var(--line);
      text-align: left;
      vertical-align: middle;
    }
    td code {
      display: block;
      font-size: var(--step--1);
      color: var(--ink-soft);
    }
    .num {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .qty {
      width: 5em;
      text-align: right;
    }
    .summary {
      display: flex;
      flex-direction: column;
      align-items: end;
      gap: 12px;
      margin-top: 16px;
    }
    .summary p {
      margin: 0;
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .warning {
      display: block;
      font-size: var(--step--1);
      color: var(--amber);
    }
    .summary .warning {
      max-width: 60ch;
      text-align: right;
    }
    .note {
      max-width: 60ch;
      font-size: var(--step--1);
      color: var(--ink-soft);
      text-align: right;
    }
  `,
})
export class CartPage {
  private readonly api = inject(OrdersApi);
  private readonly router = inject(Router);
  protected readonly cart = inject(CartStore);
  protected readonly users = inject(UserStore);

  protected readonly maxQuantity = MAX_QUANTITY;
  protected readonly placing = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);

  /** Checked again whenever the cart changes; the previous request is dropped. */
  private readonly quoteResource = rxResource({
    params: () => {
      const items = this.cart.items();
      return items.length ? { items } : undefined;
    },
    stream: ({ params }) => this.api.quote(params.items),
  });

  protected readonly quote = computed(() => valueOf(this.quoteResource));

  protected readonly quoted = computed(
    () => new Map<string, QuoteLine>((this.quote()?.lines ?? []).map((l) => [l.productId, l])),
  );

  protected placeOrder(): void {
    this.placing.set(true);
    this.problem.set(null);
    this.api.place(this.cart.items()).subscribe({
      next: (orderId) => {
        this.cart.clear();
        void this.router.navigate(['/orders', orderId], { queryParams: { placed: 1 } });
      },
      error: (error: unknown) => {
        this.placing.set(false);
        this.problem.set(toProblem(error));
      },
    });
  }

  protected money(amount: number, currency: string): string {
    return new Intl.NumberFormat('en', { style: 'currency', currency }).format(amount);
  }
}
