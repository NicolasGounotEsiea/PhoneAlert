const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

/**
 * Quand un ordre change dans groups/{groupId}.controls (un membre verrouille/débloque
 * un autre membre), on envoie un push FCM haute priorité à chaque cible concernée.
 * Le push réveille l'app de la cible même fermée.
 */
exports.relayControl = onDocumentUpdated(
  { document: "groups/{groupId}", region: "europe-west1" },
  async (event) => {
    const before = event.data?.before?.data() || {};
    const after = event.data?.after?.data() || {};
    const beforeControls = before.controls || {};
    const afterControls = after.controls || {};

    const tasks = [];
    for (const target of Object.keys(afterControls)) {
      const a = afterControls[target];
      const b = beforeControls[target];
      if (!a || !a.action) continue;
      if (b && b.at === a.at) continue; // ordre inchangé
      tasks.push(sendTo(target, a.action));
    }
    await Promise.all(tasks);
  }
);

async function sendTo(target, action) {
  const userDoc = await getFirestore().collection("users").doc(target).get();
  const token = userDoc.get("fcmToken");
  if (!token) {
    console.log("Aucun token FCM pour la cible", target);
    return;
  }
  await getMessaging().send({
    token,
    data: { action: String(action) },
    android: { priority: "high" },
  });
  console.log("Push FCM envoyé:", action, "-> cible", target);
}
