const jwt = require('jsonwebtoken');
const config = require('../config');

function setupSocket(io) {
  io.use((socket, next) => {
    const token = socket.handshake.auth?.token || socket.handshake.query?.token;
    if (!token) {
      return next(new Error('Authentication required'));
    }
    try {
      const decoded = jwt.verify(token, config.jwtSecret);
      socket.userId = decoded.id;
      socket.join(`user:${decoded.id}`);
      next();
    } catch (err) {
      next(new Error('Invalid token'));
    }
  });

  io.on('connection', (socket) => {
    console.log(`Socket connected: user ${socket.userId}`);

    socket.on('disconnect', () => {
      console.log(`Socket disconnected: user ${socket.userId}`);
    });
  });
}

module.exports = { setupSocket };
