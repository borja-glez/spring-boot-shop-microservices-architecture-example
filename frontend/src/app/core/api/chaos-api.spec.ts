import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ChaosApi, ChaosFault } from './chaos-api';

describe('ChaosApi', () => {
  let http: HttpTestingController;
  let api: ChaosApi;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(ChaosApi);
  });

  afterEach(() => http.verify());

  it('reads a switched-off chaos endpoint as no faults', () => {
    let result: ChaosFault[] | null | undefined;
    api.faults('payments').subscribe((faults) => (result = faults));

    http
      .expectOne('/api/payments/chaos')
      .flush({ status: 404 }, { status: 404, statusText: 'Not Found' });

    expect(result).toBeNull();
  });

  it('keeps other errors as errors', () => {
    let failed = false;
    api.faults('orders').subscribe({ error: () => (failed = true) });

    http.expectOne('/api/orders/chaos').flush({}, { status: 503, statusText: 'Unavailable' });

    expect(failed).toBe(true);
  });

  it('sets a fault by key', () => {
    api.set('payments', 'AuthorizePayment.fail', 1).subscribe();

    const request = http.expectOne('/api/payments/chaos/AuthorizePayment.fail');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ value: 1 });
    request.flush({ key: 'AuthorizePayment.fail', description: '', kind: 'TOGGLE', value: 1 });
  });

  it('turns every fault of a service off', () => {
    api.reset('inventory').subscribe();

    expect(http.expectOne('/api/inventory/chaos').request.method).toBe('DELETE');
  });
});
