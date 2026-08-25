export const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:3001/api';
export const SOCKET_URL = process.env.NEXT_PUBLIC_SOCKET_URL || 'http://localhost:3001';

export interface User {
  id: string;
  email: string;
  name: string;
}

export interface Device {
  _id: string;
  deviceId: string;
  deviceName: string;
  androidVersion: string;
  manufacturer: string;
  model: string;
  appVersion: string;
  isOnline: boolean;
  lastSeen: string;
  batteryPercentage: number | null;
  isCharging: boolean;
  batteryTemperature: number | null;
  batteryHealth: string | null;
  networkType: string;
  wifiAvailable: boolean;
  mobileDataAvailable: boolean;
  lastLatitude: number | null;
  lastLongitude: number | null;
  lastAccuracy: number | null;
  lastAltitude: number | null;
  lastSpeed: number | null;
  lastLocationTimestamp: string | null;
  trackingEnabled: boolean;
  updateIntervalSeconds: number;
}

export interface LocationPoint {
  _id: string;
  latitude: number;
  longitude: number;
  accuracy: number | null;
  altitude: number | null;
  speed: number | null;
  timestamp: string;
}

export function getToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem('token');
}

export function setToken(token: string) {
  localStorage.setItem('token', token);
}

export function clearAuth() {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
}

export function getUser(): User | null {
  if (typeof window === 'undefined') return null;
  const raw = localStorage.getItem('user');
  return raw ? JSON.parse(raw) : null;
}

export function setUser(user: User) {
  localStorage.setItem('user', JSON.stringify(user));
}

async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(`${API_URL}${path}`, { ...options, headers });
  const data = await res.json().catch(() => ({}));

  if (!res.ok) {
    throw new Error(data.error || `Request failed (${res.status})`);
  }
  return data as T;
}

export const api = {
  login: (email: string, password: string) =>
    apiRequest<{ token: string; user: User }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    }),

  register: (email: string, password: string, name: string) =>
    apiRequest<{ message: string; email: string }>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password, name }),
    }),

  verifyOtp: (email: string, otp: string) =>
    apiRequest<{ token: string; user: User; message: string }>('/auth/verify-otp', {
      method: 'POST',
      body: JSON.stringify({ email, otp }),
    }),

  resendOtp: (email: string) =>
    apiRequest<{ message: string; email: string }>('/auth/resend-otp', {
      method: 'POST',
      body: JSON.stringify({ email }),
    }),

  getDevices: () => apiRequest<{ devices: Device[] }>('/devices'),

  getDevice: (deviceId: string) =>
    apiRequest<{ device: Device }>(`/devices/${deviceId}`),

  getLocationHistory: (deviceId: string, date?: string, page = 1) => {
    const params = new URLSearchParams({ page: String(page) });
    if (date) params.set('date', date);
    return apiRequest<{
      locations: LocationPoint[];
      pagination: { page: number; limit: number; total: number; pages: number };
      summary: { pointCount: number; startTime: string | null; endTime: string | null };
    }>(`/devices/${deviceId}/location/history?${params}`);
  },
};
