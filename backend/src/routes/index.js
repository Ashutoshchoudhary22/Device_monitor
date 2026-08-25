const express = require('express');
const { authenticate } = require('../middleware/auth');
const { register, verifyOtp, resendOtp, login } = require('../routes/auth');
const {
  registerDevice,
  getDevices,
  getDevice,
  postLocation,
  getLocationHistory,
  postStatus,
  postBattery,
} = require('../routes/devices');

const router = express.Router();

router.post('/auth/register', register);
router.post('/auth/verify-otp', verifyOtp);
router.post('/auth/resend-otp', resendOtp);
router.post('/auth/login', login);

router.post('/devices/register', authenticate, registerDevice);
router.get('/devices', authenticate, getDevices);
router.get('/devices/:deviceId', authenticate, getDevice);
router.post('/devices/:deviceId/location', authenticate, postLocation);
router.get('/devices/:deviceId/location/history', authenticate, getLocationHistory);
router.post('/devices/:deviceId/status', authenticate, postStatus);
router.post('/devices/:deviceId/battery', authenticate, postBattery);

module.exports = router;
