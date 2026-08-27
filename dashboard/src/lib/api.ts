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

import { socketRequest } from './socketManager';

export const api = {
  login: async (email: string, password: string) => {
    const data = await socketRequest<{
      ok: boolean;
      token: string;
      user: User;
    }>('auth:login', { email, password }, { guest: true });
    return { token: data.token, user: data.user };
  },

  register: async (email: string, password: string, name: string) => {
    const data = await socketRequest<{
      ok: boolean;
      message: string;
      email: string;
    }>('auth:register', { email, password, name }, { guest: true });
    return { message: data.message, email: data.email };
  },

  verifyOtp: async (email: string, otp: string) => {
    const data = await socketRequest<{
      ok: boolean;
      token: string;
      user: User;
      message: string;
    }>('auth:verify-otp', { email, otp }, { guest: true });
    return { token: data.token, user: data.user, message: data.message };
  },

  resendOtp: async (email: string) => {
    const data = await socketRequest<{
      ok: boolean;
      message: string;
      email: string;
    }>('auth:resend-otp', { email }, { guest: true });
    return { message: data.message, email: data.email };
  },

  getDevices: async () => {
    const data = await socketRequest<{ ok: boolean; devices: Device[] }>('dashboard:getDevices');
    return { devices: data.devices };
  },

  getDevice: async (deviceId: string) => {
    const data = await socketRequest<{ ok: boolean; device: Device }>('dashboard:getDevice', {
      deviceId,
    });
    return { device: data.device };
  },

  getLocationHistory: async (deviceId: string, date?: string, page = 1) => {
    const data = await socketRequest<{
      ok: boolean;
      locations: LocationPoint[];
      pagination: { page: number; limit: number; total: number; pages: number };
      summary: { pointCount: number; startTime: string | null; endTime: string | null };
    }>('dashboard:getLocationHistory', { deviceId, date, page });
    return {
      locations: data.locations,
      pagination: data.pagination,
      summary: data.summary,
    };
  },
};
