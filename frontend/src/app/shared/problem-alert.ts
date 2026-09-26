import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { ApiProblem } from '../core/api/problem';

/** Explains a failed request: what happened, what to do, and the id to quote. */
@Component({
  selector: 'app-problem-alert',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="alert" role="alert" [class.server]="problem().status >= 500">
      <p class="title">{{ problem().title }}</p>
      <p>{{ problem().detail }}</p>
      @if (problem().errors?.length) {
        <ul>
          @for (error of problem().errors; track error.field) {
            <li>
              <strong>{{ error.field }}</strong
              >: {{ error.message }}
            </li>
          }
        </ul>
      }
      <p class="meta">
        Code <code>{{ problem().code }}</code>
        @if (problem().correlationId) {
          · correlation <code>{{ problem().correlationId }}</code>
        }
      </p>
    </div>
  `,
  styles: `
    .alert {
      border: 1px solid var(--amber);
      background: var(--amber-wash);
      border-radius: var(--radius-m);
      padding: 12px 16px;
    }
    .alert.server {
      border-color: var(--tomato);
      background: var(--tomato-wash);
    }
    p {
      margin: 0 0 4px;
    }
    .title {
      font-weight: 600;
    }
    .meta {
      color: var(--ink-soft);
      font-size: var(--step--1);
      margin: 8px 0 0;
    }
    ul {
      margin: 4px 0;
      padding-left: 20px;
    }
  `,
})
export class ProblemAlert {
  readonly problem = input.required<ApiProblem>();
}
