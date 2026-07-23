package com.pazim.bankbudget;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BudgetDatabase extends SQLiteOpenHelper {

    private static BudgetDatabase instance;

    public static synchronized BudgetDatabase get(Context context) {
        if (instance == null) {
            instance = new BudgetDatabase(context.getApplicationContext());
        }
        return instance;
    }

    public static String normalizeType(String type) {
        if (type == null) {
            return "EXPENSE";
        }

        String value = type.trim().toUpperCase(Locale.CANADA);

        if (value.equals("DEBIT")
                || value.equals("PURCHASE")
                || value.equals("SPENT")
                || value.equals("CHARGE")
                || value.equals("PAYMENT")
                || value.equals("WITHDRAWAL")
                || value.equals("EXPENSE")) {
            return "EXPENSE";
        }

        if (value.equals("CREDIT")
                || value.equals("REFUND")
                || value.equals("DEPOSIT")
                || value.equals("PAYROLL")
                || value.equals("INCOME")) {
            return "INCOME";
        }

        if (value.equals("TRANSFER")
                || value.equals("E-TRANSFER")
                || value.equals("E TRANSFER")) {
            return "TRANSFER";
        }

        return "EXPENSE";
    }

    public static boolean isExpenseType(String type) {
        return "EXPENSE".equals(normalizeType(type));
    }

    private BudgetDatabase(Context context) {
        super(context, "bank_budget.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE transactions(" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "timestamp INTEGER NOT NULL," +
                        "amount REAL NOT NULL," +
                        "currency TEXT NOT NULL," +
                        "merchant TEXT NOT NULL," +
                        "category TEXT NOT NULL," +
                        "source TEXT NOT NULL," +
                        "type TEXT NOT NULL," +
                        "raw_text TEXT NOT NULL," +
                        "manual INTEGER NOT NULL DEFAULT 0," +
                        "fingerprint TEXT UNIQUE)"
        );

        db.execSQL(
                "CREATE TABLE categories(" +
                        "name TEXT PRIMARY KEY COLLATE NOCASE," +
                        "monthly_budget REAL NOT NULL DEFAULT 0)"
        );

        db.execSQL(
                "CREATE TABLE merchant_rules(" +
                        "keyword TEXT PRIMARY KEY COLLATE NOCASE," +
                        "category TEXT NOT NULL)"
        );

        for (String category : new String[]{
                "Bills", "Groceries", "Shopping", "Eating Out", "Fuel",
                "Transport", "Entertainment", "Health", "Transfers",
                "Income", "Other"
        }) {
            ContentValues values = new ContentValues();
            values.put("name", category);
            db.insert("categories", null, values);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public synchronized long insertTransaction(Transaction transaction) {
        transaction.type = normalizeType(transaction.type);

        if (transaction.category == null || transaction.category.trim().isEmpty()) {
            transaction.category = "Other";
        }

        if (transaction.merchant == null || transaction.merchant.trim().isEmpty()) {
            transaction.merchant = "Unknown merchant";
        }

        if (transaction.currency == null || transaction.currency.trim().isEmpty()) {
            transaction.currency = "CAD";
        }

        if (transaction.source == null || transaction.source.trim().isEmpty()) {
            transaction.source = "Unknown";
        }

        if (transaction.timestamp <= 0) {
            transaction.timestamp = System.currentTimeMillis();
        }

        if (transaction.fingerprint == null || transaction.fingerprint.trim().isEmpty()) {
            transaction.fingerprint = "transaction-" + System.nanoTime();
        }

        ContentValues values = new ContentValues();
        values.put("timestamp", transaction.timestamp);
        values.put("amount", Math.abs(transaction.amount));
        values.put("currency", transaction.currency);
        values.put("merchant", transaction.merchant);
        values.put("category", transaction.category);
        values.put("source", transaction.source);
        values.put("type", normalizeType(transaction.type));
        values.put("raw_text", transaction.rawText == null ? "" : transaction.rawText);
        values.put("manual", transaction.manual ? 1 : 0);
        values.put("fingerprint", transaction.fingerprint);

        return getWritableDatabase().insertWithOnConflict(
                "transactions",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
    }

    public synchronized boolean isDuplicate(Transaction transaction) {
        String normalizedType = normalizeType(transaction.type);

        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT source,merchant,type FROM transactions " +
                        "WHERE ABS(amount-?)<0.01 " +
                        "AND timestamp BETWEEN ? AND ? " +
                        "ORDER BY timestamp DESC LIMIT 20",
                new String[]{
                        String.valueOf(transaction.amount),
                        String.valueOf(transaction.timestamp - 120000L),
                        String.valueOf(transaction.timestamp + 120000L)
                }
        )) {
            while (cursor.moveToNext()) {
                String source = cursor.getString(0);
                String merchant = cursor.getString(1);
                String type = normalizeType(cursor.getString(2));

                if (!normalizedType.equals(type)) {
                    continue;
                }

                boolean walletPair =
                        ("Google Wallet".equals(transaction.source)
                                && !"Google Wallet".equals(source))
                                || (!"Google Wallet".equals(transaction.source)
                                && "Google Wallet".equals(source));

                String existingMerchant = CategoryEngine.normalize(merchant);
                String newMerchant = CategoryEngine.normalize(transaction.merchant);

                boolean sameMerchant =
                        !existingMerchant.isEmpty()
                                && !newMerchant.isEmpty()
                                && (existingMerchant.contains(newMerchant)
                                || newMerchant.contains(existingMerchant));

                if (walletPair || sameMerchant) {
                    return true;
                }
            }
        }

        return false;
    }

    public synchronized List<Transaction> getTransactions(long start, long end) {
        List<Transaction> output = new ArrayList<>();

        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,timestamp,amount,currency,merchant,category,source,type," +
                        "raw_text,manual,fingerprint FROM transactions " +
                        "WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp DESC",
                new String[]{String.valueOf(start), String.valueOf(end)}
        )) {
            while (cursor.moveToNext()) {
                output.add(from(cursor));
            }
        }

        return output;
    }

    public synchronized List<Transaction> getAllTransactions() {
        List<Transaction> output = new ArrayList<>();

        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id,timestamp,amount,currency,merchant,category,source,type," +
                        "raw_text,manual,fingerprint FROM transactions " +
                        "ORDER BY timestamp DESC",
                null
        )) {
            while (cursor.moveToNext()) {
                output.add(from(cursor));
            }
        }

        return output;
    }

    public synchronized void updateTransaction(
            long id,
            double amount,
            String merchant,
            String category,
            String type,
            boolean remember
    ) {
        ContentValues values = new ContentValues();
        values.put("amount", Math.abs(amount));
        values.put("merchant", merchant.trim());
        values.put("category", category);
        values.put("type", normalizeType(type));

        getWritableDatabase().update(
                "transactions",
                values,
                "id=?",
                new String[]{String.valueOf(id)}
        );

        if (remember && merchant != null && !merchant.trim().isEmpty()) {
            saveMerchantRule(merchant, category);
        }
    }

    public synchronized void deleteTransaction(long id) {
        getWritableDatabase().delete(
                "transactions",
                "id=?",
                new String[]{String.valueOf(id)}
        );
    }

    public synchronized List<String> getCategories() {
        List<String> output = new ArrayList<>();

        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT name FROM categories ORDER BY name COLLATE NOCASE",
                null
        )) {
            while (cursor.moveToNext()) {
                output.add(cursor.getString(0));
            }
        }

        return output;
    }

    public synchronized void addCategory(String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put("name", name.trim());

        getWritableDatabase().insertWithOnConflict(
                "categories",
                null,
                values,
                SQLiteDatabase.CONFLICT_IGNORE
        );
    }

    public synchronized void setBudget(String category, double budget) {
        ContentValues values = new ContentValues();
        values.put("monthly_budget", Math.max(0, budget));

        getWritableDatabase().update(
                "categories",
                values,
                "name=?",
                new String[]{category}
        );
    }

    public synchronized double getBudget(String category) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT monthly_budget FROM categories WHERE name=?",
                new String[]{category}
        )) {
            return cursor.moveToFirst() ? cursor.getDouble(0) : 0;
        }
    }

    public synchronized double monthSpending(String category, long start, long end) {
        double total = 0;

        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT amount,type FROM transactions " +
                        "WHERE category=? AND timestamp BETWEEN ? AND ?",
                new String[]{
                        category,
                        String.valueOf(start),
                        String.valueOf(end)
                }
        )) {
            while (cursor.moveToNext()) {
                if (isExpenseType(cursor.getString(1))) {
                    total += Math.abs(cursor.getDouble(0));
                }
            }
        }

        return total;
    }

    public synchronized String findLearnedCategory(String text) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT keyword,category FROM merchant_rules " +
                        "ORDER BY LENGTH(keyword) DESC",
                null
        )) {
            while (cursor.moveToNext()) {
                String keyword = CategoryEngine.normalize(cursor.getString(0));

                if (!keyword.isEmpty() && text.contains(keyword)) {
                    return cursor.getString(1);
                }
            }
        }

        return null;
    }

    public synchronized void saveMerchantRule(String merchant, String category) {
        ContentValues values = new ContentValues();
        values.put("keyword", CategoryEngine.normalize(merchant));
        values.put("category", category);

        getWritableDatabase().insertWithOnConflict(
                "merchant_rules",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        );
    }

    private Transaction from(Cursor cursor) {
        Transaction transaction = new Transaction();
        transaction.id = cursor.getLong(0);
        transaction.timestamp = cursor.getLong(1);
        transaction.amount = cursor.getDouble(2);
        transaction.currency = cursor.getString(3);
        transaction.merchant = cursor.getString(4);
        transaction.category = cursor.getString(5);
        transaction.source = cursor.getString(6);
        transaction.type = cursor.getString(7);
        transaction.rawText = cursor.getString(8);
        transaction.manual = cursor.getInt(9) == 1;
        transaction.fingerprint = cursor.getString(10);
        return transaction;
    }
}
