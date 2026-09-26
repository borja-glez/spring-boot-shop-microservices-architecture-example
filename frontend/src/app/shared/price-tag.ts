import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const formatters = new Map<string, Intl.NumberFormat>();

function format(amount: number, currency: string): string {
  let formatter = formatters.get(currency);
  if (!formatter) {
    formatter = new Intl.NumberFormat('en', { style: 'currency', currency });
    formatters.set(currency, formatter);
  }
  return formatter.format(amount);
}

/** A market-stall price label: yellow card, punched hole, condensed figures. */
@Component({
  selector: 'app-price-tag',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="tag" [class.large]="size() === 'large'">{{ text() }}</span>`,
  styles: `
    .tag {
      position: relative;
      display: inline-block;
      padding: 2px 10px 2px 22px;
      background: var(--tag);
      border: 1px solid var(--tag-edge);
      border-radius: 3px 10px 10px 3px;
      font-family: var(--font-display);
      font-weight: 700;
      font-size: var(--step-1);
      font-variant-numeric: tabular-nums;
      line-height: 1.3;
      color: var(--ink);
      transform: rotate(-2deg);
    }
    .tag::before {
      content: '';
      position: absolute;
      left: 8px;
      top: 50%;
      width: 7px;
      height: 7px;
      margin-top: -3.5px;
      border-radius: 50%;
      background: var(--tile);
      box-shadow: inset 0 0 0 1px var(--tag-edge);
    }
    .tag.large {
      font-size: var(--step-3);
      padding: 4px 18px 4px 32px;
    }
    .tag.large::before {
      left: 12px;
      width: 10px;
      height: 10px;
      margin-top: -5px;
    }
  `,
})
export class PriceTag {
  readonly amount = input.required<number>();
  readonly currency = input('EUR');
  readonly size = input<'normal' | 'large'>('normal');

  protected readonly text = computed(() => format(this.amount(), this.currency()));
}
