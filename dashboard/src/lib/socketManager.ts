import { io, Socket } from 'socket.io-client';
import { SOCKET_URL, getToken } from './api';

const EVENTS = [
  'device:online',
  'device:offline',
  'device:location',
  'device:battery',
  'device:status',
] as const;

type EventHandler = (event: string, data: unknown) => void;

let socket: Socket | null = null;

function connect(): Socket | null {
  const token = getToken();
  if (!token) return null;

  if (socket) {
    const currentToken = (socket.auth as { token?: string })?.token;
    if (currentToken !== token) {
      socket.disconnect();
      socket = null;
    }
  }

  if (!socket) {
    socket = io(SOCKET_URL, {
      auth: { token },
      transports: ['websocket', 'polling'],
      reconnection: true,
      reconnectionAttempts: 10,
      reconnectionDelay: 1000,
    });
  }

  return socket;
}

export function disconnectSocket() {
  if (socket) {
    socket.disconnect();
    socket = null;
  }
}

export function subscribe(handler: EventHandler): () => void {
  const s = connect();
  if (!s) return () => {};

  const listeners = EVENTS.map((event) => {
    const fn = (data: unknown) => handler(event, data);
    s.on(event, fn);
    return { event, fn };
  });

  return () => {
    listeners.forEach(({ event, fn }) => s.off(event, fn));
  };
}
