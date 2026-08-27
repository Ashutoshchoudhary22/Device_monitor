const Device = require('../models/Device');
const Location = require('../models/Location');
const DeviceStatus = require('../models/DeviceStatus');
const config = require('../config');
const { reverseGeocode } = require('./geocoding');
const {
  sanitizeString,
  isValidDeviceId,
  parseNumber,
} = require('../utils/validation');

async function cleanupOldLocations(deviceId, userId) {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - config.locationRetentionDays);

  await Location.deleteMany({
    deviceId,
    userId,
    timestamp: { $lt: cutoff },
  });

  const count = await Location.countDocuments({ deviceId, userId });
  if (count > config.locationMaxPerDevice) {
    const excess = count - config.locationMaxPerDevice;
    const oldest = await Location.find({ deviceId, userId })
      .sort({ timestamp: 1 })
      .limit(excess)
      .select('_id');
    await Location.deleteMany({ _id: { $in: oldest.map((l) => l._id) } });
  }
}

function emitToUser(io, userId, event, payload) {
  if (io) {
    io.to(`user:${userId}`).emit(event, payload);
  }
}

function getOfflineCutoffMs(device) {
  const intervalSec = device.updateIntervalSeconds || 15;
  return Math.max(config.deviceOfflineTimeoutMs, intervalSec * 3 * 1000);
}

function isDeviceEffectivelyOnline(device) {
  if (!device.isOnline) return false;
  if (!device.lastSeen) return false;
  const cutoff = Date.now() - getOfflineCutoffMs(device);
  return new Date(device.lastSeen).getTime() >= cutoff;
}

function applyEffectiveOnlineStatus(device) {
  const effectiveOnline = isDeviceEffectivelyOnline(device);
  if (effectiveOnline === device.isOnline) return device;
  return { ...device, isOnline: effectiveOnline };
}

async function registerDevice(userId, data) {
  const {
    deviceId,
    deviceName,
    androidVersion,
    manufacturer,
    model,
    appVersion,
    updateIntervalSeconds,
  } = data;

  if (!deviceId || !isValidDeviceId(deviceId)) {
    const error = new Error('Valid deviceId is required (8-64 alphanumeric chars)');
    error.status = 400;
    throw error;
  }

  const existing = await Device.findOne({ deviceId });
  if (existing) {
    if (existing.userId.toString() !== userId.toString()) {
      const error = new Error('Device already registered to another account');
      error.status = 409;
      throw error;
    }
    existing.deviceName = sanitizeString(deviceName || existing.deviceName, 100);
    existing.androidVersion = sanitizeString(androidVersion || '', 50);
    existing.manufacturer = sanitizeString(manufacturer || '', 100);
    existing.model = sanitizeString(model || '', 100);
    existing.appVersion = sanitizeString(appVersion || '', 50);
    if (updateIntervalSeconds) {
      const interval = parseNumber(updateIntervalSeconds, 5, 300);
      if (interval) existing.updateIntervalSeconds = interval;
    }
    existing.isOnline = true;
    existing.lastSeen = new Date();
    await existing.save();
    return { device: existing, message: 'Device updated' };
  }

  const device = await Device.create({
    userId,
    deviceId,
    deviceName: sanitizeString(deviceName || 'My Device', 100),
    androidVersion: sanitizeString(androidVersion || '', 50),
    manufacturer: sanitizeString(manufacturer || '', 100),
    model: sanitizeString(model || '', 100),
    appVersion: sanitizeString(appVersion || '', 50),
    updateIntervalSeconds: parseNumber(updateIntervalSeconds, 5, 300) || 15,
    isOnline: true,
    lastSeen: new Date(),
  });

  return { device, message: 'Device registered' };
}

async function getDevices(userId) {
  const devices = await Device.find({ userId }).sort({ lastSeen: -1 }).lean();
  return { devices: devices.map(applyEffectiveOnlineStatus) };
}

async function getDevice(userId, deviceId) {
  const device = await Device.findOne({ deviceId, userId }).lean();
  if (!device) {
    const error = new Error('Device not found');
    error.status = 404;
    throw error;
  }
  return { device: applyEffectiveOnlineStatus(device) };
}

async function markStaleDevicesOffline(io) {
  const onlineDevices = await Device.find({ isOnline: true });
  const now = Date.now();

  for (const device of onlineDevices) {
    const cutoffMs = getOfflineCutoffMs(device);
    if (now - new Date(device.lastSeen).getTime() <= cutoffMs) continue;

    device.isOnline = false;
    await device.save();

    const payload = {
      deviceId: device.deviceId,
      isOnline: false,
      networkType: device.networkType,
      wifiAvailable: device.wifiAvailable,
      mobileDataAvailable: device.mobileDataAvailable,
      lastSeen: device.lastSeen,
    };

    emitToUser(io, device.userId, 'device:offline', payload);
    emitToUser(io, device.userId, 'device:status', payload);
  }
}

