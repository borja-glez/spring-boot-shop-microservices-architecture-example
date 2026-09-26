import { describe, expect, it } from 'vitest';

import { CheckoutView } from '../../core/api/order-models';
import { toCheckoutSummary } from './checkout-steps';

const base: CheckoutView = {
  orderId: 'o-1',
  state: 'RUNNING',
  mode: 'CHECKOUT',
  step: 'RESERVE_STOCK',
  attempts: 0,
  nextAttemptAt: '2026-09-25T10:00:00Z',
  lastError: null,
  rejectionReason: null,
  rejectionDetail: null,
  steps: [],
};

describe('toCheckoutSummary', () => {
  it('shows the step in progress and the retry count', () => {
    expect(toCheckoutSummary(base).headline).toBe('Reserve stock…');
    const retrying = toCheckoutSummary({ ...base, step: 'AUTHORIZE_PAYMENT', attempts: 2 });
    expect(retrying.headline).toBe('Authorize payment… (attempt 3)');
    expect(retrying.settled).toBe(false);
    expect(retrying.tone).toBe('progress');
  });

  it('explains a rejected checkout and marks the compensations', () => {
    const summary = toCheckoutSummary({
      ...base,
      state: 'COMPLETED',
      mode: 'REJECTING',
      step: 'DONE',
      rejectionReason: 'card-limit-exceeded',
      steps: [
        { step: 'RESERVE_STOCK', outcome: 'SUCCEEDED', detail: null, at: 't1' },
        {
          step: 'AUTHORIZE_PAYMENT',
          outcome: 'DECLINED',
          detail: 'card-limit-exceeded',
          at: 't2',
        },
        { step: 'RELEASE_STOCK', outcome: 'SUCCEEDED', detail: null, at: 't3' },
        { step: 'REJECT_ORDER', outcome: 'SUCCEEDED', detail: null, at: 't4' },
      ],
    });

    expect(summary.headline).toBe('Checkout rejected and rolled back');
    expect(summary.tone).toBe('failure');
    expect(summary.reason).toBe('The amount exceeds the card limit');
    expect(summary.lines.map((l) => l.compensation)).toEqual([false, false, true, false]);
    expect(summary.lines[1].detail).toBe('The amount exceeds the card limit');
    expect(summary.lines[1].outcomeLabel).toBe('Declined');
  });

  it('keeps technical details and unknown codes as they come', () => {
    const summary = toCheckoutSummary({
      ...base,
      state: 'COMPLETED',
      mode: 'REJECTING',
      step: 'DONE',
      rejectionReason: 'something-new',
      steps: [{ step: 'RESERVE_STOCK', outcome: 'RETRYING', detail: 'timeout', at: 't1' }],
    });

    expect(summary.reason).toBe('something-new');
    expect(summary.lines[0].detail).toBe('timeout');
  });

  it('describes completed, cancelled and stuck sagas', () => {
    expect(toCheckoutSummary({ ...base, state: 'COMPLETED', step: 'DONE' })).toMatchObject({
      headline: 'Checkout completed',
      settled: true,
      tone: 'success',
    });
    expect(
      toCheckoutSummary({ ...base, state: 'COMPLETED', mode: 'CANCELLING', step: 'DONE' }).headline,
    ).toBe('Payment refunded and stock released');
    expect(toCheckoutSummary({ ...base, state: 'STUCK', step: 'RELEASE_STOCK' })).toMatchObject({
      headline: '“Release stock” keeps failing; still retrying, but needs attention',
      settled: false,
      tone: 'alert',
    });
  });
});
