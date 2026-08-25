const Device = require('../models/Device');
const Location = require('../models/Location');
const DeviceStatus = require('../models/DeviceStatus');
const config = require('../config');
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

async function registerDevice(req, res, next) {
  try {
    const {
      deviceId,
      deviceName,
      androidVersion,
      manufacturer,
      model,
      appVersion,
      updateIntervalSeconds,
    } = req.body;

    if (!deviceId || !isValidDeviceId(deviceId)) {
      return res.status(400).json({ error: 'Valid deviceId is required (8-64 alphanumeric chars)' });
    }

    const existing = await Device.findOne({ deviceId });
    if (existing) {
      if (existing.userId.toString() !== req.user.id) {
        return res.status(409).json({ error: 'Device already registered to another account' });
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
      return res.json({ device: existing, message: 'Device updated' });
    }

    const device = await Device.create({
      userId: req.user.id,
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

    res.status(201).json({ device, message: 'Device registered' });
  } catch (err) {
    next(err);
  }
}

async function getDevices(req, res, next) {
  try {
    const devices = await Device.find({ userId: req.user.id }).sort({ lastSeen: -1 });
    res.json({ devices });
  } catch (err) {
    next(err);
  }
}

async function getDevice(req, res, next) {
  try {
    const { deviceId } = req.params;
    const device = await Device.findOne({ deviceId, userId: req.user.id });
    if (!device) {
      return res.status(404).json({ error: 'Device not found' });
    }
    res.json({ device });
  } catch (err) {
    next(err);
  }
}

async function postLocation(req, res, next) {
  try {
    const { deviceId } = req.params;
    const { latitude, longitude, accuracy, altitude, speed, timestamp } = req.body;

    const lat = parseNumber(latitude, -90, 90);
    const lng = parseNumber(longitude, -180, 180);
    if (lat === null || lng === null) {
      return res.status(400).json({ error: 'Valid latitude and longitude are required' });
    }

    const device = await Device.findOne({ deviceId, userId: req.user.id });
    if (!device) {
      return res.status(404).json({ error: 'Device not found' });
    }

    const ts = timestamp ? new Date(timestamp) : new Date();
    if (Number.isNaN(ts.getTime())) {
      return res.status(400).json({ error: 'Invalid timestamp' });
    }

    const locationData = {
      deviceId,
      userId: req.user.id,
      latitude: lat,
      longitude: lng,
      accuracy: parseNumber(accuracy, 0, 10000),
      altitude: parseNumber(altitude, -1000, 10000),
      speed: parseNumber(speed, 0, 500),
      timestamp: ts,
    };

    const location = await Location.create(locationData);

    device.lastLatitude = lat;
    device.lastLongitude = lng;
    device.lastAccuracy = locationData.accuracy;
    device.lastAltitude = locationData.altitude;
    device.lastSpeed = locationData.speed;
    device.lastLocationTimestamp = ts;
    device.lastSeen = new Date();
    device.isOnline = true;
    await device.save();

    cleanupOldLocations(deviceId, req.user.id).catch(console.error);

    const payload = {
      deviceId,
      latitude: lat,
      longitude: lng,
      accuracy: locationData.accuracy,
      altitude: locationData.altitude,
      speed: locationData.speed,
      timestamp: ts,
    };

    if (req.app.locals.io) {
      req.app.locals.io.to(`user:${req.user.id}`).emit('device:location', payload);
    }

    res.status(201).json({ location, message: 'Location recorded' });
  } catch (err) {
    next(err);
  }
}

async function getLocationHistory(req, res, next) {
  try {
    const { deviceId } = req.params;
    const { date, page = 1, limit = 500 } = req.query;

    const device = await Device.findOne({ deviceId, userId: req.user.id });
    if (!device) {
      return res.status(404).json({ error: 'Device not found' });
    }

    const query = { deviceId, userId: req.user.id };
    if (date) {
      const start = new Date(date);
      if (Number.isNaN(start.getTime())) {
        return res.status(400).json({ error: 'Invalid date format. Use YYYY-MM-DD' });
      }
      const end = new Date(start);
      end.setDate(end.getDate() + 1);
      query.timestamp = { $gte: start, $lt: end };
    }

    const pageNum = Math.max(1, parseInt(page, 10) || 1);
    const limitNum = Math.min(1000, Math.max(1, parseInt(limit, 10) || 500));
    const skip = (pageNum - 1) * limitNum;

    const [locations, total] = await Promise.all([
      Location.find(query).sort({ timestamp: 1 }).skip(skip).limit(limitNum),
      Location.countDocuments(query),
    ]);

    const startTime = locations.length > 0 ? locations[0].timestamp : null;
    const endTime = locations.length > 0 ? locations[locations.length - 1].timestamp : null;

    res.json({
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
    });
  } catch (err) {
    next(err);
  }
}

async function postStatus(req, res, next) {
  try {
    const { deviceId } = req.params;
    const { isOnline, networkType, wifiAvailable, mobileDataAvailable } = req.body;

    const device = await Device.findOne({ deviceId, userId: req.user.id });
    if (!device) {
      return res.status(404).json({ error: 'Device not found' });
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
      userId: req.user.id,
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

    if (req.app.locals.io) {
      const event = online ? 'device:online' : 'device:offline';
      req.app.locals.io.to(`user:${req.user.id}`).emit(event, payload);
      req.app.locals.io.to(`user:${req.user.id}`).emit('device:status', payload);
    }

    res.json({ device, message: 'Status updated' });
  } catch (err) {
    next(err);
  }
}

async function postBattery(req, res, next) {
  try {
    const { deviceId } = req.params;
    const { batteryPercentage, isCharging, batteryTemperature, batteryHealth } = req.body;

    const device = await Device.findOne({ deviceId, userId: req.user.id });
    if (!device) {
      return res.status(404).json({ error: 'Device not found' });
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

    if (req.app.locals.io) {
      req.app.locals.io.to(`user:${req.user.id}`).emit('device:battery', payload);
    }

    res.json({ device, message: 'Battery updated' });
  } catch (err) {
    next(err);
  }
}

module.exports = {
  registerDevice,
  getDevices,
  getDevice,
  postLocation,
  getLocationHistory,
  postStatus,
  postBattery,
};
