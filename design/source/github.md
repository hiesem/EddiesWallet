repo: hiesem/EddiesWallet
branch: main

## Last sync

date: 2026-09-01T18:14:12Z

### Updated in this project

- Built the first Android prototype from the v1 launch brief: parent phone (writer) and Eddie's tablet (read-only) side by side.
- Parent journey: dashboard → record event → amount + resulting-balance preview → confirmation gate → immutable activity with reversal.
- Sync states made visible: confirmed, pending sync, offline, rejected (INSUFFICIENT_SPENDING / REPAYMENT_EXCEEDS_OWED), device revoked.
- Child journey: Balance + Save Jar first, Spending/Owed kept separate, plain-language activity, just-in-time education, ask-a-parent path.

## Screen map

| Project screen | Built from |
| --- | --- |
| Parent dashboard, record, confirm, activity, pairing | README.md §1, §2.3, §3.2, §5.2 |
| Eddie's tablet home, what-happened, learn, revoked | README.md §5.1, §5.3, §6.2–6.4 |
| Sync/pending/offline/rejected states | README.md §1.4, §4.3 |
