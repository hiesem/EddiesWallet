# EddiesWallet

**Authoritative v1 launch brief — requirements and product decisions**

EddiesWallet is a family learning space for recording **virtual allowance credits**. A parent records what happened; a child reads an understandable explanation of the balance, jars, activity, and amount owed. It is **not** a bank account, payment product, card, custody service, or money-moving service.

This README is the durable source of truth for implementation. It replaces the former prototype and draft PDR. It deliberately separates approved requirements, explicit boundaries, research recommendations, and decisions that are still owned by the captain. Do not turn a recommendation or a synthetic prototype value into an approved product rule without resolving it below.

## Status and vocabulary

- **Approved requirement/decision:** implementation must honor it.
- **Prototype direction:** approved for the synthetic-data spike, but not a production promise.
- **Recommendation:** a researched candidate; it is not approved merely because it is described here.
- **Open decision:** do not build the affected production behavior until the captain resolves it.
- **Production gate:** evidence or operational work required before real family data or launch.

The phrase **local-only** means **external-money-free**, not offline-only: cloud persistence and multi-device synchronization are required. No real money, bank, card, payment, custody, payout, or cash movement may be introduced to satisfy the cloud requirement.

## Decision summary

| Area | Current authoritative position |
| --- | --- |
| Product | Virtual bookkeeping and learning only; no real-money rail or custody. |
| Delivery | Android first, for parent phone and Eddie tablet/device. Linux is deferred. |
| Persistence | Cloud-backed durable family data with multi-device sync; local SQLite is a cache/outbox, not the authority. |
| Authority | Parent writes. Child access is read-only in the UI, API, and database. |
| Parent identity | An authenticated parent account is required. The current Android direction is Google Sign-In with server-side token verification; final provider/project setup remains a gate. |
| Child access | Scoped pairing to a family/member and device, with server-side revocation. A child does not need a Google account or child email. |
| Ledger | Immutable virtual-credit events; the server derives postings and balances. Corrections are new reversal events, never edits or deletes. |
| Prototype backend | Use the existing Hetzner VPS, self-hosted PostgreSQL, a small API, HTTPS/reverse proxy, and basic monitoring. This is approved for synthetic prototype work only. |
| Managed fallback | Supabase managed PostgreSQL/Auth/API is the managed fallback if the VPS proof or operator capacity fails. Separately operated PostgreSQL plus API remains a portability alternative. |
| UX | Dashboard-first (A) with an explicit journey path; virtual credits plus jars; Balance plus Save Jar for Eddie; visible read-only boundary with write controls hidden; quick amount plus resulting-balance preview; just-in-time education. |
| Data | Prototype and research fixtures are synthetic. No production recovery promise exists until the recovery gate is funded, designed, and tested. |

## 1. Product contract: what must be true

### 1.1 Virtual-only boundary

EddiesWallet records **virtual allowance for learning**. Credits are a practice representation of an amount a parent records, not legal tender or a redeemable stored value.

Implementation must:

- establish the virtual-only boundary before showing a meaningful amount;
- use language such as “virtual credits,” “Spending Jar,” “Save Jar,” and “amount owed”;
- explain that a recorded withdrawal is a reduction in the virtual balance, not a bank withdrawal;
- keep virtual balance, savings, spending, and owed amounts understandable and distinct;
- make no promise of deposits, withdrawals, payments, returns, cash value, redemption, or custody;
- keep the disclaimer visible in onboarding and relevant dashboard/help surfaces.

### 1.2 Roles and authority

A parent is the writer and account authority. Eddie is a reader and learner.

The parent may, subject to the approved command set and unresolved policy decisions:

- create and manage a family learning space and Eddie profile;
- pair and revoke Eddie devices;
- configure and preview an allowance rule;
- record virtual deposits, withdrawals, spending, savings, loan, repayment, and reward events;
- review the resulting balance before confirmation;
- inspect immutable activity and sync outcomes;
- use parent-only recovery, export, deletion, and device-management controls once those policies are approved.

Eddie may:

- open a paired, scoped read-only view;
- see the balance, Save Jar, Spending, amount owed, and activity explanation;
- learn concepts after relevant events;
- ask a parent to review something that looks wrong.

