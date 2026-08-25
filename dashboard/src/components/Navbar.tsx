'use client';

import Link from 'next/link';
import { useTheme } from '@/context/ThemeContext';
import { clearAuth, getUser } from '@/lib/api';
import { disconnectSocket } from '@/lib/socketManager';
import { useRouter } from 'next/navigation';

export default function Navbar() {
  const { theme, toggleTheme } = useTheme();
  const router = useRouter();
  const user = getUser();

  const logout = () => {
    disconnectSocket();
    clearAuth();
    router.push('/login');
  };

  return (
    <nav className="border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex h-16 items-center justify-between">
          <div className="flex items-center gap-6">
            <Link href="/dashboard" className="text-lg font-bold text-primary">
              Device Monitor
            </Link>
            <Link href="/dashboard" className="text-sm text-gray-600 dark:text-gray-300 hover:text-primary">
              Dashboard
            </Link>
            <Link href="/devices" className="text-sm text-gray-600 dark:text-gray-300 hover:text-primary">
              Devices
            </Link>
          </div>
          <div className="flex items-center gap-4">
            {user && (
              <span className="text-sm text-gray-500 dark:text-gray-400 hidden sm:inline">
                {user.email}
              </span>
            )}
            <button
              onClick={toggleTheme}
              className="rounded-lg p-2 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700"
              aria-label="Toggle theme"
            >
              {theme === 'dark' ? '☀️' : '🌙'}
            </button>
            <button
              onClick={logout}
              className="text-sm text-red-600 hover:text-red-700 dark:text-red-400"
            >
              Logout
            </button>
          </div>
        </div>
      </div>
    </nav>
  );
}
