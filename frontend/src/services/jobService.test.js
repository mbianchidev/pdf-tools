import { describe, expect, test } from 'vitest';
import { normalizeJobApiBaseUrl } from './jobService';

describe('normalizeJobApiBaseUrl', () => {
  test.each([
    ['/api/v1/', '/api/v1'],
    ['/api/v1', '/api/v1'],
    ['https://api.example.com/api/v1///', 'https://api.example.com/api/v1'],
  ])('normalizes %s', (configuredUrl, expectedUrl) => {
    expect(normalizeJobApiBaseUrl(configuredUrl)).toBe(expectedUrl);
  });
});
