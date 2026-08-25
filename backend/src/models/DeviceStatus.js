const mongoose = require('mongoose');

const deviceStatusSchema = new mongoose.Schema(
  {
    deviceId: {
      type: String,
      required: true,
      index: true,
    },
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true,
    },
    isOnline: { type: Boolean, default: false },
    networkType: { type: String, default: 'unknown' },
    wifiAvailable: { type: Boolean, default: false },
    mobileDataAvailable: { type: Boolean, default: false },
    timestamp: { type: Date, default: Date.now },
  },
  { timestamps: true }
);

deviceStatusSchema.index({ deviceId: 1, timestamp: -1 });

module.exports = mongoose.model('DeviceStatus', deviceStatusSchema);