Eddie must not be able to record, edit, delete, approve, confirm, reverse, configure, pair, revoke, export, import, or otherwise mutate ledger data. Hiding write controls is a UX decision; server and database rejection is the security boundary.

### 1.3 Platforms and delivery order

- **Android first:** support the parent phone and Eddie’s Android tablet/device with a responsive, role-aware experience.
- **Linux deferred:** do not make a Linux client a v1 delivery dependency. Preserve a portable API, event model, export format, and local-storage boundary so a later Linux desktop app or browser PWA can reuse the same contract.
- The exact later Linux target—Flutter Linux desktop, browser PWA/no-install, or another form—is an open captain decision.

### 1.4 Persistence and synchronization

Cloud persistence and multi-device synchronization are part of the product contract. A parent may use a different device from Eddie’s tablet. The cloud/server is authoritative for accepted events and derived balances; each client is a local cache, and a parent client also has a durable command outbox.

Sync is eventually consistent, not a claim of guaranteed real-time delivery. The product must distinguish:

- **Confirmed:** accepted by the server and available in the confirmed shared snapshot;
- **Pending sync:** saved locally on a parent device but not yet accepted/confirmed by the server;
- **Offline:** no current connection; show the last confirmed snapshot and identify any local pending work;
- **Rejected:** the server declined a command; remove it from the confirmed projection and explain why;
- **Device revoked:** the child device is no longer authorized and must clear its local family data on next contact and re-pair.

The wording “confirmed snapshot” versus “up to date” remains open; the state semantics do not.

## 2. Ledger and data requirements

### 2.1 Immutable event ledger

Accepted changes must be represented by an append-only event ledger. The ledger is the durable semantic boundary and must be portable across the prototype host, a future provider, clients, and exports.

Rules:

1. The client submits a validated command, never an authoritative balance or signed posting.
2. The server authenticates the actor, derives the family/member/device scope, validates the command, calculates postings, checks invariants, and appends the event transactionally.
3. Accepted events and their postings are immutable. Do not update or delete accepted history.
4. Correct a mistake with a new reversal event that references the accepted event; preserve the original event and audit trail.
5. Balances are server-derived projections from accepted events. A cached projection may be rebuilt and must never become an independent authority.
6. The server supplies ordering metadata such as `recorded_at` and a per-family position. A client timestamp may be a display/effective date, not the ordering authority.
7. Commands need client-generated IDs/idempotency keys. Retrying after a timeout with the same key must return the original outcome without duplicating postings.
8. A duplicate key with a different payload must be rejected, not interpreted as a second event.

The minimum conceptual records are:

| Record | Required responsibility |
| --- | --- |
| Family | Family boundary, display-unit configuration, timezone, and ledger version/position. |
| Family member | Family-scoped parent/child role, display name, and status. |
| Child device | Paired child scope, device/session identity, timestamps, and revocation state. |
| Pairing code/token | Hashed, short-lived, one-use secret scoped to one family/member; never profile data in the code. |
| Ledger account | Virtual spending, savings, or owed account for the child/member. |
| Ledger event | Immutable event envelope: ID, kind, schema version, actor, effective date, server time, source, idempotency key, note/payload, and optional reversal reference. |
| Ledger posting | Signed server-created unit delta linked to an event and account. |
| Command/outcome | Idempotency, device/client sequence, payload hash, base position, accepted/rejected status, and stable rejection code. |
| Allowance rule | Configuration only: amount, cadence, time zone, destination, and active/pause dates. It is not itself a ledger event. |
| Loan | Optional loan identity and principal/status; actual changes are ledger events. |

Use same-family foreign keys and database constraints. A client must never be able to choose an arbitrary family ID to widen its scope.

### 2.2 Units and numeric safety

All values are virtual units, not currency. Do not store an ISO currency code or imply a currency account. Use integer storage, such as `BIGINT amount_units`, and reject non-positive command amounts where the event requires an amount.

The display label and precision are still open decisions. The technical recommendation is whole **credits** with integer units; fixed-scale decimal allowance units are an alternative. Do not finalize schema/display precision, rounding, or input formatting until the captain resolves that choice.

### 2.3 Event vocabulary and working semantics

The following command/event families are required by the journeys. The exact loan, negative-balance, allowance, and interest policies called out in the open-decision section remain authoritative gates where noted.

