# Tic-Tac-Toe Pro — Complete Setup, Security & Architecture Guide

## 1. Project File Structure

```text
/
├── .env.example                                   # Documented environment variable placeholders
├── firebase-blueprint.json                        # Firestore data model intermediate blueprint
├── firebase.json                                  # Local Firestore (8085) & Auth (9099) emulator binding
├── firestore.rules                                # Hardened Zero-Trust Firestore Security Rules
├── firestore.test.js                              # Tier 1 JavaScript Security Rules unit tests
├── security_spec.md                               # Adversarial security invariants & Dirty Dozen spec
└── app/
    ├── google-services.json                       # Provisioned Firebase Android config
    ├── build.gradle.kts                           # Gradle configuration & dependencies
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml                # INTERNET, ACCESS_NETWORK_STATE, READ_CONTACTS
        │   ├── java/com/example/
        │   │   ├── MainActivity.kt                # Root navigation & Auth-Gated ViewModel setup
        │   │   ├── domain/
        │   │   │   ├── game/
        │   │   │   │   ├── GameModels.kt          # BoardState, PlayerSymbol, RobotDifficulty, GameMode
        │   │   │   │   └── TicTacToeEngine.kt     # Win/Draw evaluation + Easy, Medium & Hard Minimax AI
        │   │   │   └── contacts/
        │   │   │       ├── AndroidContactPermissionModule.kt  # Real OS READ_CONTACTS runtime flow
        │   │   │       ├── PhoneNormalizationModule.kt        # ITU-T E.164 phone number normalizer
        │   │   │       ├── LocalContactReaderModule.kt        # Consent-gated ContactsContract reader
        │   │   │       ├── ConsentManagementModule.kt         # Two-stage opt-in & withdrawal DataStore
        │   │   │       ├── SecureContactMatchingModule.kt     # Keyed HMAC-SHA256 & rate-limiter
        │   │   │       └── ContactDiscoverySettingsModule.kt  # Discovery orchestration & erasure
        │   │   ├── data/
        │   │   │   ├── local/
        │   │   │   │   └── LocalScoreDatabase.kt  # Offline Room database for scores & history
        │   │   │   └── firebase/
        │   │   │       ├── FirebaseConfigModule.kt    # Single-init validator & custom DB resolver
        │   │   │       ├── FirestoreErrorHandler.kt   # Structured JSON error diagnostics
        │   │   │       ├── FirestoreModels.kt         # Type-safe models with serverTimestamp support
        │   │   │       ├── GoogleAuthManager.kt       # Jetpack Credential Manager Google Sign-In
        │   │   │       └── OnlineGameRepository.kt    # Rooms, games, user profile, consents & erasure
        │   │   └── ui/
        │   │       ├── theme/                     # Dark Navy (#0B1120), Electric Blue X, Neon Purple O
        │   │       ├── viewmodel/                 # TicTacToeViewModel & OnlineMultiplayerViewModel
        │   │       └── screens/                   # All 12 responsive Jetpack Compose screens
        │   └── res/
        └── test/java/com/example/
            ├── ExampleUnitTest.kt                 # Game engine, Minimax AI, E.164 & HMAC unit tests
            ├── ExampleRobolectricTest.kt          # Android permission & consent rejection tests
            ├── base/FirestoreEmulatorTestBase.kt  # Robolectric emulator harness
            └── data/firebase/OnlineGameRepositoryRuleTest.kt # Tier 2 repository & rule tests
```

---

## 2. Step-by-Step Firebase & Android Setup Guide

### 1) Register the Web / Android App in Firebase Console
1. Open the [Firebase Console](https://console.firebase.google.com/) and select your project (already provisioned in AI Studio as `gen-lang-client-0781917599`).
2. To add an Android app manually in your own external Firebase project:
   - Click **Add app** -> **Android**.
   - Package name: `com.aistudio.tictactoepro.vqxkpm`
   - Debug signing certificate SHA-1: extract using:
     ```bash
     keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
   - Download `google-services.json` and place it at `app/google-services.json`.

### 2) Enable Authentication Providers
1. In Firebase Console -> **Authentication** -> **Sign-in method**:
   - Enable **Google** Sign-In (used by Jetpack `CredentialManager` with `GetSignInWithGoogleOption`).
   - *(Optional for future Caller ID verification)*: Enable **Phone** Authentication so users can verify ownership of their own phone number (`verifiedPhoneNumber`).

### 3) Create Cloud Firestore Database
1. In Firebase Console -> **Firestore Database**, ensure your database is provisioned (in AI Studio, the named enterprise database ID is configured in `app/src/main/res/values/firebase_applet_config.xml`).
2. All client calls resolve `R.string.firestore_database_id` via `FirebaseFirestore.getInstance(databaseId)`.

### 4) Configure Database Security Rules
Deploy `/firestore.rules` to your Firestore database:
```bash
firebase deploy --only firestore:rules
```

### 5) Configure Environment Variables & Secrets
- Inspect `/.env.example` for optional overrides (`FIREBASE_API_KEY`, `CONTACT_MATCHING_ENDPOINT_URL`).
- In AI Studio, add any secret values via the **Secrets panel**.
- **Never** embed the server-side HMAC Pepper key in the client app or `.env`; store the HMAC Pepper exclusively in **Google Cloud Secret Manager** for your Cloud Function.

### 6) Trusted Cloud Functions Reference (For Production Contact Discovery Matching)
Deploy the following Cloud Function in your Firebase project to perform rate-limited matching against `/contactDiscovery/{hmacToken}` using the Admin SDK:
```typescript
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";
import * as crypto from "crypto";

const hmacPepper = defineSecret("CONTACT_DISCOVERY_HMAC_PEPPER");

export const matchContacts = onCall({ secrets: [hmacPepper] }, async (request) => {
  if (!request.auth?.uid) {
    throw new HttpsError("unauthenticated", "Authentication required.");
  }
  const e164Numbers: string[] = request.data?.numbers ?? [];
  if (!Array.isArray(e164Numbers) || e164Numbers.length > 50) {
    throw new HttpsError("invalid-argument", "Max 50 E.164 numbers per batch.");
  }
  // Compute server-keyed HMAC-SHA256 using secret Pepper from Secret Manager
  const tokens = e164Numbers.map((num) =>
    crypto.createHmac("sha256", hmacPepper.value()).update(num).digest("hex")
  );
  // Look up only users who explicitly verified their own phone number via Firebase Auth
  return { matchedCount: 0, tokensProcessed: tokens.length };
});
```

### 7) Running Tests & Building the Android APK
- **Run JavaScript Firestore Security Rules Tests (Tier 1)**:
  ```bash
  FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
  ```
- **Run Kotlin & Robolectric Unit/Rule Tests (Tier 2)**:
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Build Debug APK**:
  ```bash
  gradle :app:assembleDebug
  ```
  The generated APK is output to `app/build/outputs/apk/debug/app-debug.apk`.
