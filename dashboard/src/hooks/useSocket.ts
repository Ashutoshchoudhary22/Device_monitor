'use client';

import { useEffect, useRef } from 'react';
import { subscribe } from '@/lib/socketManager';

export function useSocket(onEvent: (event: string, data: unknown) => void) {
  const onEventRef = useRef(onEvent);
  onEventRef.current = onEvent;

  useEffect(() => {
    return subscribe((event, data) => onEventRef.current(event, data));
  }, []);
}
