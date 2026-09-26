import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';

import { CartProduct, CartStore } from '../core/cart/cart-store';

/** "Add to cart" with the units already in it, so shoppers see the click did something. */
@Component({
  selector: 'app-add-to-cart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="button" [disabled]="refused()" (click)="add()">
      @if (inCart()) {
        Add another · {{ inCart() }} in cart
      } @else {
        Add to cart
      }
    </button>
    @if (refused()) {
      <p class="full" role="status">
        Your cart already holds the maximum number of different products.
      </p>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .full {
      margin: 0;
      font-size: var(--step--1);
      color: var(--amber);
    }
  `,
})
export class AddToCart {
  private readonly cart = inject(CartStore);

  readonly product = input.required<CartProduct>();

  protected readonly inCart = computed(() => this.cart.quantityOf(this.product().productId));
  private readonly lastRefused = signal<string | null>(null);
  protected readonly refused = computed(
    () => this.lastRefused() === this.product().productId && this.cart.isFull() && !this.inCart(),
  );

  protected add(): void {
    this.lastRefused.set(this.cart.add(this.product()) ? null : this.product().productId);
  }
}
