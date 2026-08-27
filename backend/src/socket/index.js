const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const config = require('../config');
const User = require('../models/User');
const { isValidEmail, sanitizeString } = require('../utils/validation');
const {
  createRegistrationOtp,
  verifyRegistrationOtp,
  resendRegistrationOtp,
} = require('../services/otpService');
const { isEmailConfigured } = require('../services/email');
const deviceEvents = require('../services/deviceEvents');

function ackError(ack, err) {
  if (typeof ack === 'function') {
    const message = err?.message || (typeof err === 'string' ? err : 'Request failed');
    ack({ ok: false, error: message });
  }
}

function ackSuccess(ack, data) {
  if (typeof ack === 'function') {
    ack({ ok: true, ...data });
  }
}

function requireAuth(socket, ack) {
  if (!socket.userId) {
    ackError(ack, { message: 'Authentication required' });
    return false;
  }
  return true;
}

async function authenticateSocket(socket, userId) {
  socket.userId = userId.toString();
  await socket.join(`user:${socket.userId}`);
}

function setupSocket(io) {
  io.use((socket, next) => {
    const token = socket.handshake.auth?.token || socket.handshake.query?.token;
    if (!token) {
      socket.userId = null;
      return next();
    }
    try {
      const decoded = jwt.verify(token, config.jwtSecret);
      socket.userId = decoded.id.toString();
      socket.join(`user:${socket.userId}`);
      next();
    } catch (err) {
      next(new Error('Invalid token'));
    }
  });

  io.on('connection', (socket) => {
    console.log(`Socket connected: ${socket.userId || 'guest'}`);

    socket.on('auth:login', async (data, ack) => {
      try {
        const { email, password } = data || {};
        if (!email || !password) {
          return ackError(ack, { message: 'Email and password are required' });
        }

        const user = await User.findOne({ email: email.toLowerCase() });
        if (!user) {
          return ackError(ack, { message: 'Invalid credentials' });
        }

        const valid = await bcrypt.compare(password, user.password);
        if (!valid) {
          return ackError(ack, { message: 'Invalid credentials' });
        }

        const token = jwt.sign({ id: user._id, email: user.email }, config.jwtSecret, {
          expiresIn: config.jwtExpiresIn,
        });

        await authenticateSocket(socket, user._id);

        ackSuccess(ack, {
          token,
          user: { id: user._id, email: user.email, name: user.name },
        });
      } catch (err) {
        ackError(ack, err);
      }
    });

    socket.on('auth:register', async (data, ack) => {
      try {
        const { email, password, name } = data || {};
        if (!email || !password) {
          return ackError(ack, { message: 'Email and password are required' });
        }
        if (!isValidEmail(email)) {
          return ackError(ack, { message: 'Invalid email format' });
        }
        if (password.length < 6) {
          return ackError(ack, { message: 'Password must be at least 6 characters' });
        }
        if (!isEmailConfigured()) {
          return ackError(ack, { message: 'Email service is not configured on the server' });
        }

        const normalizedEmail = email.toLowerCase();
        const existing = await User.findOne({ email: normalizedEmail });
        if (existing) {
          return ackError(ack, { message: 'Email already registered' });
        }

        const hashed = await bcrypt.hash(password, 12);
        const result = await createRegistrationOtp(
          normalizedEmail,
          hashed,
          sanitizeString(name || '', 100)
        );

        ackSuccess(ack, result);
      } catch (err) {
        ackError(ack, err);
      }
    });

    socket.on('auth:verify-otp', async (data, ack) => {
      try {
        const { email, otp } = data || {};
        if (!email || !otp) {
          return ackError(ack, { message: 'Email and OTP are required' });
        }

        const normalizedEmail = email.toLowerCase();
        const verified = await verifyRegistrationOtp(normalizedEmail, String(otp).trim());

        const user = await User.create({
          email: verified.email,
          password: verified.passwordHash,
          name: verified.name,
        });

        const token = jwt.sign({ id: user._id, email: user.email }, config.jwtSecret, {
          expiresIn: config.jwtExpiresIn,
        });

        await authenticateSocket(socket, user._id);

        ackSuccess(ack, {
          token,
          user: { id: user._id, email: user.email, name: user.name },
          message: 'Account verified and created successfully',
        });
      } catch (err) {
        ackError(ack, err.status ? err : { message: err.message });
      }
    });

    socket.on('auth:resend-otp', async (data, ack) => {
      try {
        const { email } = data || {};
        if (!email) {
          return ackError(ack, { message: 'Email is required' });
        }
        const result = await resendRegistrationOtp(email.toLowerCase());
        ackSuccess(ack, result);
      } catch (err) {
        ackError(ack, err.status ? err : { message: err.message });
      }
    });

    socket.on('device:register', async (data, ack) => {
      if (!requireAuth(socket, ack)) return;
      try {
        const result = await deviceEvents.registerDevice(socket.userId, data || {});
        if (data?.deviceId) {
          socket.deviceId = data.deviceId;
        }
        ackSuccess(ack, result);
      } catch (err) {
        ackError(ack, err);
      }
    });

    socket.on('device:location', async (data, ack) => {
      if (!requireAuth(socket, ack)) return;
      try {
        const { deviceId, ...locationData } = data || {};
        if (!deviceId) {
          return ackError(ack, { message: 'deviceId is required' });
        }
        socket.deviceId = deviceId;
        const result = await deviceEvents.recordLocation(
          socket.userId,
          deviceId,
          locationData,
          io
        );
        ackSuccess(ack, { message: result.message });
      } catch (err) {
        ackError(ack, err);
      }
    });

    socket.on('device:status', async (data, ack) => {
      if (!requireAuth(socket, ack)) return;
      try {
        const { deviceId, ...statusData } = data || {};
        if (!deviceId) {
          return ackError(ack, { message: 'deviceId is required' });
        }
        socket.deviceId = deviceId;
        const result = await deviceEvents.recordStatus(
          socket.userId,
          deviceId,
          statusData,
          io
        );
        ackSuccess(ack, { message: result.message });
      } catch (err) {
        ackError(ack, err);
      }
    });

    socket.on('device:battery', async (data, ack) => {
      if (!requireAuth(socket, ack)) return;
      try {
        const { deviceId, ...batteryData } = data || {};
        if (!deviceId) {
          return ackError(ack, { message: 'deviceId is required' });
        }
        socket.deviceId = deviceId;
        const result = await deviceEvents.recordBattery(
          socket.userId,
          deviceId,
          batteryData,
          io
        );
        ackSuccess(ack, { message: result.message });
      } catch (err) {
        ackError(ack, err);
      }
    });

    socket.on('dashboard:getDevices', async (_data, ack) => {
      if (!requireAuth(socket, ack)) return;
      try {
        const result = await deviceEvents.getDevices(socket.userId);
        ackSuccess(ack, result);
      } catch (err) {
        ackError(ack, err);
      }
    });

    socket.on('dashboard:getDevice', async (data, ack) => {
      if (!requireAuth(socket, ack)) return;
      try {
        const { deviceId } = data || {};
        if (!deviceId) {
          return ackError(ack, { message: 'deviceId is required' });
        }
        const result = await deviceEvents.getDevice(socket.userId, deviceId);
        ackSuccess(ack, result);
      } catch (err) {
        ackError(ack, err);
      }
    });

    socket.on('dashboard:getLocationHistory', async (data, ack) => {
      if (!requireAuth(socket, ack)) return;
      try {
        const { deviceId, date, page, limit } = data || {};
        if (!deviceId) {
          return ackError(ack, { message: 'deviceId is required' });
        }
        const result = await deviceEvents.getLocationHistory(socket.userId, deviceId, {
          date,
          page,
          limit,
        });
        ackSuccess(ack, result);
      } catch (err) {
        ackError(ack, err);
      }
    });

    socket.on('disconnect', async () => {
      console.log(`Socket disconnected: ${socket.userId || 'guest'}`);
      if (!socket.userId || !socket.deviceId) return;

      try {
        await deviceEvents.recordStatus(
          socket.userId,
          socket.deviceId,
          { isOnline: false },
          io
        );
      } catch (err) {
        console.error('Failed to mark device offline:', err.message);
      }
    });
  });
}

module.exports = { setupSocket };