| Event | Working server-derived posting | Boundary/teaching requirement |
| --- | --- | --- |
| Deposit/allowance | Spending `+amount` | Parent only; virtual record, never a cash deposit. |
| Withdrawal/spending | Spending `-amount` | Parent only; explain as a virtual reduction, never payment/cash movement. |
| Save | Spending `-amount`, Save Jar/savings `+amount` | Show that credits changed location, not that money moved externally. |
| Unsave (if enabled) | Savings `-amount`, spending `+amount` | Parent-only; policy and naming must be confirmed before implementation. |
| Loan issue | Candidate baseline: spending `+amount`, owed `+amount` | Show available credits and amount owed separately; exact policy is open. |
| Loan repayment | Candidate baseline: spending `-amount`, owed `-amount` | Explain before/after owed amount; exact limits and negative-balance treatment are open. |
| Interest/reward | Candidate baseline: savings `+amount` | Must be clearly an educational virtual reward, never a guaranteed financial return; timing/automation are open. |
| Reversal | Exact inverse of one accepted event | New immutable event; never edit/delete the original. |

The server must reject unknown event kinds, invalid amounts, cross-family references, stale/revoked actors, invalid state transitions, and mismatched idempotency payloads. It must calculate postings from the command rather than accept client-supplied deltas.

### 2.4 Derived views

The server and clients may expose projections such as:

- spending/available virtual credits;
- Save Jar/savings;
- amount owed;
- total held or balance, if the final terminology and formula are approved;
- activity timeline with event explanation and sync status.

These are derived values, not mutable truth. The implementation must document the relationship among “Balance,” “Spending,” “Save Jar,” and “amount owed” before production UI is finalized. A loan must not be disguised as earned allowance, and owed must not be silently folded into a single child-facing number.

## 3. Authentication, pairing, and authorization

### 3.1 Parent authentication

An authenticated parent account is required for parent access and writes. The current Android implementation direction is:

1. Android Credential Manager / Google Sign-In obtains a Google ID token and nonce.
2. The app sends the token over HTTPS to the backend.
3. The backend verifies the token signature using maintained rotating keys and checks issuer, audience, expiry, nonce where applicable, and stable Google `sub`.
4. The backend maps `(issuer, sub)` to a parent identity and issues the application session.

The API, not the Android client, is the final identity authority. A Google ID token is sign-in proof, not an application session. Never use mutable email as the account key, put secrets in the APK, or use debug token inspection as the production trust boundary.

The concrete Google Cloud project, allowed audiences, consent ownership, and final session/re-authentication policy are production setup gates. No OAuth client or cloud account is created by this repository change.

### 3.2 Child pairing

Child access is scoped, paired, and revocable:

1. An authenticated parent requests a pairing code for one child/member.
2. The server creates a high-entropy opaque token, stores only its hash, applies a short expiry and one-use constraint, and rate-limits attempts.
3. The child device redeems the token over HTTPS and receives a child-scoped session/device identity.
4. The server binds that device to exactly the intended family/member scope.
5. Every read checks active device scope and revocation. Anonymous or child identity alone is not authorization.
6. A parent may revoke the device. On next contact the server denies reads, the client clears tokens and local family data, and the device returns to pairing.

The child should not need an email, phone number, or Google account. Pairing codes must not contain names, profile data, or a reusable authorization claim. Offline revocation cannot erase data already viewed, copied, or photographed; that limitation must be accepted in the threat model.

### 3.3 Server and database enforcement

Authorization must be enforced twice:

- the API derives role, family, member, and device from the verified session and database membership; it ignores client-provided `mode`, role, or family scope;
- the database uses restricted roles, allow-listed read views/functions, constraints, and row-level security or an equivalent policy. Child identities have no insert/update/delete capability.

The client must expose no child command UI, outbox, or write-capable token, but a modified client is still untrusted. Test direct API, REST/RPC, and SQL/DML attempts. Never use a database superuser, owner, service key, or `BYPASSRLS` role for client requests.

Family-isolation tests must cover two families, two parents, two children, an unpaired device, a paired device, a revoked device, forged family IDs, cross-family references, pairing-code replay, exports, sync cursors, and error responses.

## 4. Local cache and synchronization

### 4.1 Client responsibilities

