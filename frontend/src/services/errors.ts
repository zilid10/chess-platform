import axios from 'axios';

export function apiErrorMessage(err: unknown, fallback: string): string {
  if (!axios.isAxiosError(err)) return fallback;

  // Error bodies can also be empty, HTML from a proxy, or an older API payload.
  const data: unknown = err.response?.data;
  if (typeof data !== 'object' || data === null) return fallback;

  const body = data as Record<string, unknown>;
  for (const key of ['detail', 'error', 'message', 'title']) {
    const value = body[key];
    if (typeof value === 'string' && value.trim()) return value;
  }
  return fallback;
}
