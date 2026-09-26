import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { App } from './app';

/** jsdom has no EventSource; the notices bell opens one. */
class FakeEventSource {
  readonly listeners: string[] = [];
  closed = false;

  addEventListener(type: string): void {
    this.listeners.push(type);
  }

  close(): void {
    this.closed = true;
  }
}

describe('App', () => {
  beforeEach(async () => {
    globalThis.EventSource ??= FakeEventSource as unknown as typeof EventSource;
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  it('shows the market name and every section', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('.brand')?.textContent).toContain('Mercado');
    const links = Array.from(element.querySelectorAll('nav a')).map((a) => a.textContent?.trim());
    expect(links).toEqual([
      'Shop',
      'My orders',
      'Event store',
      'Stock',
      'Payments',
      'Reports',
      'Filter lab',
      'Chaos',
      'Cart',
    ]);
  });

  it('lets the visitor choose who they are', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const select = (fixture.nativeElement as HTMLElement).querySelector('#user-switcher');
    expect(select?.querySelectorAll('option').length).toBe(8);
  });
});
