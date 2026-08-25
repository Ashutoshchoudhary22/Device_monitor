'use client';

import { useCallback, useEffect, useState } from 'react';
import dynamic from 'next/dynamic';
import Link from 'next/link';
import Navbar from '@/components/Navbar';
import { useAuth } from '@/hooks/useAuth';
import { useSocket } from '@/hooks/useSocket';
import { api, Device } from '@/lib/api';

const MapView = dynamic(() => import('@/components/MapView'), { ssr: false });

export default function DeviceDetailPage({ params }: { params: { deviceId: string } }) {
  const { deviceId } = params;
  const ready = useAuth();
  const [device, setDevice] = useState<Device | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const { device: d } = await api.getDevice(deviceId);
      setDevice(d);
      setError('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load device');
    } finally {
      setLoading(false);
    }
  }, [deviceId]);

  useEffect(() => {
    if (ready) load();
  }, [ready, load]);

  useSocket((event, data) => {
    const payload = data as Record<string, unknown>;
    if (payload.deviceId !== deviceId) return;

    setDevice((prev) => {
      if (!prev) return prev;
      if (event === 'device:location') {
        return {
          ...prev,
          lastLatitude: payload.latitude as number,
          lastLongitude: payload.longitude as number,
          lastAccuracy: payload.accuracy as number,
          lastAltitude: payload.altitude as number,
          lastSpeed: payload.speed as number,
          lastLocationTimestamp: payload.timestamp as string,
          isOnline: true,
          lastSeen: new Date().toISOString(),
        };
      }
      if (event === 'device:battery') {
        return {
          ...prev,
          batteryPercentage: payload.batteryPercentage as number,
          isCharging: payload.isCharging as boolean,
          batteryTemperature: payload.batteryTemperature as number,
          batteryHealth: payload.batteryHealth as string,
          lastSeen: new Date().toISOString(),
        };
      }
      if (event === 'device:online' || event === 'device:offline' || event === 'device:status') {
        return {
          ...prev,
          isOnline: payload.isOnline as boolean,
          networkType: (payload.networkType as string) || prev.networkType,
          wifiAvailable: (payload.wifiAvailable as boolean) ?? prev.wifiAvailable,
          mobileDataAvailable: (payload.mobileDataAvailable as boolean) ?? prev.mobileDataAvailable,
          lastSeen: (payload.lastSeen as string) || new Date().toISOString(),
        };
      }
      return prev;
    });
  });

  if (!ready) return null;

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <Navbar />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex items-center gap-4 mb-8">
          <Link href="/devices" className="text-primary hover:underline text-sm">← Devices</Link>
          <h1 className="text-2xl font-bold">{device?.deviceName || deviceId}</h1>
          {device && (
            <span
              className={`text-xs px-2 py-1 rounded-full ${
                device.isOnline
                  ? 'bg-green-100 text-green-700 dark:bg-green-900/30'
                  : 'bg-gray-100 text-gray-500'
              }`}
            >
              {device.isOnline ? 'Online' : 'Offline'}
            </span>
          )}
        </div>

        {loading && <p className="text-gray-500">Loading...</p>}
        {error && <p className="text-red-600">{error}</p>}

        {device && (
          <div className="space-y-8">
            {device.lastLatitude && device.lastLongitude ? (
              <MapView
                latitude={device.lastLatitude}
                longitude={device.lastLongitude}
                height="450px"
              />
            ) : (
              <div className="rounded-xl border border-dashed border-gray-300 dark:border-gray-600 p-12 text-center text-gray-500">
                No location data yet. Enable tracking on the device.
              </div>
            )}

            <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4">
              <InfoCard label="Latitude" value={device.lastLatitude?.toFixed(6) ?? '—'} />
              <InfoCard label="Longitude" value={device.lastLongitude?.toFixed(6) ?? '—'} />
              <InfoCard label="Accuracy" value={device.lastAccuracy ? `${device.lastAccuracy} m` : '—'} />
              <InfoCard label="Battery" value={device.batteryPercentage !== null ? `${device.batteryPercentage}%` : '—'} />
              <InfoCard label="Charging" value={device.isCharging ? 'Yes' : 'No'} />
              <InfoCard label="Network" value={device.networkType} />
              <InfoCard label="WiFi" value={device.wifiAvailable ? 'Available' : 'Not available'} />
              <InfoCard label="Mobile Data" value={device.mobileDataAvailable ? 'Available' : 'Not available'} />
              <InfoCard label="Last Seen" value={new Date(device.lastSeen).toLocaleString()} />
              <InfoCard label="Model" value={`${device.manufacturer} ${device.model}`} />
              <InfoCard label="Android" value={device.androidVersion} />
              <InfoCard label="App Version" value={device.appVersion} />
            </div>

            <Link
              href={`/devices/${deviceId}/history`}
              className="inline-flex items-center rounded-lg bg-primary text-white px-4 py-2 text-sm font-medium hover:bg-primary-dark"
            >
              View Location History
            </Link>
          </div>
        )}
      </main>
    </div>
  );
}

function InfoCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4">
      <p className="text-sm text-gray-500 dark:text-gray-400">{label}</p>
      <p className="mt-1 font-medium">{value}</p>
    </div>
  );
}