The client stores a local SQLite cache containing accepted events, a rebuildable projection, sync cursor, and—on parent devices only—a durable outbox of commands. A local repository/domain layer should own validation, persistence, and synchronization; widgets must not calculate authoritative balances or call the API directly.

A parent may optimistically see a provisional result after confirmation in the UI, but it must be labeled as pending until accepted. A child renders only the last confirmed snapshot, including when offline; the child never creates a pending event.

### 4.2 Command flow

A versioned API can use a shape like this; field names are illustrative and must be versioned before implementation:

```http
POST /v1/commands
Authorization: Bearer <parent-session>
Content-Type: application/json

{
  "command_id": "client-generated-uuid",
  "client_seq": 42,
  "base_position": 17,
  "kind": "loan_repayment",
  "payload": { "loan_id": "loan-uuid", "amount_units": 200 },
  "effective_on": "YYYY-MM-DD"
}
```

The accepted response includes the event ID, server position, and new projection/checksum as appropriate. A rejected response includes a stable, actionable code such as `INSUFFICIENT_SPENDING`, `REPAYMENT_EXCEEDS_OWED`, `REVOKED`, `DUPLICATE_PAYLOAD_MISMATCH`, or `SCHEMA_UNSUPPORTED`.

The server must lock or otherwise serialize each family’s accepted append, recompute relevant accepted state, validate invariants, and insert command outcome, event, postings, and server position atomically. A lost response is recovered by retrying the same command ID.

### 4.3 Pull flow and failure handling

A cursor-based pull endpoint can use a shape like:

```http
GET /v1/sync?after=<server-position>&limit=200
Authorization: Bearer <parent-or-child-session>
```

The family is derived from the session, not trusted from a query parameter. The client commits a complete event page and cursor in one local transaction, then renders it. Pull is idempotent and can replay from position zero.

Required behavior:

- parent online: persist outbox command, show pending, upload, then pull the accepted event;
- parent offline: allow local entry and retry with backoff; identify the provisional balance as unconfirmed;
- child offline: show the last confirmed snapshot and last-sync time; do not allow writes;
- server rejection: remove/reconcile provisional state, replay accepted events plus remaining pending commands, and explain the rejection;
- timeout after server commit: retry the same idempotency key and return the original outcome;
- two parent devices offline: serialize competing withdrawals/repayments against accepted server state; never last-write-wins a mutable balance;
- process death or partial page: transactional outbox/cursor recovery on restart;
- revocation: stop reads on the server and clear the child cache/tokens on next contact;
- outage: do not claim fresh confirmed data or real-time availability; cached child data is visibly stale.

Background retry may use a maintained Android scheduling integration, but app start/resume synchronization is also required. Realtime notifications can wake a pull but are not the correctness mechanism.

## 5. Approved UX direction

The captain-selected UX direction is an approved product requirement, not a choice to reopen in implementation.

### 5.1 Information architecture and language

- Start **dashboard-first (A)** so a parent can understand the family snapshot quickly.
- Keep an **explicit journey path** with labeled steps and understandable Back/Next movement visible. Bottom tabs are not the primary MVP navigation spine.
- Use **virtual credits** before any abbreviation; use **Spending Jar**, **Save Jar**, and **amount owed** consistently.
- Eddie’s first meaningful block is **Balance plus Save Jar**, followed by Spending, Owed, and latest activity.
- Keep the child boundary visible as read-only, but **hide unavailable write controls**. Provide a clear “ask a parent to review” path.
- Use a **quick amount plus resulting-balance preview**, then an explicit confirmation gate before the local action changes the provisional snapshot.
- Teach concepts **just in time** after the relevant saving, reward, loan, or repayment event instead of requiring a full financial model up front.

B (journey-first) and C (pockets + jars) were retained in the prototype as comparison alternatives. They do not override selected A and are not competing production requirements.

### 5.2 Parent journey

The parent journey must cover:

1. Welcome and the virtual-only boundary before meaningful amounts.
2. Authenticated parent setup of a synthetic family space and Eddie profile.
3. Pair Eddie’s Android device with a scoped, read-only child session.
4. Preview an allowance rule and its Spending Jar/Save Jar result before posting.
5. Choose an event: deposit/allowance, withdrawal, spending, saving, loan, repayment, or interest/reward when policy permits.
6. Enter a quick amount and plain-language reason.
7. Preview the resulting balance and affected jar/owed amount.
8. Confirm the virtual event through a clear confirmation gate.
9. See whether it is Pending sync, Confirmed, Offline, or Rejected and understand what Eddie can see.
10. Review immutable activity, reversal/review behavior, and device state as parent controls become available.

