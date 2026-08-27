const cache = new Map();
let lastRequestAt = 0;

function formatAddress(data) {
  const a = data.address || {};
  const parts = [
    a.house_number,
    a.road || a.neighbourhood || a.suburb,
    a.city || a.town || a.village || a.county,
    a.state,
    a.postcode,
    a.country,
  ].filter(Boolean);

  if (parts.length > 0) {
    return parts.join(', ');
  }

  return data.display_name || null;
}

async function reverseGeocode(latitude, longitude) {
  const lat = Number(latitude);
  const lng = Number(longitude);
  if (Number.isNaN(lat) || Number.isNaN(lng)) return null;

  const cacheKey = `${lat.toFixed(4)},${lng.toFixed(4)}`;
  if (cache.has(cacheKey)) {
    return cache.get(cacheKey);
  }

  const now = Date.now();
  const waitMs = Math.max(0, 1100 - (now - lastRequestAt));
  if (waitMs > 0) {
    await new Promise((resolve) => setTimeout(resolve, waitMs));
  }
  lastRequestAt = Date.now();

  try {
    const url = new URL('https://nominatim.openstreetmap.org/reverse');
    url.searchParams.set('lat', String(lat));
    url.searchParams.set('lon', String(lng));
    url.searchParams.set('format', 'json');
    url.searchParams.set('addressdetails', '1');
    url.searchParams.set('zoom', '18');

    const response = await fetch(url, {
      headers: {
        'User-Agent': 'DeviceMonitor/1.0 (contact@faltu.shop)',
        Accept: 'application/json',
      },
    });

    if (!response.ok) return null;

    const data = await response.json();
    const address = formatAddress(data);
    if (address) {
      cache.set(cacheKey, address);
    }
    return address;
  } catch (err) {
    console.error('Reverse geocoding failed:', err.message);
    return null;
  }
}

module.exports = { reverseGeocode };