async function recordLocation(userId, deviceId, data, io) {
  const { latitude, longitude, accuracy, altitude, speed, timestamp } = data;

  const lat = parseNumber(latitude, -90, 90);
  const lng = parseNumber(longitude, -180, 180);
  if (lat === null || lng === null) {
    const error = new Error('Valid latitude and longitude are required');
    error.status = 400;
    throw error;
  }

  const device = await Device.findOne({ deviceId, userId });
  if (!device) {
    const error = new Error('Device not found');
    error.status = 404;
    throw error;
  }

  const ts = timestamp ? new Date(timestamp) : new Date();
  if (Number.isNaN(ts.getTime())) {
    const error = new Error('Invalid timestamp');
    error.status = 400;
    throw error;
  }

  const locationData = {
    deviceId,
    userId,
    latitude: lat,
    longitude: lng,
    accuracy: parseNumber(accuracy, 0, 10000),
    altitude: parseNumber(altitude, -1000, 10000),
    speed: parseNumber(speed, 0, 500),
    timestamp: ts,
  };

  const address = await reverseGeocode(lat, lng);
  if (address) {
    locationData.address = address;
  }

  const location = await Location.create(locationData);

  device.lastLatitude = lat;
  device.lastLongitude = lng;
  device.lastAddress = address || device.lastAddress;
  device.lastAccuracy = locationData.accuracy;
  device.lastAltitude = locationData.altitude;
  device.lastSpeed = locationData.speed;
  device.lastLocationTimestamp = ts;
  device.lastSeen = new Date();
  device.isOnline = true;
  await device.save();

  cleanupOldLocations(deviceId, userId).catch(console.error);

  const payload = {
    deviceId,
    latitude: lat,
    longitude: lng,
    address: device.lastAddress,
    accuracy: locationData.accuracy,
    altitude: locationData.altitude,
    speed: locationData.speed,
    timestamp: ts,
  };

  emitToUser(io, userId, 'device:location', payload);

  return { location, message: 'Location recorded', payload };
}

async function getLocationHistory(userId, deviceId, query = {}) {
  const { date, page = 1, limit = 500 } = query;

  const device = await Device.findOne({ deviceId, userId });
  if (!device) {
    const error = new Error('Device not found');
    error.status = 404;
    throw error;
  }

  const dbQuery = { deviceId, userId };
  if (date) {
    const start = new Date(date);
    if (Number.isNaN(start.getTime())) {
      const error = new Error('Invalid date format. Use YYYY-MM-DD');
      error.status = 400;
      throw error;
    }
    const end = new Date(start);
    end.setDate(end.getDate() + 1);
    dbQuery.timestamp = { $gte: start, $lt: end };
  }

  const pageNum = Math.max(1, parseInt(page, 10) || 1);
  const limitNum = Math.min(1000, Math.max(1, parseInt(limit, 10) || 500));
  const skip = (pageNum - 1) * limitNum;

  const [locations, total] = await Promise.all([
    Location.find(dbQuery).sort({ timestamp: 1 }).skip(skip).limit(limitNum).lean(),
    Location.countDocuments(dbQuery),
  ]);

  const startTime = locations.length > 0 ? locations[0].timestamp : null;
  const endTime = locations.length > 0 ? locations[locations.length - 1].timestamp : null;

  return {
    locations,
    pagination: {
      page: pageNum,
      limit: limitNum,
      total,
      pages: Math.ceil(total / limitNum),
    },
    summary: {
      pointCount: locations.length,
      startTime,
      endTime,
    },
  };
}

async function recordStatus(userId, deviceId, data, io) {
  const { isOnline, networkType, wifiAvailable, mobileDataAvailable } = data;

  const device = await Device.findOne({ deviceId, userId });
  if (!device) {
    const error = new Error('Device not found');
    error.status = 404;
    throw error;
  }

  const online = typeof isOnline === 'boolean' ? isOnline : true;
  device.isOnline = online;
  device.networkType = sanitizeString(networkType || device.networkType, 50);
  device.wifiAvailable = Boolean(wifiAvailable);
  device.mobileDataAvailable = Boolean(mobileDataAvailable);
  device.lastSeen = new Date();
  await device.save();

  await DeviceStatus.create({
    deviceId,
    userId,
    isOnline: online,
    networkType: device.networkType,
    wifiAvailable: device.wifiAvailable,
    mobileDataAvailable: device.mobileDataAvailable,
    timestamp: new Date(),
  });

  const payload = {
    deviceId,
    isOnline: online,
    networkType: device.networkType,
    wifiAvailable: device.wifiAvailable,
    mobileDataAvailable: device.mobileDataAvailable,
    lastSeen: device.lastSeen,
  };

  const event = online ? 'device:online' : 'device:offline';
  emitToUser(io, userId, event, payload);
  emitToUser(io, userId, 'device:status', payload);

  return { device, message: 'Status updated', payload };
}

async function recordBattery(userId, deviceId, data, io) {
  const { batteryPercentage, isCharging, batteryTemperature, batteryHealth } = data;

  const device = await Device.findOne({ deviceId, userId });
  if (!device) {
    const error = new Error('Device not found');
    error.status = 404;
    throw error;
  }

  const pct = parseNumber(batteryPercentage, 0, 100);
  if (pct !== null) device.batteryPercentage = pct;
  if (typeof isCharging === 'boolean') device.isCharging = isCharging;
  const temp = parseNumber(batteryTemperature, -50, 150);
  if (temp !== null) device.batteryTemperature = temp;
  if (batteryHealth) device.batteryHealth = sanitizeString(batteryHealth, 50);
  device.lastSeen = new Date();
  await device.save();

  const payload = {
    deviceId,
    batteryPercentage: device.batteryPercentage,
    isCharging: device.isCharging,
    batteryTemperature: device.batteryTemperature,
    batteryHealth: device.batteryHealth,
    lastSeen: device.lastSeen,
  };

  emitToUser(io, userId, 'device:battery', payload);

  return { device, message: 'Battery updated', payload };
}

module.exports = {
  registerDevice,
  getDevices,
  getDevice,
  recordLocation,
  getLocationHistory,
  recordStatus,
  recordBattery,
  markStaleDevicesOffline,
  isDeviceEffectivelyOnline,
};
