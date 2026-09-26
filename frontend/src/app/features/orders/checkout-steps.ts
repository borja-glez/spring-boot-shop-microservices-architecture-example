import {
  CheckoutStepName,
  CheckoutView,
  REJECTION_LABELS,
  StepOutcome,
} from '../../core/api/order-models';

/** A line of the checkout timeline. */
export interface CheckoutLine {
  title: string;
  outcome: StepOutcome;
  outcomeLabel: string;
  detail: string | null;
  at: string;
  /** Compensation steps undo an earlier one. */
  compensation: boolean;
}

/** What the saga is doing now, in words, and whether it still moves. */
export interface CheckoutSummary {
  headline: string;
  settled: boolean;
  tone: 'progress' | 'success' | 'failure' | 'alert';
  reason: string | null;
  lines: CheckoutLine[];
}

const STEP_TITLES: Record<CheckoutStepName, string> = {
  RESERVE_STOCK: 'Reserve stock',
  AUTHORIZE_PAYMENT: 'Authorize payment',
  CONFIRM_ORDER: 'Confirm order',
  REFUND_PAYMENT: 'Refund or void payment',
  RELEASE_STOCK: 'Release stock',
  REJECT_ORDER: 'Reject order',
  DONE: 'Done',
};

const OUTCOME_LABELS: Record<StepOutcome, string> = {
  SUCCEEDED: 'Done',
  DECLINED: 'Declined',
  RETRYING: 'Failed; retrying',
  GAVE_UP: 'Gave up after retries',
};

const COMPENSATIONS: readonly CheckoutStepName[] = ['REFUND_PAYMENT', 'RELEASE_STOCK'];

/** Turns the saga into the lines and the headline the order page shows. */
export function toCheckoutSummary(checkout: CheckoutView): CheckoutSummary {
  const lines = checkout.steps.map((s) => ({
    title: STEP_TITLES[s.step],
    outcome: s.outcome,
    outcomeLabel: OUTCOME_LABELS[s.outcome],
    detail: s.outcome === 'DECLINED' ? (REJECTION_LABELS[s.detail ?? ''] ?? s.detail) : s.detail,
    at: s.at,
    compensation: COMPENSATIONS.includes(s.step),
  }));
  const reason = checkout.rejectionReason
    ? (REJECTION_LABELS[checkout.rejectionReason] ?? checkout.rejectionReason)
    : null;
  return { ...headline(checkout), reason, lines };
}

function headline(checkout: CheckoutView): Pick<CheckoutSummary, 'headline' | 'settled' | 'tone'> {
  if (checkout.state === 'STUCK') {
    return {
      headline: `“${STEP_TITLES[checkout.step]}” keeps failing; still retrying, but needs attention`,
      settled: false,
      tone: 'alert',
    };
  }
  if (checkout.state === 'RUNNING') {
    const retry = checkout.attempts > 0 ? ` (attempt ${checkout.attempts + 1})` : '';
    return {
      headline: `${STEP_TITLES[checkout.step]}…${retry}`,
      settled: false,
      tone: 'progress',
    };
  }
  switch (checkout.mode) {
    case 'CHECKOUT':
      return { headline: 'Checkout completed', settled: true, tone: 'success' };
    case 'REJECTING':
      return { headline: 'Checkout rejected and rolled back', settled: true, tone: 'failure' };
    case 'CANCELLING':
      return { headline: 'Payment refunded and stock released', settled: true, tone: 'success' };
  }
}
