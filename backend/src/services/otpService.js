const crypto = require('crypto');
const bcrypt = require('bcryptjs');
const Otp = require('../models/Otp');
const config = require('../config');
const { sendOtpEmail } = require('./email');

function generateOtpCode() {
  return String(crypto.randomInt(100000, 999999));
}

function hashOtp(code) {
  return crypto.createHash('sha256').update(code).digest('hex');
}

async function createRegistrationOtp(email, passwordHash, name) {
  const code = generateOtpCode();
  const codeHash = hashOtp(code);
  const expiresAt = new Date(Date.now() + config.otpExpiryMinutes * 60 * 1000);

  await Otp.deleteMany({ email, purpose: 'register' });

  await Otp.create({
    email,
    codeHash,
    purpose: 'register',
    name,
    passwordHash,
    expiresAt,
    attempts: 0,
  });

  await sendOtpEmail(email, code, 'register');

  return { message: 'OTP sent to your email', email };
}

async function verifyRegistrationOtp(email, code) {
  const otpRecord = await Otp.findOne({
    email,
    purpose: 'register',
    expiresAt: { $gt: new Date() },
  });

  if (!otpRecord) {
    throw Object.assign(new Error('OTP expired or not found. Please register again.'), { status: 400 });
  }

  if (otpRecord.attempts >= config.otpMaxAttempts) {
    await Otp.deleteOne({ _id: otpRecord._id });
    throw Object.assign(new Error('Too many failed attempts. Please register again.'), { status: 400 });
  }

  const codeHash = hashOtp(code);
  if (codeHash !== otpRecord.codeHash) {
    otpRecord.attempts += 1;
    await otpRecord.save();
    throw Object.assign(new Error('Invalid OTP'), { status: 400 });
  }

  const result = {
    email: otpRecord.email,
    passwordHash: otpRecord.passwordHash,
    name: otpRecord.name,
  };

  await Otp.deleteOne({ _id: otpRecord._id });

  return result;
}

async function resendRegistrationOtp(email) {
  const otpRecord = await Otp.findOne({
    email,
    purpose: 'register',
    expiresAt: { $gt: new Date() },
  });

  if (!otpRecord) {
    throw Object.assign(new Error('No pending registration found. Please register again.'), { status: 400 });
  }

  const code = generateOtpCode();
  otpRecord.codeHash = hashOtp(code);
  otpRecord.expiresAt = new Date(Date.now() + config.otpExpiryMinutes * 60 * 1000);
  otpRecord.attempts = 0;
  await otpRecord.save();

  await sendOtpEmail(email, code, 'register');

  return { message: 'OTP resent to your email', email };
}

module.exports = {
  generateOtpCode,
  hashOtp,
  createRegistrationOtp,
  verifyRegistrationOtp,
  resendRegistrationOtp,
};
