# Security Specification — Tic-Tac-Toe Pro

## 1. Data Invariants

1. **Default-Deny Catch-All**: All paths not explicitly matched are denied (`allow read, write: if false;`).
2. **User Profile & PII Isolation (`/users/{uid}`)**:
   - Only the authenticated owner (`request.auth.uid == uid`) can `get`, `list`, `create`, `update`, or `delete` their user document.
   - `userId` and `createdAt` are immutable after creation.
   - `verifiedPhoneNumber` is optional and isolated to the owner only.
3. **Online Multiplayer Rooms (`/rooms/{roomId}`)**:
   - Only authenticated users can `get` a room by its valid `roomId` or `create` a new room where `hostUid == request.auth.uid`.
   - `list` queries are restricted to rooms where the caller is `hostUid` or `guestUid`.
   - `update` is strictly partitioned into 4 action gates (`Join`, `Move`, `Rematch`, `Presence/Abandon`), always wrapped by `isValidRoom(incoming())`, preserving immutable `hostUid` and `createdAt`.
   - Client moves are validated for board length (`size() == 9`), valid turn symbols (`"X"`, `"O"`), and room membership (`hostUid` or `guestUid`).
4. **Game Records (`/games/{gameId}`)**:
   - Strictly isolated to the owner (`userId == request.auth.uid`) for all CRUD and `list` operations.
   - Terminal State Locking: Once `existing().status == "finished"`, further updates to the game record are rejected.
5. **Privacy & Contact Discovery Consents (`/consents/{uid}`)**:
   - Strictly isolated to the authenticated user (`request.auth.uid == uid`).
   - Validates `consentPurpose`, `policyVersion`, boolean flags, and server timestamps.
6. **Server-Managed Contact Discovery Vault (`/contactDiscovery/{recordId}`)**:
   - Strictly closed to all client SDK access (`allow read, write: if false;`). Managed exclusively by trusted Cloud Functions using the Admin SDK.
7. **User Discovery Audit Batches (`/users/{uid}/discoveryBatches/{batchId}`)**:
   - Strictly isolated to `request.auth.uid == uid` so users can inspect and delete their submitted privacy-preserving token batches.

## 2. The "Dirty Dozen" Adversarial Payloads

1. **Unauthenticated Read**: `unauthDb.collection("users").doc("alice").get()` -> REJECTED.
2. **Cross-User PII Read**: Bob attempts `bobDb.collection("users").doc("alice").get()` -> REJECTED.
3. **Identity Spoofing on Create**: Alice attempts to create `/games/game_1` with `userId: "bob"` -> REJECTED.
4. **Shadow Update (Ghost Field Injection)**: Alice updates `/users/alice` with `isAdmin: true` -> REJECTED via `affectedKeys().hasOnly(...)` and `keys().hasOnly(...)`.
5. **Immortal Field Mutation**: Alice attempts to change `createdAt` or `hostUid` on update -> REJECTED.
6. **Terminal State Bypass**: Alice attempts to update `/games/game_1` after `status == "finished"` -> REJECTED.
7. **Resource / ID Poisoning**: Attacker attempts to create a room with a 500-character or special-character ID -> REJECTED by `isValidId()`.
8. **Value Poisoning**: Attacker updates `displayName` on `/users/alice` with a 10,000-character string or integer -> REJECTED by `isValidUserProfile()`.
9. **Unauthorized Room Takeover**: Third user Charlie attempts to make a move in a room where `hostUid == "alice"` and `guestUid == "bob"` -> REJECTED.
10. **Full Room Overwrite**: Charlie attempts to join a room where `status == "playing"` and `guestUid == "bob"` -> REJECTED.
11. **Unfiltered Collection Scraping**: Alice executes `aliceDb.collection("games").get()` without `.where("userId", "==", "alice")` -> REJECTED.
12. **Direct Client Access to Contact Discovery Vault**: Alice attempts to read or write `/contactDiscovery/hash_123` -> REJECTED (`allow read, write: if false`).
