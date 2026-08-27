const deviceEvents = require('../services/deviceEvents');

async function registerDevice(req, res, next) {
  try {
    const result = await deviceEvents.registerDevice(req.user.id, req.body);
    const status = result.message === 'Device registered' ? 201 : 200;
    res.status(status).json(result);
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
    next(err);
  }
}

async function getDevices(req, res, next) {
  try {
    const result = await deviceEvents.getDevices(req.user.id);
    res.json(result);
  } catch (err) {
    next(err);
  }
}

async function getDevice(req, res, next) {
  try {
    const result = await deviceEvents.getDevice(req.user.id, req.params.deviceId);
    res.json(result);
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
    next(err);
  }
}

async function postLocation(req, res, next) {
  try {
    const result = await deviceEvents.recordLocation(
      req.user.id,
      req.params.deviceId,
      req.body,
      req.app.locals.io
    );
    res.status(201).json({ location: result.location, message: result.message });
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
    next(err);
  }
}

async function getLocationHistory(req, res, next) {
  try {
    const result = await deviceEvents.getLocationHistory(
      req.user.id,
      req.params.deviceId,
      req.query
    );
    res.json(result);
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
    next(err);
  }
}

async function postStatus(req, res, next) {
  try {
    const result = await deviceEvents.recordStatus(
      req.user.id,
      req.params.deviceId,
      req.body,
      req.app.locals.io
    );
    res.json({ device: result.device, message: result.message });
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
    next(err);
  }
}

async function postBattery(req, res, next) {
  try {
    const result = await deviceEvents.recordBattery(
      req.user.id,
      req.params.deviceId,
      req.body,
      req.app.locals.io
    );
    res.json({ device: result.device, message: result.message });
  } catch (err) {
    if (err.status) return res.status(err.status).json({ error: err.message });
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
