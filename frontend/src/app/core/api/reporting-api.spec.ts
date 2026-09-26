import { describe, expect, it } from 'vitest';

import { toQueryParams } from '../filters/filter-serializer';
import { dayRange, lineDayRange } from './reporting-api';

describe('report day ranges', () => {
  it('filter on the placement day, inclusive', () => {
    expect(decodeURIComponent(toQueryParams(dayRange('2026-09-01', '2026-09-30')))).toBe(
      'filter=placedDay:gte:2026-09-01&filter=placedDay:lte:2026-09-30',
    );
  });

  it('leave out the ends that are empty', () => {
    expect(dayRange('', '').all).toEqual([]);
    expect(dayRange('2026-09-01', '').all).toHaveLength(1);
  });

  it('go through the order for the product report', () => {
    expect(lineDayRange('2026-09-01', '').all[0].field).toBe('order.placedDay');
  });
});
