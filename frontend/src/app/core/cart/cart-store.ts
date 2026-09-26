import { Injectable, computed, inject, signal } from '@angular/core';

import { ProductCard } from '../api/models';
import { OrderItem } from '../api/order-models';
import { UserStore } from '../user/user-store';

/** What the cart remembers of a product. Prices are indicative: the order uses the catalog's. */
export interface CartProduct {
  productId: string;
  slug: string;
  sku: string;
  name: string;
  price: number;
  currency: string;
}

export interface CartLine extends CartProduct {
  quantity: number;
}

/** What the cart keeps of a catalog product. */
export function toCartProduct(
  product: Pick<ProductCard, 'id' | 'slug' | 'sku' | 'name' | 'price' | 'currency'>,
): CartProduct {
  const { id, slug, sku, name, price, currency } = product;
  return { productId: id, slug, sku, name, price, currency };
}

/** The same limits the orders service enforces. */
export const MAX_LINES = 20;
export const MAX_QUANTITY = 99;

const STORAGE_PREFIX = 'shop.cart.';

type Carts = Record<string, CartLine[]>;

/**
 * One cart per demo user, kept in local storage so it survives reloads. Switching user switches
 * cart, the way two people sharing a computer would expect.
 */
@Injectable({ providedIn: 'root' })
export class CartStore {
  private readonly users = inject(UserStore);
  private readonly carts = signal<Carts>({});

  readonly lines = computed(() => this.cartOf(this.users.current().id));
  readonly count = computed(() => this.lines().reduce((sum, line) => sum + line.quantity, 0));
  readonly total = computed(() =>
    this.lines().reduce((sum, line) => sum + line.price * line.quantity, 0),
  );
  readonly currency = computed(() => this.lines()[0]?.currency ?? 'EUR');
  readonly isFull = computed(() => this.lines().length >= MAX_LINES);
  readonly items = computed<OrderItem[]>(() =>
    this.lines().map(({ productId, quantity }) => ({ productId, quantity })),
  );

  /** Adds units of a product. Returns false when the cart cannot take another product. */
  add(product: CartProduct, quantity = 1): boolean {
    const lines = this.lines();
    const existing = lines.find((line) => line.productId === product.productId);
    if (existing) {
      this.setQuantity(product.productId, existing.quantity + quantity);
      return true;
    }
    if (lines.length >= MAX_LINES) {
      return false;
    }
    this.save([...lines, { ...product, quantity: clamp(quantity) }]);
    return true;
  }

  setQuantity(productId: string, quantity: number): void {
    if (quantity < 1) {
      this.remove(productId);
      return;
    }
    this.save(
      this.lines().map((line) =>
        line.productId === productId ? { ...line, quantity: clamp(quantity) } : line,
      ),
    );
  }

  remove(productId: string): void {
    this.save(this.lines().filter((line) => line.productId !== productId));
  }

  clear(): void {
    this.save([]);
  }

  quantityOf(productId: string): number {
    return this.lines().find((line) => line.productId === productId)?.quantity ?? 0;
  }

  /** The cart changed in this visit, or else the one stored by a previous visit. */
  private cartOf(userId: string): CartLine[] {
    return this.carts()[userId] ?? readStored(userId);
  }

  private save(lines: CartLine[]): void {
    const userId = this.users.current().id;
    this.carts.update((carts) => ({ ...carts, [userId]: lines }));
    try {
      localStorage.setItem(STORAGE_PREFIX + userId, JSON.stringify(lines));
    } catch {
      // Storage can be unavailable (private mode); the cart then lasts for this visit only.
    }
  }
}

function clamp(quantity: number): number {
  return Math.min(MAX_QUANTITY, Math.max(1, Math.trunc(quantity)));
}

function readStored(userId: string): CartLine[] {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + userId);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter(isCartLine).slice(0, MAX_LINES) : [];
  } catch {
    return [];
  }
}

function isCartLine(value: unknown): value is CartLine {
  const line = value as Partial<CartLine> | null;
  return (
    typeof line?.productId === 'string' &&
    typeof line.name === 'string' &&
    typeof line.price === 'number' &&
    typeof line.quantity === 'number' &&
    line.quantity >= 1
  );
}
