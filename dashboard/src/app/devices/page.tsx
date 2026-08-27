'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import Navbar from '@/components/Navbar';
import { useAuth } from '@/hooks/useAuth';
import { useSocket } from '@/hooks/useSocket';
import { api, Device } from '@/lib/api';

export default function DevicesPage() {
  const ready = useAuth();
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const { devices: list } = await api.getDevices();
      setDevices(list);
      setError('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (ready) load();
  }, [ready, load]);

  useSocket((event, data) => {
    const payload = data as Record<string, unknown>;
    const deviceId = payload.deviceId as string;
    setDevices((prev) =>
      prev.map((d) => {
        if (d.deviceId !== deviceId) return d;
        if (event === 'device:location') {
          return {
            ...d,
            lastLatitude: payload.latitude as number,
            lastLongitude: payload.longitude as number,
            lastAddress: (payload.address as string) || d.lastAddress,
            isOnline: true,
            lastSeen: new Date().toISOString(),
          };
        }
        return {
          ...d,
          isOnline: event === 'device:offline' ? false : event === 'device:online' ? true : d.isOnline,
          batteryPercentage: (payload.batteryPercentage as number) ?? d.batteryPercentage,
          isCharging: (payload.isCharging as boolean) ?? d.isCharging,
          lastSeen: new Date().toISOString(),
        };
      })
    );
  });

  if (!ready) return null;

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <Navbar />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <h1 className="text-2xl font-bold mb-8">Devices</h1>

        {loading && <p className="text-gray-500">Loading...</p>}
        {error && <p className="text-red-600">{error}</p>}

        {!loading && devices.length === 0 && (
          <div className="text-center py-16 text-gray-500">No devices found</div>
        )}

        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {devices.map((device) => (
            <Link
              key={device.deviceId}
              href={`/devices/${device.deviceId}`}
              className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6 hover:shadow-md transition-shadow"
            >
              <div className="flex items-start justify-between mb-4">
                <h2 className="font-semibold text-lg">{device.deviceName}</h2>
                <span
                  className={`text-xs px-2 py-1 rounded-full ${
                    device.isOnline
                      ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                      : 'bg-gray-100 text-gray-500 dark:bg-gray-700 dark:text-gray-400'
                  }`}
                >
                  {device.isOnline ? 'Online' : 'Offline'}
                </span>
              </div>
              <dl className="space-y-2 text-sm">
                <div className="flex justify-between gap-4">
                  <dt className="text-gray-500 shrink-0">Location</dt>
                  <dd className="text-right">
                    {device.lastAddress ? (
                      <span className="line-clamp-2">{device.lastAddress}</span>
                    ) : device.lastLatitude && device.lastLongitude ? (
                      <span>{device.lastLatitude.toFixed(4)}, {device.lastLongitude.toFixed(4)}</span>
                    ) : (
                      '—'
                    )}
                  </dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-gray-500">Model</dt>
                  <dd>{device.manufacturer} {device.model}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-gray-500">Android</dt>
                  <dd>{device.androidVersion}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-gray-500">Battery</dt>
                  <dd>
                    {device.batteryPercentage !== null
                      ? `${device.batteryPercentage}% ${device.isCharging ? '(charging)' : ''}`
                      : '—'}
                  </dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-gray-500">Network</dt>
                  <dd>{device.networkType}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-gray-500">Last seen</dt>
                  <dd>{new Date(device.lastSeen).toLocaleString()}</dd>
                </div>
              </dl>
            </Link>
          ))}
        </div>
      </main>
    </div>
  );
}
