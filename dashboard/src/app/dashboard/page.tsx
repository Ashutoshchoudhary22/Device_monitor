'use client';

import { useCallback, useEffect, useState } from 'react';
import dynamic from 'next/dynamic';
import Link from 'next/link';
import Navbar from '@/components/Navbar';
import StatCard from '@/components/StatCard';
import { useAuth } from '@/hooks/useAuth';
import { useSocket } from '@/hooks/useSocket';
import { api, Device } from '@/lib/api';
import { useToast } from '@/context/ToastContext';

const MapView = dynamic(() => import('@/components/MapView'), { ssr: false });

export default function DashboardPage() {
  const ready = useAuth();
  const { showToast } = useToast();
  const [devices, setDevices] = useState<Device[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadDevices = useCallback(async () => {
    try {
      const { devices: list } = await api.getDevices();
      setDevices(list);
      setError('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load devices');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (ready) loadDevices();
  }, [ready, loadDevices]);

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
            lastAccuracy: payload.accuracy as number,
            lastLocationTimestamp: payload.timestamp as string,
            isOnline: true,
            lastSeen: new Date().toISOString(),
          };
        }
        if (event === 'device:battery') {
          return {
            ...d,
            batteryPercentage: payload.batteryPercentage as number,
            isCharging: payload.isCharging as boolean,
            lastSeen: new Date().toISOString(),
          };
        }
        if (event === 'device:online' || event === 'device:offline' || event === 'device:status') {
          return {
            ...d,
            isOnline: payload.isOnline as boolean,
            networkType: (payload.networkType as string) || d.networkType,
            lastSeen: (payload.lastSeen as string) || new Date().toISOString(),
          };
        }
        return d;
      })
    );
    if (event === 'device:location') {
      showToast(`Location update from ${deviceId}`, 'info');
    }
  });

  if (!ready) return null;

  const online = devices.filter((d) => d.isOnline).length;
  const offline = devices.length - online;
  const latestBattery = devices.find((d) => d.batteryPercentage !== null);
  const mapDevice = devices.find((d) => d.lastLatitude && d.lastLongitude);

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <Navbar />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <h1 className="text-2xl font-bold mb-8">Dashboard</h1>

        {loading && (
          <div className="text-center py-12 text-gray-500">Loading devices...</div>
        )}

        {error && (
          <div className="rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 p-4 text-red-700 dark:text-red-300 mb-6">
            {error}
          </div>
        )}

        {!loading && devices.length === 0 && (
          <div className="text-center py-16 rounded-xl border border-dashed border-gray-300 dark:border-gray-600">
            <p className="text-gray-500 dark:text-gray-400 mb-2">No devices registered yet</p>
            <p className="text-sm text-gray-400">Install the Android app and sign in to register a device</p>
          </div>
        )}

        {devices.length > 0 && (
          <>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
              <StatCard title="Total Devices" value={devices.length} />
              <StatCard title="Online" value={online} color="text-green-600" />
              <StatCard title="Offline" value={offline} color="text-gray-500" />
              <StatCard
                title="Latest Battery"
                value={latestBattery?.batteryPercentage ?? '—'}
                subtitle={
                  latestBattery
                    ? `${latestBattery.isCharging ? 'Charging' : 'Not charging'} · ${latestBattery.deviceName}`
                    : 'No data'
                }
              />
            </div>

            {mapDevice && mapDevice.lastLatitude && mapDevice.lastLongitude && (
              <div className="mb-8">
                <h2 className="text-lg font-semibold mb-4">Latest Location — {mapDevice.deviceName}</h2>
                <MapView
                  latitude={mapDevice.lastLatitude}
                  longitude={mapDevice.lastLongitude}
                  height="350px"
                />
              </div>
            )}

            <h2 className="text-lg font-semibold mb-4">Recent Devices</h2>
            <div className="grid gap-4">
              {devices.slice(0, 5).map((device) => (
                <Link
                  key={device.deviceId}
                  href={`/devices/${device.deviceId}`}
                  className="flex items-center justify-between rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4 hover:border-primary transition-colors"
                >
                  <div>
                    <p className="font-medium">{device.deviceName}</p>
                    <p className="text-sm text-gray-500">{device.manufacturer} {device.model}</p>
                  </div>
                  <div className="flex items-center gap-4 text-sm">
                    <span className={device.isOnline ? 'text-green-600' : 'text-gray-400'}>
                      {device.isOnline ? 'Online' : 'Offline'}
                    </span>
                    {device.batteryPercentage !== null && (
                      <span>{device.batteryPercentage}%</span>
                    )}
                  </div>
                </Link>
              ))}
            </div>
          </>
        )}
      </main>
    </div>
  );
}
