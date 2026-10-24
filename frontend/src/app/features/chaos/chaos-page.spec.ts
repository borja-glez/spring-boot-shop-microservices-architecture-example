import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ChaosFault } from '../../core/api/chaos-api';
import { ChaosPage } from './chaos-page';

const DECLINE: ChaosFault = {
  key: 'payments.decline-all',
  description: 'Lowers the card limit',
  kind: 'TOGGLE',
  value: 0,
};
const PAUSED: ChaosFault = {
  key: 'relay.paused',
  description: 'Stops the relay',
  kind: 'TOGGLE',
  value: 1,
};

describe('ChaosPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ChaosPage>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChaosPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ChaosPage);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  async function answer(orders: 'down' | 'off' = 'down'): Promise<HTMLElement> {
    // A refresh after an action starts new requests on the next change detection.
    TestBed.tick();
    http.expectOne('/api/v1/chaos/payments').flush([DECLINE]);
    http.expectOne('/api/v1/chaos/inventory').flush([]);
    if (orders === 'down') {
      http
        .expectOne('/api/v1/chaos/orders')
        .flush(
          { status: 503, title: 'Service Unavailable' },
          { status: 503, statusText: 'Unavailable' },
        );
    } else {
      http
        .expectOne('/api/v1/chaos/orders')
        .flush({ status: 404 }, { status: 404, statusText: 'Not Found' });
    }
    http.expectOne('/api/v1/chaos/catalog').flush([PAUSED]);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows every service on its own, even when one of them is down', async () => {
    const element = await answer();

    const panels = Array.from(element.querySelectorAll('section.service'));
    expect(panels.map((p) => p.querySelector('h2')?.textContent)).toEqual([
      'Payments',
      'Inventory',
      'Orders',
      'Catalog',
    ]);
    expect(panels[0].textContent).toContain('Decline every card');
    expect(panels[2].querySelector('app-problem-alert')).not.toBeNull();
    expect(panels[3].querySelector('li.active')).not.toBeNull();
  });

  it('says when chaos is off in a service', async () => {
    const element = await answer('off');

    expect(element.querySelectorAll('section.service')[2].textContent).toContain(
      'Chaos is disabled in this service.',
    );
  });

  it('keeps the problem of a failed change on screen after refreshing', async () => {
    const element = await answer('off');
    const toggle = element.querySelector<HTMLInputElement>('section.service input[type=checkbox]')!;

    toggle.checked = true;
    toggle.dispatchEvent(new Event('change'));
    http.expectOne('/api/v1/chaos/payments/payments.decline-all').flush(
      {
        status: 422,
        code: 'chaos-value-out-of-range',
        title: 'Unprocessable',
        detail: 'Out of range',
      },
      { status: 422, statusText: 'Unprocessable Content' },
    );
    await answer('off');

    expect(element.querySelector(':scope > app-problem-alert')?.textContent).toContain(
      'Out of range',
    );
  });

  it('turns everything off in every service, even those that did not answer', async () => {
    const element = await answer();
    const calmDown = Array.from(
      element.querySelectorAll<HTMLButtonElement>('.actions button'),
    ).find((b) => b.textContent?.includes('Turn everything off'))!;

    calmDown.click();

    for (const service of ['payments', 'inventory', 'orders', 'catalog']) {
      const request = http.expectOne(
        (r) => r.url === `/api/v1/chaos/${service}` && r.method === 'DELETE',
      );
      if (service === 'orders') {
        request.flush({ status: 404 }, { status: 404, statusText: 'Not Found' });
      } else {
        request.flush(null, { status: 204, statusText: 'No Content' });
      }
    }
    const element2 = await answer('off');
    expect(element2.querySelector(':scope > app-problem-alert')).toBeNull();
  });
});
