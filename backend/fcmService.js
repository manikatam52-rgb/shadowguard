const admin = require('firebase-admin');

let fcmInitialized = false;

try {
  if (process.env.FIREBASE_SERVICE_ACCOUNT_JSON) {
    const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
    fcmInitialized = true;
    console.log('[FCM] Firebase Cloud Messaging initialized successfully');
  } else {
    console.log('[FCM] Firebase credentials not set; push notifications will run in mock mode');
  }
} catch (e) {
  console.warn('[FCM] Warning initializing Firebase Admin:', e.message);
}

async function sendPushNotification(fcmToken, dataPayload) {
  if (!fcmToken) return false;

  if (fcmInitialized) {
    try {
      await admin.messaging().send({
        token: fcmToken,
        data: dataPayload
      });
      console.log('[FCM] Push notification sent to token:', fcmToken.substring(0, 10) + '...');
      return true;
    } catch (err) {
      console.error('[FCM] Error sending push notification:', err.message);
      return false;
    }
  } else {
    console.log('[FCM Mock] Push notification event queued for token:', fcmToken.substring(0, 10) + '...', dataPayload);
    return true;
  }
}

module.exports = { sendPushNotification };
