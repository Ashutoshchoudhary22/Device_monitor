const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const User = require('../models/User');
const config = require('../config');
const { isValidEmail, sanitizeString } = require('../utils/validation');
const {
  createRegistrationOtp,
  verifyRegistrationOtp,
  resendRegistrationOtp,
} = require('../services/otpService');
const { isEmailConfigured } = require('../services/email');

async function register(req, res, next) {
  try {
    const { email, password, name } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required' });
    }

    if (!isValidEmail(email)) {
      return res.status(400).json({ error: 'Invalid email format' });
    }

    if (password.length < 6) {
      return res.status(400).json({ error: 'Password must be at least 6 characters' });
    }

    if (!isEmailConfigured()) {
      return res.status(503).json({ error: 'Email service is not configured on the server' });
    }

    const normalizedEmail = email.toLowerCase();
    const existing = await User.findOne({ email: normalizedEmail });
    if (existing) {
      return res.status(409).json({ error: 'Email already registered' });
    }

    const hashed = await bcrypt.hash(password, 12);
    const result = await createRegistrationOtp(
      normalizedEmail,
      hashed,
      sanitizeString(name || '', 100)
    );

    res.status(200).json(result);
  } catch (err) {
    if (err.message?.includes('Email service')) {
      return res.status(503).json({ error: err.message });
    }
    next(err);
  }
}

async function verifyOtp(req, res, next) {
  try {
    const { email, otp } = req.body;

    if (!email || !otp) {
      return res.status(400).json({ error: 'Email and OTP are required' });
    }

    if (!isValidEmail(email)) {
      return res.status(400).json({ error: 'Invalid email format' });
    }

    const normalizedEmail = email.toLowerCase();
    const existing = await User.findOne({ email: normalizedEmail });
    if (existing) {
      return res.status(409).json({ error: 'Email already registered' });
    }

    const verified = await verifyRegistrationOtp(normalizedEmail, String(otp).trim());

    const user = await User.create({
      email: verified.email,
      password: verified.passwordHash,
      name: verified.name,
    });

    const token = jwt.sign({ id: user._id, email: user.email }, config.jwtSecret, {
      expiresIn: config.jwtExpiresIn,
    });

    res.status(201).json({
      token,
      user: { id: user._id, email: user.email, name: user.name },
      message: 'Account verified and created successfully',
    });
  } catch (err) {
    if (err.status) {
      return res.status(err.status).json({ error: err.message });
    }
    next(err);
  }
}

async function resendOtp(req, res, next) {
  try {
    const { email } = req.body;

    if (!email) {
      return res.status(400).json({ error: 'Email is required' });
    }

    if (!isValidEmail(email)) {
      return res.status(400).json({ error: 'Invalid email format' });
    }

    const result = await resendRegistrationOtp(email.toLowerCase());
    res.json(result);
  } catch (err) {
    if (err.status) {
      return res.status(err.status).json({ error: err.message });
    }
    next(err);
  }
}

async function login(req, res, next) {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required' });
    }

    const user = await User.findOne({ email: email.toLowerCase() });
    if (!user) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const valid = await bcrypt.compare(password, user.password);
    if (!valid) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    const token = jwt.sign({ id: user._id, email: user.email }, config.jwtSecret, {
      expiresIn: config.jwtExpiresIn,
    });

    res.json({
      token,
      user: { id: user._id, email: user.email, name: user.name },
    });
  } catch (err) {
    next(err);
  }
}

module.exports = { register, verifyOtp, resendOtp, login };
