import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { FacetValue } from '../../core/api/models';

/** One refinement block (categories, sellers, tags) with counts next to each option. */
@Component({
  selector: 'app-facet-group',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <fieldset>
      <legend>{{ title() }}</legend>
      @for (option of values(); track option.value) {
        <label class="option">
          <input
            [type]="multiple() ? 'checkbox' : 'radio'"
            [name]="title()"
            [checked]="selected().includes(option.value)"
            (change)="toggled.emit(option.value)"
          />
          <span class="label">{{ option.label }}</span>
          <span class="count">{{ option.count }}</span>
        </label>
      } @empty {
        <p class="empty">Nothing to refine with the current filters.</p>
      }
    </fieldset>
  `,
  styles: `
    fieldset {
      border: 0;
      margin: 0;
      padding: 0;
    }
    legend {
      font-family: var(--font-display);
      font-weight: 700;
      font-size: var(--step-1);
      margin-bottom: 6px;
    }
    .option {
      display: grid;
      grid-template-columns: auto 1fr auto;
      align-items: center;
      gap: 8px;
      padding: 3px 0;
      cursor: pointer;
    }
    .count {
      font-variant-numeric: tabular-nums;
      color: var(--ink-soft);
      font-size: var(--step--1);
    }
    .empty {
      color: var(--ink-soft);
      font-size: var(--step--1);
      margin: 0;
    }
  `,
})
export class FacetGroup {
  readonly title = input.required<string>();
  readonly values = input.required<FacetValue[]>();
  readonly selected = input<string[]>([]);
  readonly multiple = input(true);
  readonly toggled = output<string>();
}
