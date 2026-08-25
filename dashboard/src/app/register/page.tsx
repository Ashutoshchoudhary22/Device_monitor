'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { api, setToken, setUser } from '@/lib/api';
import { useToast } from '@/context/ToastContext';

export default function RegisterPage() {
  const router = useRouter();
  const { showToast } = useToast();
  const [step, setStep] = useState<'form' | 'otp'>('form');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [otp, setOtp] = useState('');
  const [loading, setLoading] = useState(false);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      const result = await api.register(email, password, name);
      showToast(result.message || 'OTP sent to your email', 'success');
      setStep('otp');
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Registration failed', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    if (otp.length < 6) {
      showToast('Enter the 6-digit OTP', 'error');
      return;
    }
    setLoading(true);
    try {
      const { token, user } = await api.verifyOtp(email, otp);
      setToken(token);
      setUser(user);
      showToast('Account verified successfully', 'success');
      router.push('/dashboard');
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'OTP verification failed', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleResendOtp = async () => {
    setLoading(true);
    try {
      const result = await api.resendOtp(email);
      showToast(result.message || 'OTP resent', 'success');
    } catch (err) {
      showToast(err instanceof Error ? err.message : 'Failed to resend OTP', 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
      <div className="w-full max-w-md rounded-2xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-8 shadow-lg">
        <h1 className="text-2xl font-bold text-center mb-2">Create Account</h1>
        <p className="text-center text-gray-500 dark:text-gray-400 text-sm mb-8">
          {step === 'form'
            ? 'Register to monitor your devices'
            : `Enter the OTP sent to ${email}`}
        </p>

        {step === 'form' ? (
          <form onSubmit={handleRegister} className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1">Name</label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 px-4 py-2 focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="Your name"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Email</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 px-4 py-2 focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Password</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                minLength={6}
                className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 px-4 py-2 focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full rounded-lg bg-primary hover:bg-primary-dark text-white py-2.5 font-medium disabled:opacity-50"
            >
              {loading ? 'Sending OTP...' : 'Send OTP'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleVerifyOtp} className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1">6-digit OTP</label>
              <input
                type="text"
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))}
                required
                maxLength={6}
                inputMode="numeric"
                autoComplete="one-time-code"
                className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-700 px-4 py-2 text-center text-2xl tracking-[0.5em] focus:outline-none focus:ring-2 focus:ring-primary"
                placeholder="000000"
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full rounded-lg bg-primary hover:bg-primary-dark text-white py-2.5 font-medium disabled:opacity-50"
            >
              {loading ? 'Verifying...' : 'Verify OTP & Create Account'}
            </button>
            <button
              type="button"
              onClick={handleResendOtp}
              disabled={loading}
              className="w-full rounded-lg border border-gray-300 dark:border-gray-600 py-2.5 text-sm font-medium hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50"
            >
              Resend OTP
            </button>
            <button
              type="button"
              onClick={() => setStep('form')}
              className="w-full text-sm text-gray-500 hover:text-primary"
            >
              ← Change email / details
            </button>
          </form>
        )}

        <p className="mt-6 text-center text-sm text-gray-500">
          Already have an account?{' '}
          <Link href="/login" className="text-primary hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
