# Bank Budget Tracker

Private Android budgeting app.

Features:
- Notification access for Scotiabank, TD, CIBC, and Google Wallet.
- Parses likely transaction notifications.
- Treats matching bank and Google Wallet notifications within two minutes as duplicates.
- Built-in categories and keyword rules.
- Manual transaction entry.
- Edit transaction category and remember the merchant rule for future purchases.
- Add custom categories.
- Set monthly category budgets.
- Local SQLite storage only.

Build:
1. Upload all files to a private GitHub repository.
2. Open Actions and run Build Android APK.
3. Download BankBudgetTracker-debug.
4. Extract app-debug.apk and install it.
5. Open the app and enable Notification Access.

Notes:
- Old notifications are not imported.
- Notification formats vary. We can improve the parser later using real redacted examples.
- Android may redact OTP notifications; OTPs are intentionally not processed.
