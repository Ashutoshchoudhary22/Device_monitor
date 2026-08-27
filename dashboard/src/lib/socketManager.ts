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

interface SocketAck {
  ok: boolean;
  error?: string;
  [key: string]: unknown;
}

let socket: Socket | null = null;
let connectPromise: Promise<Socket> | null = null;

function createSocket(token?: string | null): Socket {
  return io(SOCKET_URL, {
    auth: token ? { token } : {},
    transports: ['websocket', 'polling'],
    reconnection: true,
    reconnectionAttempts: Infinity,
    reconnectionDelay: 1000,
    timeout: 15000,
    autoConnect: false,
  });
}

function connect(): Socket | null {
  const token = getToken();

  if (socket) {
    const currentToken = (socket.auth as { token?: string })?.token;
    if (currentToken !== token) {
      socket.disconnect();
      socket = null;
      connectPromise = null;
    }
  }

  if (!socket) {
    socket = createSocket(token);
  }

  return socket;
}

function connectGuest(): Socket {
  if (socket?.connected) {
    return socket;
  }

  if (socket) {
    socket.disconnect();
  }

  socket = createSocket(null);
  connectPromise = null;
  return socket;
}

function waitForConnection(s: Socket): Promise<Socket> {
  if (s.connected) return Promise.resolve(s);

  if (!connectPromise) {
    connectPromise = new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error('Socket connection timeout'));
      }, 15000);

      const onConnect = () => {
        clearTimeout(timeout);
        s.off('connect_error', onError);
        resolve(s);
      };

      const onError = (err: Error) => {
        clearTimeout(timeout);
        s.off('connect', onConnect);
        reject(err);
      };

      s.once('connect', onConnect);
      s.once('connect_error', onError);
      if (!s.connected) s.connect();
    });
  }

  return connectPromise;
}

export async function socketRequest<T extends SocketAck>(
  event: string,
  data?: Record<string, unknown>,
  options?: { guest?: boolean }
): Promise<T> {
  const s = options?.guest ? connectGuest() : connect();
  if (!s) throw new Error('Not authenticated');

  const connected = await waitForConnection(s);

  return new Promise((resolve, reject) => {
    connected.timeout(15000).emit(event, data ?? {}, (response: T) => {
      if (response?.ok) {
        resolve(response);
      } else {
        reject(new Error(response?.error || 'Socket request failed'));
      }
    });
  });
}

export function disconnectSocket() {
  if (socket) {
    socket.disconnect();
    socket = null;
    connectPromise = null;
  }
}

export function subscribe(handler: EventHandler): () => void {
  const s = connect();
  if (!s) return () => {};

  void waitForConnection(s).catch(() => {});

  const listeners = EVENTS.map((event) => {
    const fn = (data: unknown) => handler(event, data);
    s.on(event, fn);
    return { event, fn };
  });

  return () => {
    listeners.forEach(({ event, fn }) => s.off(event, fn));
  };
}
