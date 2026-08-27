const mongoose = require('mongoose');

const deviceSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true,
    },
    deviceId: {
      type: String,
      required: true,
      unique: true,
      trim: true,
    },
    deviceName: {
      type: String,
      trim: true,
      default: 'My Device',
    },
    androidVersion: { type: String, default: '' },
    manufacturer: { type: String, default: '' },
    model: { type: String, default: '' },
    appVersion: { type: String, default: '' },
    isOnline: { type: Boolean, default: false },
    lastSeen: { type: Date, default: Date.now, index: true },
    batteryPercentage: { type: Number, min: 0, max: 100, default: null },
    isCharging: { type: Boolean, default: false },
    batteryTemperature: { type: Number, default: null },
    batteryHealth: { type: String, default: null },
    networkType: { type: String, default: 'unknown' },
    wifiAvailable: { type: Boolean, default: false },
    mobileDataAvailable: { type: Boolean, default: false },
    lastLatitude: { type: Number, default: null },
    lastLongitude: { type: Number, default: null },
    lastAddress: { type: String, default: null },
    lastAccuracy: { type: Number, default: null },
    lastAltitude: { type: Number, default: null },
    lastSpeed: { type: Number, default: null },
    lastLocationTimestamp: { type: Date, default: null },
    trackingEnabled: { type: Boolean, default: false },
    updateIntervalSeconds: { type: Number, default: 15, min: 5, max: 300 },
  },
  { timestamps: true }
);

deviceSchema.index({ userId: 1, deviceId: 1 });
deviceSchema.index({ lastSeen: -1 });

module.exports = mongoose.model('Device', deviceSchema);
