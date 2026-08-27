const mongoose = require('mongoose');

const locationSchema = new mongoose.Schema(
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
    latitude: { type: Number, required: true },
    longitude: { type: Number, required: true },
    address: { type: String, default: null },
    accuracy: { type: Number, default: null },
    altitude: { type: Number, default: null },
    speed: { type: Number, default: null },
    timestamp: { type: Date, required: true, index: true },
  },
  { timestamps: true }
);

locationSchema.index({ deviceId: 1, timestamp: -1 });
locationSchema.index({ userId: 1, deviceId: 1, timestamp: -1 });

module.exports = mongoose.model('Location', locationSchema);
