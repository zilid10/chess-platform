import axios from 'axios';

export function apiErrorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError<{ error?: string; message?: string }>(err)) {
    return err.response?.data?.error || err.response?.data?.message || fallback;
  }
  return fallback;
}
