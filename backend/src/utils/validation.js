const validator = require('validator');

function sanitizeString(str, maxLen = 255) {
  if (typeof str !== 'string') return '';
  return validator.escape(str.trim().slice(0, maxLen));
}

function isValidEmail(email) {
  return validator.isEmail(email);
}

function isValidDeviceId(deviceId) {
  return typeof deviceId === 'string' && /^[a-zA-Z0-9_-]{8,64}$/.test(deviceId);
}

function parseNumber(val, min, max) {
  const n = Number(val);
  if (Number.isNaN(n)) return null;
  if (min !== undefined && n < min) return null;
  if (max !== undefined && n > max) return null;
  return n;
}

module.exports = {
  sanitizeString,
  isValidEmail,
  isValidDeviceId,
  parseNumber,
};
