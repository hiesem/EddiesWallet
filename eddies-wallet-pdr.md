# EddiesWallet MVP Product Design Requirements

**Status:** Draft direction for captain review  
**Review artifact:** `.lavish/eddies-wallet-ux-prototype.html`  
**Scope:** UX/product direction only; synthetic virtual credits, no production implementation

## Decision summary

Captain feedback selected the following MVP direction:

- **Information architecture:** A · Dashboard-first.
- **Navigation:** Explicit journey path. Keep the labeled path and Back/Next movement visible rather than replacing it with bottom tabs.
- **Terminology:** Virtual credits plus jars. Use **virtual credits**, **Spending Jar**, **Save Jar**, and **amount owed**.
- **Eddie home hierarchy:** Balance plus Save Jar first; then Spending, Owed, and latest activity.
- **Child boundary:** Show the read-only boundary and hide write controls. Eddie should have a clear ask-parent path without seeing unavailable editing controls.
- **Transaction form:** Quick amount plus resulting-balance preview, followed by a confirmation gate.
- **Education timing:** Just in time, after the relevant saving, reward, loan, or repayment event.

Variations B · Journey-first and C · Pockets + jars remain playable in the prototype as comparison alternatives. Keeping them does not override the selected A direction; it preserves the original low-fidelity exploration for future review.

## Product intent

EddiesWallet is a shared learning space where a parent records synthetic virtual-credit events and Eddie reads an understandable explanation. It is not a bank account, payment card, cash wallet, custody product, or money-moving service.

The core promise is: **a parent can record what happened, and Eddie can see the balance change, where the virtual credits went, and why.**

## MVP requirements

### R1 — Establish the virtual-only boundary

Before showing an amount, welcome must say that credits are pretend and local to the practice space. The product must not imply real deposits, withdrawals, payments, financial returns, or custody.

### R2 — Make the parent the writer

The parent can set up a synthetic family space, pair Eddie’s illustrative Android tablet, choose an event, enter an amount and plain-language reason, preview the resulting balance, and confirm. No identity provider or backend is part of this UX review.

### R3 — Make Eddie a reader and learner

Eddie’s view must expose a visible read-only boundary, balance plus Save Jar as the first meaningful block, and a path to activity explanations. Eddie can ask a parent to review something that looks wrong; Eddie cannot record, edit, approve, or confirm.

### R4 — Preserve an explicit journey path

The selected A flow should expose these labeled steps as a navigable path:

1. Start and virtual-only boundary
2. Parent setup
3. Pair Eddie’s device
4. Preview allowance
5. Record an event
6. Eddie reads the explanation
7. Learn about the relevant concept just in time
8. Review sync state

Back and Next must remain understandable at each step. The B and C variation controls must remain available for comparison in this prototype.

### R5 — Use concrete, repeatable language

Use “virtual credits” before abbreviating to VC. Use Spending Jar and Save Jar consistently. Show amount owed separately from total. An activity explanation should answer **what happened**, **where it went**, and **why the total changed**.

### R6 — Keep the write confirmation safe and legible

The parent action form must include:

- Event type: deposit, withdrawal, spending, saving, interest reward, loan, or repayment.
- Amount in virtual credits.
- Plain-language note.
- Resulting-balance preview.
- Explicit confirmation before the local fixture snapshot changes.

A confirmed local action may display Pending sync as a simulated state. This is review scaffolding, not a connected synchronization service.

### R7 — Teach concepts just in time

Do not require a full financial model before the first action. Explain saving after a saving event, explain a reward as a teaching example after the relevant save, and introduce loan and repayment language only when those events are relevant. Keep the sequence balance → save → reward → loan → repayment, while allowing the explicit path to show the next relevant step.

### R8 — Make uncertainty honest

The review must keep distinct simulated states for Confirmed, Pending sync, Offline, Rejected, and Device revoked. Copy must preserve the last confirmed snapshot when a new state is not confirmed. These are UX states only.

## Primary journey

### Parent path

1. Read the virtual-only boundary.
2. Enter synthetic parent/family labels.
3. Review illustrative read-only pairing.
4. Preview the weekly allowance and its Spending Jar / Save Jar split.
5. Choose an event and enter amount plus reason.
6. Check the resulting-balance preview.
7. Confirm the local fixture event.
8. See the simulated sync state.

### Eddie path

1. Open the read-only tablet view.
2. See balance plus Save Jar.
3. See Spending and Owed without conflating either with the total.
4. Open the latest activity.
5. Read the plain-language explanation.
6. Follow a just-in-time lesson or ask a parent to review.

## Out of scope

- Account creation or authentication.
- Real personal, financial, or family data.
- Backend, persistence, device pairing service, or synchronization service.
- Payment cards, cash movement, real-world custody, or financial advice.
- Production visual styling or final brand identity.
- Treating interest/reward examples as guaranteed financial returns.

## Remaining product decisions

The following calls are still open and are not resolved by the selected direction:

1. **Interest reward timing:** first save, second visit, or after week one.
2. **Sync vocabulary:** “confirmed snapshot” or “up to date.”
3. **Visual tone:** warm/playful, calm/structured, or adaptive by role.
4. **Allowance split and rule details:** the prototype uses an 8 VC example split into 6 Spending and 2 Save; the production rule and whether that split is default still need a captain decision.
5. **Risk-specific confirmation:** the selected quick-preview form is the baseline; whether loan and withdrawal require an additional review step remains open.

These decisions should be carried into the next captain review rather than inferred from the prototype’s fixture values.

## Prototype traceability

The selected direction is represented in `.lavish/eddies-wallet-ux-prototype.html`:

- Selected MVP framing, terminology, hierarchy, and just-in-time education: lines 271–274.
- Explicit A path and retained B/C alternatives: lines 356–370.
- Annotated selected journey: lines 307–324.
- Selected decision ledger: lines 331–334.
- Captain-input controls with the seven selected answers preselected for review: lines 339–346.
- Parent action form and resulting preview: lines 443–450.
- Eddie balance + Save Jar home and read-only boundary: lines 451–457.
- Simulated sync states: line 468.

## Review acceptance checks

A future implementation or UX pass should demonstrate that:

- A reviewer can identify the virtual-only boundary before seeing a meaningful amount.
- A parent can complete one event from selection through resulting preview and confirmation.
- Eddie can identify balance, Save Jar, Spending, Owed, and the latest explanation without write controls.
- The explicit path makes the next step discoverable without hiding the retained B/C alternatives.
- The same event is explained in parent-write and Eddie-read language.
- Unconfirmed, offline, rejected, and revoked states do not masquerade as a confirmed shared snapshot.
- Remaining decisions above are answered by the captain before production behavior is finalized.
