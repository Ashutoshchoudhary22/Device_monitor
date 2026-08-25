const nodemailer = require('nodemailer');
const config = require('../config');

let transporter = null;

function getTransporter() {
  if (!transporter) {
    transporter = nodemailer.createTransport({
      host: config.email.host,
      port: config.email.port,
      secure: config.email.port === 465,
      auth: {
        user: config.email.user,
        pass: config.email.password,
      },
    });
  }
  return transporter;
}

function isEmailConfigured() {
  return Boolean(config.email.user && config.email.password && config.email.from);
}

async function sendOtpEmail(email, otp, purpose = 'register') {
  if (!isEmailConfigured()) {
    throw new Error('Email service is not configured. Set EMAIL_USER and EMAIL_PASSWORD in .env');
  }

  const subject =
    purpose === 'register'
      ? 'Device Monitor - Registration OTP'
      : 'Device Monitor - Verification OTP';

  const html = `
    <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto; padding: 24px;">
      <h2 style="color: #2563eb;">Device Monitor</h2>
      <p>Your OTP for account registration is:</p>
      <p style="font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #111;">${otp}</p>
      <p style="color: #666;">This OTP is valid for ${config.otpExpiryMinutes} minutes. Do not share it with anyone.</p>
      <p style="color: #999; font-size: 12px;">If you did not request this, please ignore this email.</p>
    </div>
  `;

  const transport = getTransporter();
  await transport.sendMail({
    from: config.email.from,
    to: email,
    subject,
    html,
    text: `Your Device Monitor registration OTP is: ${otp}. Valid for ${config.otpExpiryMinutes} minutes.`,
  });
}

module.exports = { sendOtpEmail, isEmailConfigured };
