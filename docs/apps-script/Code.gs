/**
 * Reconstructed JAGAQ email endpoint draft.
 *
 * This is not the recovered original source. The legacy OTP request contract is
 * preserved as inferred from the Java client: {email, otp, secret}. A reset
 * request is a separate operation using {messageType, email, resetUrl, secret}.
 * No credentials or message secrets belong in this source file.
 */

const CONFIG = Object.freeze({
  secretProperty: 'OTP_EMAIL_API_SECRET',
  resetHostsProperty: 'PASSWORD_RESET_ALLOWED_HOSTS',
  appNameProperty: 'JAGAQ_APP_NAME',
  otpExpiryMinutesProperty: 'OTP_EXPIRATION_MINUTES',
  otpMaxPerEmailHourProperty: 'OTP_MAX_PER_EMAIL_PER_HOUR',
  resetMaxPerEmailHourProperty: 'RESET_MAX_PER_EMAIL_PER_HOUR',
  rateWindowMs: 60 * 60 * 1000,
  defaultOtpExpiryMinutes: 10,
  defaultOtpMaxPerEmailHour: 5,
  defaultResetMaxPerEmailHour: 3
});

function doPost(e) {
  try {
    const payload = parsePayload_(e);
    const props = PropertiesService.getScriptProperties();
    const configuredSecret = props.getProperty(CONFIG.secretProperty) || '';

    if (!configuredSecret || !constantTimeEquals_(String(payload.secret || ''), configuredSecret)) {
      return jsonResponse_(false, 'unauthorized');
    }

    const email = String(payload.email || '').trim();
    if (!isValidEmail_(email)) {
      return jsonResponse_(false, 'invalid_request');
    }

    // Legacy requests have no operation discriminator and continue to use the
    // original {email, otp, secret} shape.
    if (payload.messageType === undefined || payload.messageType === null || payload.messageType === '') {
      return sendOtp_(payload, email, props);
    }
    if (payload.messageType === 'password_reset') {
      return sendPasswordReset_(payload, email, props);
    }
    return jsonResponse_(false, 'invalid_request');
  } catch (_err) {
    // Do not log exceptions: they can contain request or provider data.
    return jsonResponse_(false, 'delivery_failed');
  }
}

function sendOtp_(payload, email, props) {
  const otp = String(payload.otp || '');
  if (!/^\d{6}$/.test(otp)) {
    return jsonResponse_(false, 'invalid_request');
  }

  const limit = positiveInt_(props.getProperty(CONFIG.otpMaxPerEmailHourProperty),
    CONFIG.defaultOtpMaxPerEmailHour);
  if (!reserveEmailAttempt_(email, 'otp', limit)) {
    return jsonResponse_(false, 'rate_limited');
  }
  if (!hasMailQuota_()) {
    return jsonResponse_(false, 'quota_exceeded');
  }

  const minutes = positiveInt_(props.getProperty(CONFIG.otpExpiryMinutesProperty),
    CONFIG.defaultOtpExpiryMinutes);
  const appName = props.getProperty(CONFIG.appNameProperty) || 'JAGAQ';
  const subject = appName + ' registration verification code';
  const text = 'Your ' + appName + ' verification code is ' + otp +
    '. It expires in ' + minutes + ' minutes. If you did not request this code, you can ignore this email.';
  const html = '<p>Your ' + escapeHtml_(appName) + ' verification code is:</p>' +
    '<p style="font-size:24px;font-weight:bold;letter-spacing:4px">' + otp + '</p>' +
    '<p>This code expires in ' + minutes + ' minutes. If you did not request it, you can ignore this email.</p>';

  try {
    MailApp.sendEmail({ to: email, subject: subject, body: text, htmlBody: html });
    return jsonResponse_(true, 'sent');
  } catch (_err) {
    return jsonResponse_(false, 'delivery_failed');
  }
}

