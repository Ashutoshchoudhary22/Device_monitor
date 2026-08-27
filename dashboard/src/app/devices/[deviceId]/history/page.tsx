'use client';

import { useCallback, useEffect, useState } from 'react';
import dynamic from 'next/dynamic';
import Link from 'next/link';
import Navbar from '@/components/Navbar';
import { useAuth } from '@/hooks/useAuth';
import { api, LocationPoint } from '@/lib/api';
import LocationAddress from '@/components/LocationAddress';

const MapView = dynamic(() => import('@/components/MapView'), { ssr: false });

export default function HistoryPage({ params }: { params: { deviceId: string } }) {
  const { deviceId } = params;
  const ready = useAuth();
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
  const [locations, setLocations] = useState<LocationPoint[]>([]);
  const [summary, setSummary] = useState<{
    pointCount: number;
    startTime: string | null;
    endTime: string | null;
  } | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await api.getLocationHistory(deviceId, date);
      setLocations(data.locations);
      setSummary(data.summary);
      setError('');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load history');
      setLocations([]);
      setSummary(null);
    } finally {
      setLoading(false);
    }
  }, [deviceId, date]);

  useEffect(() => {
    if (ready) load();
  }, [ready, load]);

  if (!ready) return null;

  const path: Array<[number, number]> = locations.map((l) => [l.latitude, l.longitude]);
  const latest = locations.length > 0 ? locations[locations.length - 1] : null;
  const centerLat = latest?.latitude ?? 28.6139;
  const centerLng = latest?.longitude ?? 77.209;

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <Navbar />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex flex-wrap items-center gap-4 mb-8">
          <Link href={`/devices/${deviceId}`} className="text-primary hover:underline text-sm">
            ← Back to device
          </Link>
          <h1 className="text-2xl font-bold">Location History</h1>
          <input
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            className="rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 px-3 py-2 text-sm"
          />
        </div>

        {loading && <p className="text-gray-500">Loading history...</p>}
        {error && <p className="text-red-600 mb-4">{error}</p>}

        {!loading && locations.length === 0 && !error && (
          <div className="text-center py-16 text-gray-500 rounded-xl border border-dashed border-gray-300 dark:border-gray-600">
            No location points for this date
          </div>
        )}

        {locations.length > 0 && (
          <div className="space-y-6">
            <div className="grid sm:grid-cols-3 gap-4">
              <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4">
                <p className="text-sm text-gray-500">Points recorded</p>
                <p className="text-2xl font-bold">{summary?.pointCount ?? locations.length}</p>
              </div>
              <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4">
                <p className="text-sm text-gray-500">Start time</p>
                <p className="font-medium">
                  {summary?.startTime ? new Date(summary.startTime).toLocaleString() : '—'}
                </p>
              </div>
              <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4">
                <p className="text-sm text-gray-500">End time</p>
                <p className="font-medium">
                  {summary?.endTime ? new Date(summary.endTime).toLocaleString() : '—'}
                </p>
              </div>
            </div>

            {latest && (
              <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-4">
                <p className="text-sm text-gray-500 mb-2">Latest point</p>
                <LocationAddress
                  address={latest.address}
                  latitude={latest.latitude}
                  longitude={latest.longitude}
                />
              </div>
            )}

            <MapView latitude={centerLat} longitude={centerLng} path={path} height="500px" />

            <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 overflow-hidden">
              <div className="px-4 py-3 border-b border-gray-200 dark:border-gray-700">
                <h2 className="font-semibold">Location Points</h2>
              </div>
              <div className="max-h-80 overflow-y-auto divide-y divide-gray-100 dark:divide-gray-700">
                {[...locations].reverse().map((point) => (
                  <div key={point._id} className="px-4 py-3 text-sm">
                    <p className="font-medium">{point.address || 'Address not available'}</p>
                    <p className="text-xs text-gray-500 mt-1">
                      {point.latitude.toFixed(6)}, {point.longitude.toFixed(6)} ·{' '}
                      {new Date(point.timestamp).toLocaleString()}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
