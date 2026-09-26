import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { DEMO_USERS, UserStore } from './user-store';

describe('UserStore', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('starts as the first shopper when nothing was chosen before', () => {
    const store = TestBed.inject(UserStore);

    expect(store.current().id).toBe(DEMO_USERS[0].id);
  });

  it('remembers the chosen user across reloads', () => {
    TestBed.inject(UserStore).select('seller-ana');
    TestBed.resetTestingModule();

    expect(TestBed.inject(UserStore).current().id).toBe('seller-ana');
  });

  it('ignores unknown stored ids', () => {
    localStorage.setItem('shop.user', 'intruder');

    expect(TestBed.inject(UserStore).current().id).toBe(DEMO_USERS[0].id);
  });
});