### 5.3 Child journey

The child journey must cover:

1. Redeem pairing on an Android device and see the read-only boundary.
2. Open a dashboard led by Balance plus Save Jar.
3. See Spending, Save Jar, and amount owed separately rather than conflated.
4. Read the latest activity in plain language answering **what happened, where it went, and why the total changed**.
5. Follow just-in-time education after saving, a reward, a loan, or a repayment.
6. Use an ask-parent-to-review path without seeing hidden write controls.
7. Open a previously synced view while offline and see that it is the last confirmed snapshot.
8. Encounter a revoked-device state that explains re-pairing and clears local access on next contact.

### 5.4 Required event and state journeys

The implementation and test plan must make these journeys playable and understandable:

- parent authentication and setup;
- scoped child pairing and re-pairing;
- allowance preview and eventual posting;
- parent deposit/credit;
- parent withdrawal and spending;
- saving into and, if approved, out of the Save Jar;
- loan creation and child-visible amount owed;
- repayment and before/after owed explanation;
- educational reward/interest explanation;
- child read-only dashboard and activity;
- offline parent entry;
- pending-sync reconciliation;
- confirmed shared snapshot;
- rejected command with no false confirmed balance;
- revoked child device with server denial and local wipe on contact.

## 6. Allowances, savings, loans, rewards, and education

### 6.1 Allowance

The product must support an allowance configuration/preview journey. A rule is configuration, not money: it may include amount, cadence, local time, IANA timezone, destination jar/account, start/pause dates, and an occurrence identity.

The prototype’s former **8-credit** example, including a **6 Spending / 2 Save** split, was synthetic fixture data. It is not an approved production amount or default split. The allowance amount, split, cadence, pause/missed-occurrence behavior, confirmation language, and whether posting is parent-confirmed or automated are open decisions.

No client may independently auto-post a cloud allowance. If automation is later approved, the server must own deterministic occurrence identity, timezone/DST behavior, idempotency, and auditability.

### 6.2 Savings

The Save Jar is a separate virtual location. Moving credits into savings changes allocation, not external ownership or cash. The child should see the saving event and a simple explanation of why Spending and Save Jar changed.

### 6.3 Loans and repayment

Loans and repayment are required learning/product journeys, but the exact policy is still captain-owned. The implementation must not silently choose:

- whether a loan increases available spending while also increasing amount owed;
- whether negative spending is permitted;
- whether a parent can override a rejected command with a separately labeled adjustment;
- whether a loan is principal-only;
- whether loan interest, fees, schedules, late rules, or amortization exist.

The safe research baseline is principal-only, no negative balance, no loan interest, and server rejection of spending/repayment that exceeds the applicable balance. This is a **recommendation**, not an approved production rule until resolved.

Whatever policy is selected, available credits and amount owed must remain visibly separate and each transition must be explainable to Eddie.

### 6.4 Interest/reward education

A reward must never be presented as a guaranteed financial return. A parent-recorded virtual reward is a safe prototype teaching example. The timing—first save, second visit, or after week one—and whether rewards are manual or automatically calculated are open. Automatic rate-based behavior is out of scope until rate, cadence, calculation basis, rounding, caps, and audit/version rules are approved.

Education follows the selected sequence **balance → save → reward → loan → repayment**, while appearing just in time after the relevant event. Avoid shame, rankings, financial advice, and language that implies real credit or investment.

## 7. Prototype backend and implementation recommendations

### 7.1 Approved prototype deployment direction

Use the **existing Hetzner VPS with self-hosted PostgreSQL, a small API, local SQLite cache/outbox, HTTPS/reverse proxy, and basic monitoring** for the synthetic-data prototype.

This is an approved prototype direction, not a production-readiness claim:

- the reported cost is approximately **€4/month**, a captain-provided assumption rather than a current vendor quote;
- keep PostgreSQL private and expose only the HTTPS API;
- keep services small: API, PostgreSQL, TLS/reverse proxy, and basic monitoring;
- the API owns Google verification, application sessions, command validation, idempotency, pairing, revocation, and sync;
- do not expose generic table writes to Android;
- do not add a self-hosted identity platform such as Keycloak, Authentik, or self-hosted Supabase Auth merely to make the prototype appear complete;
- do not create or provision a server, cloud account, OAuth client, DNS record, credential, or real family data as part of this documentation work.

