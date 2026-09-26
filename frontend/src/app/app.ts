import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { CartStore } from './core/cart/cart-store';
import { NotificationBell } from './shared/notification-bell';
import { UserSwitcher } from './shared/user-switcher';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, UserSwitcher, NotificationBell],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly cart = inject(CartStore);
}
