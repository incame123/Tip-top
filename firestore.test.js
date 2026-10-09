const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

const nowTimestamp = () => new Date(Date.now() - 1000);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// 1. Unauthenticated rejection
test("Unauthenticated user: cannot read or write users, rooms, games, or consents", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("rooms").doc("ROOM01").get());
  await assertFails(unauthDb.collection("games").doc("game_1").get());
  await assertFails(unauthDb.collection("consents").doc(ALICE_UID).get());
});

// 2. User profile & PII isolation
test("User profile: owner can create and read own profile, non-owner is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Pro",
      verifiedPhoneNumber: "+14155550101",
      contactDiscoveryEnabled: true,
      serverMatchingConsent: false,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  // Bob cannot read Alice's PII profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  // Shadow update with ghost field is rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Hacked",
      isAdmin: true,
      updatedAt: nowTimestamp(),
    })
  );
});

// 3. Online Multiplayer Room creation, joining, turn validation, and unauthorized move prevention
test("Online Room: host creates room, guest joins, players take turns in order, intruder fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const roomRefAlice = aliceDb.collection("rooms").doc("ROOM99");
  await assertSucceeds(
    roomRefAlice.set({
      roomId: "ROOM99",
      hostUid: ALICE_UID,
      hostName: "Alice",
      guestUid: "",
      guestName: "",
      board: ["", "", "", "", "", "", "", "", ""],
      currentTurn: "X",
      status: "waiting",
      winner: "",
      winningCells: [],
      hostScore: 0,
      guestScore: 0,
      draws: 0,
      roundNumber: 1,
      hostConnected: true,
      guestConnected: false,
      expiresAt: Date.now() + 3600000,
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  // Guest Bob joins room
  const roomRefBob = bobDb.collection("rooms").doc("ROOM99");
  await assertSucceeds(
    roomRefBob.update({
      guestUid: BOB_UID,
      guestName: "Bob",
      guestConnected: true,
      status: "playing",
      updatedAt: nowTimestamp(),
    })
  );

  // Charlie cannot join a full room
  await assertFails(
    charlieDb.collection("rooms").doc("ROOM99").update({
      guestUid: CHARLIE_UID,
      guestName: "Charlie",
      guestConnected: true,
      status: "playing",
      updatedAt: nowTimestamp(),
    })
  );

  // Bob (O) cannot move on X's turn
  await assertFails(
    roomRefBob.update({
      board: ["O", "", "", "", "", "", "", "", ""],
      currentTurn: "X",
      updatedAt: nowTimestamp(),
    })
  );

  // Alice (X) makes valid move on X's turn
  await assertSucceeds(
    roomRefAlice.update({
      board: ["X", "", "", "", "", "", "", "", ""],
      currentTurn: "O",
      updatedAt: nowTimestamp(),
    })
  );
});

// 4. Games collection: owner query alignment & terminal state locking
test("Games collection: enforces userId filter and terminal state locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("games").doc("game_101").set({
      gameId: "game_101",
      userId: ALICE_UID,
      playerUids: [ALICE_UID],
      board: ["X", "X", "X", "O", "O", "", "", "", ""],
      currentTurn: "O",
      status: "finished",
      result: "X_WON",
      mode: "ROBOT_HARD",
      createdAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  // Filtered query succeeds
  await assertSucceeds(
    aliceDb.collection("games").where("userId", "==", ALICE_UID).get()
  );

  // Unfiltered query fails
  await assertFails(aliceDb.collection("games").get());

  // Cross-user read fails
  await assertFails(bobDb.collection("games").doc("game_101").get());

  // Terminal state lock prevents modifying finished game
  await assertFails(
    aliceDb.collection("games").doc("game_101").update({
      result: "O_WON",
      updatedAt: nowTimestamp(),
    })
  );
});

// 5. Consents & Contact Discovery isolation
test("Consents & Contact Discovery: owner manages consent, direct contactDiscovery vault is locked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("consents").doc(ALICE_UID).set({
      userId: ALICE_UID,
      consentPurpose: "contact_discovery_matching",
      policyVersion: "1.0.0",
      localContactsGranted: true,
      serverMatchingGranted: true,
      grantedAt: nowTimestamp(),
      updatedAt: nowTimestamp(),
    })
  );

  await assertFails(bobDb.collection("consents").doc(ALICE_UID).get());

  // Direct access to server-only contactDiscovery collection is strictly forbidden
  await assertFails(aliceDb.collection("contactDiscovery").doc("token_1").get());
  await assertFails(
    aliceDb.collection("contactDiscovery").doc("token_1").set({ owner: ALICE_UID })
  );
});