A single VPS is a single failure domain. The prototype intentionally has no backup/recovery promise and must remain synthetic until the production gates below are satisfied.

### 7.2 Managed and separately operated alternatives

These are explicit fallbacks/alternatives, **not the selected prototype host**:

| Option | Status and use |
| --- | --- |
| **Supabase managed PostgreSQL/Auth + API/Edge Functions** | **Managed fallback.** Consider if the Hetzner proof fails, an operator is unavailable, or managed database operations and recovery are worth the recurring cost. It has useful managed Auth/RLS/backups/regions, but a current planning floor of about $25/month for Pro was reported and does not fit the selected €4 prototype constraint. Keep the immutable event/API boundary unchanged. |
| **Separately operated PostgreSQL + small API** | **Portability alternative.** Appropriate when a platform team owns database, TLS, sessions, migrations, backups, monitoring, and incident recovery. PostgreSQL licensing is fee-free; hosting and operations are not. |
| **Supabase + PowerSync or another sync service** | **Candidate sync fallback only.** Consider after a measured comparison if custom sync fails the two-device spike. It does not remove the need for server-side validation, idempotency, and an explicit ledger conflict policy. |
| **Firebase/Firestore** | **Not selected.** Its offline last-write-wins behavior is a poor default for a financial-looking immutable ledger unless events, not mutable balances, are modeled and the full policy is independently proven. |

Provider choice must not change the product boundary, server-derived balances, child authorization, event immutability, or export contract.

### 7.3 Recommended, not yet approved, client/architecture choices

The technical research recommends Flutter/Dart for one Android phone/tablet and later Linux codebase, a repository/view-model architecture, and Drift/native SQLite. These are candidate implementation choices, not captain-approved product requirements. Kotlin/Compose + Room and a browser/PWA path remain viable engineering alternatives.

The research also recommends a small custom pull/push protocol over the event stream, integer units, API-owned sessions, server-confirmed allowance occurrences, and deterministic replay. Use these as the starting hypotheses for the two-device spike; resolve any choice that affects the open product decisions first.

## 8. Explicit non-goals and safety boundaries

The following are out of scope for v1 or prohibited by the product boundary:

- bank accounts, bank connections, cards, card networks, payment processing, transfers, cash movement, cash-out, payouts through the app, or custody;
- real currencies, exchange rates, investments, securities, returns, taxes, overdraft, credit scoring, or financial advice;
- any promise that a virtual balance can be redeemed for money or goods;
- child-created, child-edited, child-approved, or child-confirmed ledger events;
- child-to-child transfers, public profiles, competition, social messaging, advertising, behavioral profiling, or targeted marketing;
- complex loan schedules, late fees, amortization, automatic compounding, or loan interest before policy approval;
- silent server-generated allowance/reward entries before automation policy is approved;
- real family/financial/personal data in the prototype;
- treating a local fixture, local cache, UI status, or optimistic balance as confirmed shared truth;
- last-write-wins synchronization of a mutable balance;
- generic database writes from clients, client-supplied postings, client-supplied balances, or a client-only authorization check;
- guaranteed real-time delivery or an uptime/recovery promise from a single VPS;
- a Linux v1 deliverable;
- production visual polish or a final brand/visual tone before that choice is made;
- analytics or crash reporting that collects child names, notes, balances, events, tokens, or unnecessary identifiers;
- creation of cloud accounts, OAuth clients, DNS, production credentials, infrastructure, or real family records in this repository task.

Backups and recovery are **deferred for the synthetic prototype**, not waived for production. A child-device cache is never a backup.

## 9. Open captain decisions

These choices remain unresolved. They must be recorded before the affected production feature is built; a prototype fixture or research recommendation is not an answer.

### Product behavior and copy

