import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { UserStore } from '../core/user/user-store';

/** Lets the visitor act as a shopper or as one of the seeded sellers. */
@Component({
  selector: 'app-user-switcher',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label for="user-switcher">Shopping as</label>
    <select
      id="user-switcher"
      class="field"
      [value]="store.current().id"
      (change)="store.select($any($event.target).value)"
    >
      <optgroup label="Shoppers">
        @for (user of shoppers; track user.id) {
          <option [value]="user.id" [selected]="user.id === store.current().id">
            {{ user.name }}
          </option>
        }
      </optgroup>
      <optgroup label="Sellers">
        @for (user of sellers; track user.id) {
          <option [value]="user.id" [selected]="user.id === store.current().id">
            {{ user.name }}
          </option>
        }
      </optgroup>
    </select>
  `,
  styles: `
    :host {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: var(--step--1);
      color: var(--ink-soft);
    }
  `,
})
export class UserSwitcher {
  protected readonly store = inject(UserStore);
  protected readonly shoppers = this.store.users.filter((u) => u.role === 'cliente');
  protected readonly sellers = this.store.users.filter((u) => u.role === 'vendedor');
}
