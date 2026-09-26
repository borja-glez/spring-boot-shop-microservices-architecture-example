import { Injectable, computed, signal } from '@angular/core';

export interface DemoUser {
  id: string;
  name: string;
  role: 'cliente' | 'vendedor';
}

/**
 * The demo has no login. The visitor chooses who they are and the id travels in the
 * X-Shop-User header. Seller ids match the seeded catalog.
 */
export const DEMO_USERS: readonly DemoUser[] = [
  { id: 'cliente-lucia', name: 'Lucía (shopper)', role: 'cliente' },
  { id: 'cliente-mateo', name: 'Mateo (shopper)', role: 'cliente' },
  { id: 'seller-ana', name: 'Tostadores Ana', role: 'vendedor' },
  { id: 'seller-bruno', name: 'Almazara Bruno', role: 'vendedor' },
  { id: 'seller-carmen', name: 'Carmen Hogar', role: 'vendedor' },
  { id: 'seller-diego', name: 'Diego Electrónica', role: 'vendedor' },
  { id: 'seller-elena', name: 'Librería Elena', role: 'vendedor' },
  { id: 'seller-fermin', name: 'Ultramarinos Fermín', role: 'vendedor' },
];

const STORAGE_KEY = 'shop.user';

@Injectable({ providedIn: 'root' })
export class UserStore {
  private readonly currentId = signal(readStoredId());

  readonly users = DEMO_USERS;
  readonly current = computed(
    () => DEMO_USERS.find((u) => u.id === this.currentId()) ?? DEMO_USERS[0],
  );

  select(id: string): void {
    if (!DEMO_USERS.some((u) => u.id === id)) {
      return;
    }
    this.currentId.set(id);
    try {
      localStorage.setItem(STORAGE_KEY, id);
    } catch {
      // Storage can be unavailable (private mode); the choice then lasts for this visit only.
    }
  }
}

function readStoredId(): string {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return DEMO_USERS.some((u) => u.id === stored) ? (stored as string) : DEMO_USERS[0].id;
  } catch {
    return DEMO_USERS[0].id;
  }
}