1. **Interest/reward timing:** first save, second visit, or after week one.
2. **Sync vocabulary:** “confirmed snapshot” or “up to date,” while retaining the same honest state distinctions.
3. **Visual tone:** warm/playful, calm/structured, or adaptive by role.
4. **Allowance details:** amount, Spending/Save split, cadence, destination, timezone, pause/resume, missed occurrences, and the exact rule/preview copy.
5. **Risk-specific confirmation:** whether loan and withdrawal need an additional review step beyond quick amount, resulting-balance preview, and confirmation.
6. **Units and precision:** whole “credits,” fixed decimal allowance units, or another label; scale, rounding, input/display precision, and formatting.
7. **Loan and negative-balance policy:** whether a loan raises available credits and owed together, whether negative spending is allowed, whether parent overrides exist, and whether loans are principal-only.
8. **Automation:** parent-confirmed versus server-automated allowance occurrences; manual versus automatic rewards; any scheduler, retry, and audit policy.
9. **Family membership:** one owner/one Eddie profile with many child devices, or multiple parents/children in the initial product. Family-scoped authorization is required regardless.
10. **Linux target:** later Flutter desktop, later browser PWA/no-install, or no Linux target until a later release.

### Provider, privacy, and operations

11. **Provider region:** exact Hetzner EU location (for example DE or FI), or exact managed-provider region if the fallback is selected. Region scope must include database, identity, email, logs, backups, and support/subprocessors—not only the primary database.
12. **Authentication finalization:** the current direction is Google Sign-In verified by the API; confirm the owning Google Cloud project, allowed audiences, OAuth consent owner, parent session/re-auth policy, and any jurisdiction constraints.
13. **Backup and retention:** retention period, backup mechanism, encryption, off-site location, restore access, and who can trigger a restore.
14. **Recovery objectives:** acceptable RPO, RTO, outage behavior, named operator/on-call owner, and an independent restore target.
15. **Export:** JSON/CSV scope, parent authorization/re-authentication, retention by the parent, import/restore semantics, and whether export is required before deletion. JSON is the candidate round-trip format; CSV is a candidate human-readable report, not a restore format.
16. **Deletion:** parent/family deletion flow, confirmation and export warning, cloud/log/backup retention, child-cache clearing, and deletion verification.
17. **Child privacy and legal posture:** minimum child profile data, consent/age/jurisdiction review, privacy notice, processor inventory, support route, and whether any crash reporting is allowed.
18. **Analytics and crash reporting:** none, opt-in, or a redacted/child-safe design with explicit retention and scrubbing rules.

## 10. Build order and gates

Implementation should proceed in risk order, not by polishing the dashboard first.

### Gate 0 — resolve policy before affected features

Before implementing the affected behavior, obtain captain decisions for:

- unit label/precision and balance terminology;
- loan/negative-balance semantics;
- allowance amount/rules/automation;
- reward timing/automation;
- family membership scope;
- risk-specific confirmation;
- sync wording;
- provider region and authentication/project setup;
- Linux target when Linux work is proposed;
- export, deletion, privacy, retention, RPO, and RTO before any non-synthetic data.

### Gate 1 — two-device ledger spike

Before broad UI work, prove on synthetic data:

- an append-only PostgreSQL event/posting transaction;
- deterministic server-derived projection and replay;
- local SQLite cache and parent outbox;
- cursor pull with transactional page/cursor commit;
- one parent writer and one child read-only device;
- offline parent entry and later reconciliation;
- duplicate command and timeout-after-commit idempotency;
- concurrent offline withdrawals/repayments serialized against accepted state;
- rejected command rollback/reconciliation;
- pairing, revocation, and re-pair behavior;
- family isolation and export/replay checks.

Decide custom sync versus the candidate sync service at this point, based on evidence—not after all screens are built.

### Gate 2 — vertical product slice

Demonstrate Android parent authentication, family/Eddie setup, pairing, deposit/withdrawal/spending, immutable activity, child dashboard, server/database read-only enforcement, and visible pending/confirmed/offline/rejected/revoked states.

### Gate 3 — learning and hardening

Add savings, approved loan/repayment policy, approved reward policy, reversal, allowance preview, education copy, accessibility, rate limits, log redaction, token/logout behavior, and device matrix coverage.

### Gate 4 — production data and launch

No real family data or launch until all of the following are demonstrated and documented:

