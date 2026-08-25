const request = require('supertest');
const mongoose = require('mongoose');
const { MongoMemoryServer } = require('mongodb-memory-server');
const express = require('express');
const cors = require('cors');
const apiRoutes = require('../src/routes');
const errorHandler = require('../src/middleware/errorHandler');
const { sendOtpEmail } = require('../src/services/email');

jest.mock('../src/services/email', () => ({
  sendOtpEmail: jest.fn().mockResolvedValue(true),
  isEmailConfigured: jest.fn().mockReturnValue(true),
}));

let mongoServer;
let app;
let token;
let deviceId;
let lastOtp = '';

beforeAll(async () => {
  mongoServer = await MongoMemoryServer.create();
  await mongoose.connect(mongoServer.getUri());

  sendOtpEmail.mockImplementation((_email, otp) => {
    lastOtp = otp;
    return Promise.resolve();
  });

  app = express();
  app.use(cors());
  app.use(express.json());
  app.locals.io = { to: () => ({ emit: () => {} }) };
  app.use('/api', apiRoutes);
  app.use(errorHandler);
});

afterAll(async () => {
  await mongoose.disconnect();
  await mongoServer.stop();
});

describe('Auth API', () => {
  test('POST /api/auth/register - sends OTP', async () => {
    const res = await request(app)
      .post('/api/auth/register')
      .send({ email: 'test@example.com', password: 'password123', name: 'Test User' });

    expect(res.status).toBe(200);
    expect(res.body.message).toMatch(/OTP sent/i);
    expect(res.body.email).toBe('test@example.com');
    expect(lastOtp).toMatch(/^\d{6}$/);
  });

  test('POST /api/auth/verify-otp - success', async () => {
    const res = await request(app)
      .post('/api/auth/verify-otp')
      .send({ email: 'test@example.com', otp: lastOtp });

    expect(res.status).toBe(201);
    expect(res.body.token).toBeDefined();
    expect(res.body.user.email).toBe('test@example.com');
    token = res.body.token;
  });

  test('POST /api/auth/login - success', async () => {
    const res = await request(app)
      .post('/api/auth/login')
      .send({ email: 'test@example.com', password: 'password123' });

    expect(res.status).toBe(200);
    expect(res.body.token).toBeDefined();
    token = res.body.token;
  });

  test('POST /api/auth/login - invalid credentials', async () => {
    const res = await request(app)
      .post('/api/auth/login')
      .send({ email: 'test@example.com', password: 'wrong' });

    expect(res.status).toBe(401);
  });

  test('POST /api/auth/verify-otp - invalid OTP', async () => {
    await request(app)
      .post('/api/auth/register')
      .send({ email: 'other@example.com', password: 'password123', name: 'Other' });

    const res = await request(app)
      .post('/api/auth/verify-otp')
      .send({ email: 'other@example.com', otp: '000000' });

    expect(res.status).toBe(400);
  });
});

describe('Device API', () => {
  test('POST /api/devices/register - success', async () => {
    deviceId = 'test-device-001';
    const res = await request(app)
      .post('/api/devices/register')
      .set('Authorization', `Bearer ${token}`)
      .send({
        deviceId,
        deviceName: 'Test Phone',
        androidVersion: '14',
        manufacturer: 'Google',
        model: 'Pixel 8',
        appVersion: '1.0.0',
      });

    expect(res.status).toBe(201);
    expect(res.body.device.deviceId).toBe(deviceId);
  });

  test('GET /api/devices - success', async () => {
    const res = await request(app)
      .get('/api/devices')
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.devices.length).toBeGreaterThan(0);
  });

  test('GET /api/devices/:deviceId - success', async () => {
    const res = await request(app)
      .get(`/api/devices/${deviceId}`)
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.device.deviceId).toBe(deviceId);
  });

  test('POST /api/devices/:deviceId/location - success', async () => {
    const res = await request(app)
      .post(`/api/devices/${deviceId}/location`)
      .set('Authorization', `Bearer ${token}`)
      .send({
        latitude: 28.6139,
        longitude: 77.209,
        accuracy: 10,
        altitude: 200,
        speed: 5,
        timestamp: new Date().toISOString(),
      });

    expect(res.status).toBe(201);
    expect(res.body.location.latitude).toBe(28.6139);
  });

  test('GET /api/devices/:deviceId/location/history - success', async () => {
    const today = new Date().toISOString().split('T')[0];
    const res = await request(app)
      .get(`/api/devices/${deviceId}/location/history?date=${today}`)
      .set('Authorization', `Bearer ${token}`);

    expect(res.status).toBe(200);
    expect(res.body.locations.length).toBeGreaterThan(0);
  });

  test('POST /api/devices/:deviceId/status - success', async () => {
    const res = await request(app)
      .post(`/api/devices/${deviceId}/status`)
      .set('Authorization', `Bearer ${token}`)
      .send({
        isOnline: true,
        networkType: 'wifi',
        wifiAvailable: true,
        mobileDataAvailable: false,
      });

    expect(res.status).toBe(200);
    expect(res.body.device.isOnline).toBe(true);
  });

  test('POST /api/devices/:deviceId/battery - success', async () => {
    const res = await request(app)
      .post(`/api/devices/${deviceId}/battery`)
      .set('Authorization', `Bearer ${token}`)
      .send({
        batteryPercentage: 85,
        isCharging: false,
        batteryTemperature: 30,
        batteryHealth: 'good',
      });

    expect(res.status).toBe(200);
    expect(res.body.device.batteryPercentage).toBe(85);
  });

  test('Protected route without token - 401', async () => {
    const res = await request(app).get('/api/devices');
    expect(res.status).toBe(401);
  });
});
