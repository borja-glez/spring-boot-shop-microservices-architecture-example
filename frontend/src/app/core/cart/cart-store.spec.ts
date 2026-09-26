import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { UserStore } from '../user/user-store';
import { CartProduct, CartStore, MAX_LINES, MAX_QUANTITY } from './cart-store';

function product(n: number, price = 2.5): CartProduct {
  return {
    productId: `p-${n}`,
    slug: `producto-${n}`,
    sku: `SKU-${n}`,
    name: `Producto ${n}`,
    price,
    currency: 'EUR',
  };
}

describe('CartStore', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('adds products and sums their units and prices', () => {
    const cart = TestBed.inject(CartStore);

    cart.add(product(1, 2.5), 2);
    cart.add(product(2, 4));
    cart.add(product(1, 2.5));

    expect(cart.lines().map((l) => [l.productId, l.quantity])).toEqual([
      ['p-1', 3],
      ['p-2', 1],
    ]);
    expect(cart.count()).toBe(4);
    expect(cart.total()).toBe(11.5);
    expect(cart.items()).toEqual([
      { productId: 'p-1', quantity: 3 },
      { productId: 'p-2', quantity: 1 },
    ]);
  });

  it('keeps quantities within what the orders service accepts', () => {
    const cart = TestBed.inject(CartStore);
    cart.add(product(1));

    cart.setQuantity('p-1', 500);
    expect(cart.quantityOf('p-1')).toBe(MAX_QUANTITY);

    cart.setQuantity('p-1', 0);
    expect(cart.lines()).toEqual([]);
  });

  it('refuses a product beyond the line limit', () => {
    const cart = TestBed.inject(CartStore);
    for (let n = 0; n < MAX_LINES; n++) {
      expect(cart.add(product(n))).toBe(true);
    }

    expect(cart.isFull()).toBe(true);
    expect(cart.add(product(99))).toBe(false);
    expect(cart.add(product(0))).toBe(true);
    expect(cart.lines()).toHaveLength(MAX_LINES);
  });

  it('keeps one cart per user and remembers it across reloads', () => {
    const users = TestBed.inject(UserStore);
    const cart = TestBed.inject(CartStore);
    users.select('cliente-lucia');
    cart.add(product(1));
    users.select('cliente-mateo');

    expect(cart.lines()).toEqual([]);
    cart.add(product(2), 3);

    TestBed.resetTestingModule();
    const reloaded = TestBed.inject(CartStore);
    expect(reloaded.lines().map((l) => l.productId)).toEqual(['p-2']);
    TestBed.inject(UserStore).select('cliente-lucia');
    expect(reloaded.lines().map((l) => l.productId)).toEqual(['p-1']);
  });

  it('ignores a corrupted stored cart', () => {
    localStorage.setItem('shop.user', 'cliente-lucia');
    localStorage.setItem('shop.cart.cliente-lucia', '[{"productId": 1}, "x"');

    expect(TestBed.inject(CartStore).lines()).toEqual([]);
  });

  it('empties the cart after an order', () => {
    const cart = TestBed.inject(CartStore);
    cart.add(product(1));

    cart.clear();

    expect(cart.count()).toBe(0);
    expect(localStorage.getItem('shop.cart.cliente-lucia')).toBe('[]');
  });
});