- Google token verification checks signature, issuer, audience, expiry, nonce as applicable, and stable subject; negative tests pass;
- zero cross-family reads/writes through API, sync, pairing, database policies, direct interfaces, and exports;
- no child write succeeds at the API or database layer, including a forged/modified client;
- revoked devices fail reads and clear local access on next contact;
- pairing secrets are hashed, short-lived, one-use, scoped, and rate-limited;
- duplicate retries never create duplicate accepted events;
- event replay equals the server projection and validated JSON round trips preserve meaning;
- provisional, confirmed, rejected, offline, and revoked states cannot be confused;
- backup freshness, retention, encryption, independent restore target, measured RPO, measured RTO, and restore rehearsal pass;
- operator/on-call ownership, patching, TLS, secret rotation, monitoring, alerting, and incident runbooks exist;
- export, deletion, child privacy/legal review, provider region, and subprocessors are approved;
- Android phone/tablet, landscape, TalkBack, dynamic text, contrast, logout, offline, and re-pair tests pass;
- logs, analytics, crash reports, exports, and support workflows do not expose tokens, pairing secrets, child personal data, notes, or unnecessary ledger payloads.

## 11. Privacy and child-safety defaults

Until the captain approves a different policy:

- parent-created accounts are preferred; do not require a child email or phone;
- collect only the minimum parent identity and child display/profile data needed for the experience;
- do not collect precise age, location, contacts, photos, school information, advertising IDs, or behavioral profiles unless separately approved;
- use TLS for network traffic and platform secure storage for local secrets/keys;
- do not log bearer tokens, pairing codes, names, notes, full request bodies, or complete ledger payloads;
- clear tokens and local family data on logout, deletion, or confirmed revocation as appropriate;
- explain that offline revocation cannot erase content already viewed or photographed;
- provide parent-controlled export/delete and a plain-language privacy notice before production data;
- review child-privacy law, consent, age, data residency, retention, and all processors for the intended launch jurisdictions;
- use accessibility testing, large touch targets, readable language, and no shame/ranking mechanics.

These are safety defaults and gates, not permission to create production infrastructure in this repository.

## 12. Source provenance and repository history

This README synthesizes, rather than republishes, the research and review inputs. Private operational reports and generated artifacts are intentionally not copied into the repository.

Inputs consulted:

- repository history from the initial README-only scaffold through the UX prototype and draft PDR commits;
- the prior `.lavish/eddies-wallet-ux-prototype.html` prototype and its UX review/relaunch reports;
- the market research report, dated 2026-08-30, covering virtual-ledger candidates, read-only child evidence, education/loan gaps, mixed real-money products, privacy, and trial criteria;
- the technical approach report, dated 2026-08-30, covering event-ledger semantics, local SQLite/outbox, sync, authorization, portability, testing, and candidate stacks;
- the technical proposal report and preserved technical Lavish review surface, dated 2026-08-31, covering the approved synthetic Hetzner direction, API-owned parent sessions, scoped child pairing, deferred recovery, and the managed PostgreSQL fallback;
- captain-approved UX feedback recorded by the UX review: A dashboard-first, explicit journey path, virtual credits plus jars, Balance plus Save Jar, visible child boundary with hidden write controls, quick amount plus resulting preview, and just-in-time education.

The former repository artifacts were review material, not implementation authority. Their synthetic amounts, visual treatment, CDN references, simulated sync labels, and comparison variants must not be mistaken for production decisions. The current repository intentionally retains only this README so future implementation work has one durable source of truth and no private reports, credentials, local operational state, personal data, or generated artifacts.

## 13. Implementation handoff checklist

Before opening a feature PR, the implementer should be able to answer:

- Which approved requirement in this README does the feature satisfy?
- Does it affect an open captain decision? If yes, stop and resolve that decision first.
- Does it preserve virtual-only language and avoid custody/payment implications?
- Does a parent alone authorize every write, with API and database enforcement?
- Is the accepted state an immutable event with server-derived postings and balances?
- How does the local cache/outbox behave offline, on retry, rejection, timeout, and revocation?
- Can the child see the result and explanation without any write control or write capability?
- Which sync state is visible, and can a provisional state be mistaken for confirmed?
- What synthetic fixture and automated test proves the behavior?
- Is the behavior safe for the selected Android-first scope and later portable Linux/API contract?
- If production data is involved, which production gate above has been evidenced?

No application implementation, cloud account, VPS provisioning, OAuth client, DNS record, production credential, or real family data is part of this repository-shaping task.