function sendPasswordReset_(payload, email, props) {
  const resetUrl = String(payload.resetUrl || '');
  if (!isAllowedResetUrl_(resetUrl, props.getProperty(CONFIG.resetHostsProperty) || '')) {
    return jsonResponse_(false, 'invalid_request');
  }

  const limit = positiveInt_(props.getProperty(CONFIG.resetMaxPerEmailHourProperty),
    CONFIG.defaultResetMaxPerEmailHour);
  if (!reserveEmailAttempt_(email, 'reset', limit)) {
    return jsonResponse_(false, 'rate_limited');
  }
  if (!hasMailQuota_()) {
    return jsonResponse_(false, 'quota_exceeded');
  }

  const appName = props.getProperty(CONFIG.appNameProperty) || 'JAGAQ';
  const subject = appName + ' password reset';
  const text = 'A password reset was requested for your ' + appName + ' account. ' +
    'Use the link below to choose a new password. If you did not request this, ignore this email.\n\n' +
    resetUrl + '\n\nFor your security, do not share this link.';
  const html = '<p>A password reset was requested for your ' + escapeHtml_(appName) + ' account.</p>' +
    '<p><a href="' + escapeHtml_(resetUrl) + '">Reset your password</a></p>' +
    '<p>If you did not request this, you can ignore this email. For your security, do not share this link.</p>';

  try {
    MailApp.sendEmail({ to: email, subject: subject, body: text, htmlBody: html });
    return jsonResponse_(true, 'sent');
  } catch (_err) {
    return jsonResponse_(false, 'delivery_failed');
  }
}

function parsePayload_(e) {
  if (!e || !e.postData || typeof e.postData.contents !== 'string') {
    throw new Error('Invalid request');
  }
  const parsed = JSON.parse(e.postData.contents);
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('Invalid request');
  }
  return parsed;
}

function isValidEmail_(email) {
  return email.length <= 254 && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function isAllowedResetUrl_(value, allowedHostsSetting) {
  if (!value || value.length > 4096 || /[\\\s\u0000-\u001F\u007F]/.test(value)
      || /%(?![0-9a-fA-F]{2})/.test(value)) return false;
  // The reset link must target the reset endpoint and contain exactly one
  // URL-safe token. This rejects userinfo, ports, fragments, backslashes,
  // authority ambiguities, arbitrary paths, and unexpected query parameters.
  const match = value.match(/^https:\/\/([A-Za-z0-9.-]+)(\/(?:[A-Za-z0-9_-]+\/)*reset-password)(\?token=([A-Za-z0-9_-]{43}))$/i);
  if (!match) return false;
  const host = match[1].toLowerCase();
  if (host.length > 253 || host.split('.').some(function (label) {
    return !label || label.length > 63 || !/^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/.test(label);
  })) return false;
  if (!match[4]) return false;
  const allowedHosts = allowedHostsSetting.split(',')
    .map(function (item) { return item.trim().toLowerCase(); })
    .filter(function (item) {
      return item && item.length <= 253 && item.split('.').every(function (label) {
        return label.length > 0 && label.length <= 63
          && /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/.test(label);
      });
    });
  return allowedHosts.length > 0 && allowedHosts.indexOf(host) !== -1;
}

function hasMailQuota_() {
  try {
    return MailApp.getRemainingDailyQuota() > 0;
  } catch (_err) {
    return false;
  }
}

/** Reserves a per-recipient, per-operation attempt without storing the address. */
function reserveEmailAttempt_(email, operation, maxPerWindow) {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(5000)) return false;
  try {
    const cache = CacheService.getScriptCache();
    const addressDigest = Utilities.computeDigest(
      Utilities.DigestAlgorithm.SHA_256, email.toLowerCase(), Utilities.Charset.UTF_8);
    const key = 'RATE_' + operation + '_' + Utilities.base64EncodeWebSafe(addressDigest);
    const now = Date.now();
    let timestamps = [];
    const stored = cache.get(key);
    if (stored) {
      try {
        timestamps = JSON.parse(stored).filter(function (time) {
          return Number.isFinite(time) && now - time < CONFIG.rateWindowMs;
        });
      } catch (_err) {
        timestamps = [];
      }
    }
    if (timestamps.length >= maxPerWindow) return false;
    timestamps.push(now);
    cache.put(key, JSON.stringify(timestamps), Math.ceil(CONFIG.rateWindowMs / 1000));
    return true;
  } finally {
    lock.releaseLock();
  }
}

function constantTimeEquals_(left, right) {
  const a = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, left, Utilities.Charset.UTF_8);
  const b = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, right, Utilities.Charset.UTF_8);
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
  return difference === 0;
}

function positiveInt_(value, fallback) {
  const number = Number(value);
  return Number.isInteger(number) && number > 0 ? number : fallback;
}

function escapeHtml_(value) {
  return String(value).replace(/[&<>"']/g, function (char) {
    return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char];
  });
}

function jsonResponse_(success, error) {
  const result = { success: success };
  if (!success) result.error = error;
  return ContentService.createTextOutput(JSON.stringify(result))
    .setMimeType(ContentService.MimeType.JSON);
}
