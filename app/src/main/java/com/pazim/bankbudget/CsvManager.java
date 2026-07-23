package com.pazim.bankbudget;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CsvManager {

    public static final class ImportResult {
        public int imported;
        public int duplicates;
        public int skipped;
        public final List<String> errors = new ArrayList<>();

        public String summary() {
            return "Imported: " + imported
                    + "\nDuplicates skipped: " + duplicates
                    + "\nUnrecognized rows: " + skipped
                    + (errors.isEmpty() ? "" : "\nErrors: " + errors.size());
        }
    }

    private CsvManager() {
    }

    public static ImportResult importTransactions(Context context, Uri uri) {
        ImportResult result = new ImportResult();
        BudgetDatabase database = BudgetDatabase.get(context);

        try (
                InputStream inputStream =
                        context.getContentResolver().openInputStream(uri);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                )
        ) {
            String headerLine = reader.readLine();

            if (headerLine == null) {
                result.errors.add("CSV file is empty.");
                return result;
            }

            List<String> headers = parseCsvLine(removeBom(headerLine));
            Map<String, Integer> indexes = buildHeaderIndex(headers);

            String line;
            int rowNumber = 1;

            while ((line = reader.readLine()) != null) {
                rowNumber++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    List<String> row = parseCsvLine(line);
                    Transaction transaction =
                            transactionFromRow(context, indexes, row);

                    if (transaction == null) {
                        result.skipped++;
                        continue;
                    }

                    database.addCategory(transaction.category);

                    if (database.isDuplicate(transaction)) {
                        result.duplicates++;
                        continue;
                    }

                    long inserted =
                            database.insertTransaction(transaction);

                    if (inserted == -1) {
                        result.duplicates++;
                    } else {
                        result.imported++;
                    }

                } catch (Exception exception) {
                    result.skipped++;

                    if (result.errors.size() < 10) {
                        result.errors.add(
                                "Row " + rowNumber + ": "
                                        + safeMessage(exception)
                        );
                    }
                }
            }

        } catch (Exception exception) {
            result.errors.add(safeMessage(exception));
        }

        return result;
    }

    public static void exportTransactions(Context context, Uri uri)
            throws Exception {
        List<Transaction> transactions =
                BudgetDatabase.get(context).getAllTransactions();

        try (
                OutputStream outputStream =
                        context.getContentResolver().openOutputStream(uri, "wt");
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(
                                outputStream,
                                StandardCharsets.UTF_8
                        )
                )
        ) {
            writer.write(
                    "Date,Amount,Currency,Merchant,Category,Source,Type,Notes"
            );
            writer.newLine();

            SimpleDateFormat dateFormat =
                    new SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.CANADA
                    );

            for (Transaction transaction : transactions) {
                List<String> values = new ArrayList<>();
                values.add(
                        dateFormat.format(
                                new Date(transaction.timestamp)
                        )
                );
                values.add(
                        String.format(
                                Locale.CANADA,
                                "%.2f",
                                Math.abs(transaction.amount)
                        )
                );
                values.add(defaultValue(transaction.currency, "CAD"));
                values.add(defaultValue(transaction.merchant, ""));
                values.add(defaultValue(transaction.category, "Other"));
                values.add(defaultValue(transaction.source, ""));
                values.add(
                        BudgetDatabase.normalizeType(transaction.type)
                );
                values.add(defaultValue(transaction.rawText, ""));

                writer.write(toCsvLine(values));
                writer.newLine();
            }
        }
    }

    private static Transaction transactionFromRow(
            Context context,
            Map<String, Integer> indexes,
            List<String> row
    ) {
        String dateValue = first(
                indexes,
                row,
                "date",
                "transaction date",
                "posted date",
                "posting date",
                "timestamp"
        );

        String description = first(
                indexes,
                row,
                "merchant",
                "description",
                "transaction details",
                "details",
                "memo",
                "name"
        );

        String category = first(indexes, row, "category");
        String currency = first(indexes, row, "currency");
        String source = first(indexes, row, "source", "account", "bank");
        String type = first(indexes, row, "type", "transaction type");
        String notes = first(indexes, row, "notes", "raw text", "raw_text");

        String debit = first(
                indexes,
                row,
                "debit",
                "withdrawal",
                "money out"
        );

        String credit = first(
                indexes,
                row,
                "credit",
                "deposit",
                "money in"
        );

        String amountValue = first(
                indexes,
                row,
                "amount",
                "transaction amount"
        );

        Double debitAmount = parseAmount(debit);
        Double creditAmount = parseAmount(credit);
        Double genericAmount = parseAmount(amountValue);

        double amount;
        String normalizedType;

        if (debitAmount != null && debitAmount != 0) {
            amount = Math.abs(debitAmount);
            normalizedType = "EXPENSE";

        } else if (creditAmount != null && creditAmount != 0) {
            amount = Math.abs(creditAmount);
            normalizedType = "INCOME";

        } else if (genericAmount != null && genericAmount != 0) {
            amount = Math.abs(genericAmount);

            if (type != null && !type.trim().isEmpty()) {
                normalizedType =
                        BudgetDatabase.normalizeType(type);
            } else {
                String descriptionUpper =
                        description == null
                                ? ""
                                : description.toUpperCase(Locale.CANADA);

                if (descriptionUpper.contains("PAYROLL")
                        || descriptionUpper.contains("SALARY")
                        || descriptionUpper.contains("DEPOSIT")
                        || descriptionUpper.contains("REFUND")) {
                    normalizedType = "INCOME";
                } else {
                    normalizedType = "EXPENSE";
                }
            }

        } else {
            return null;
        }

        if (description == null || description.trim().isEmpty()) {
            description = "Imported transaction";
        }

        Transaction transaction = new Transaction();
        transaction.timestamp = parseDate(dateValue);
        transaction.amount = amount;
        transaction.currency =
                currency == null || currency.trim().isEmpty()
                        ? "CAD"
                        : currency.trim().toUpperCase(Locale.CANADA);
        transaction.merchant = description.trim();
        transaction.source =
                source == null || source.trim().isEmpty()
                        ? "CSV Import"
                        : source.trim();
        transaction.type = normalizedType;
        transaction.rawText =
                notes == null || notes.trim().isEmpty()
                        ? description.trim()
                        : notes.trim();
        transaction.manual = true;

        if (category == null || category.trim().isEmpty()) {
            transaction.category =
                    CategoryEngine.categorize(
                            context,
                            transaction.merchant,
                            transaction.rawText
                    );
        } else {
            transaction.category = category.trim();
        }

        transaction.fingerprint =
                "csv-"
                        + transaction.timestamp
                        + "-"
                        + String.format(
                                Locale.CANADA,
                                "%.2f",
                                amount
                        )
                        + "-"
                        + CategoryEngine.normalize(
                                transaction.merchant
                        ).hashCode();

        return transaction;
    }

    private static long parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return System.currentTimeMillis();
        }

        String cleaned = value.trim();

        String[] patterns = new String[]{
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "MM/dd/yyyy",
                "M/d/yyyy",
                "dd/MM/yyyy",
                "d/M/yyyy",
                "MMM d, yyyy",
                "MMMM d, yyyy",
                "yyyyMMdd"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat format =
                        new SimpleDateFormat(
                                pattern,
                                Locale.CANADA
                        );

                format.setLenient(false);

                Date date = format.parse(cleaned);

                if (date != null) {
                    return date.getTime();
                }
            } catch (Exception ignored) {
            }
        }

        return System.currentTimeMillis();
    }

    private static Double parseAmount(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value
                .trim()
                .replace("CAD", "")
                .replace("USD", "")
                .replace("C$", "")
                .replace("$", "")
                .replace(",", "")
                .replace("(", "-")
                .replace(")", "")
                .trim();

        if (cleaned.isEmpty()) {
            return null;
        }

        try {
            return Double.parseDouble(cleaned);
        } catch (Exception exception) {
            return null;
        }
    }

    private static Map<String, Integer> buildHeaderIndex(
            List<String> headers
    ) {
        Map<String, Integer> output = new HashMap<>();

        for (int index = 0; index < headers.size(); index++) {
            output.put(
                    normalizeHeader(headers.get(index)),
                    index
            );
        }

        return output;
    }

    private static String first(
            Map<String, Integer> indexes,
            List<String> row,
            String... names
    ) {
        for (String name : names) {
            Integer index = indexes.get(
                    normalizeHeader(name)
            );

            if (index != null && index < row.size()) {
                String value = row.get(index);

                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        }

        return null;
    }

    private static String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.CANADA)
                .replace("_", " ")
                .replaceAll("\\s+", " ");
    }

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean insideQuotes = false;

        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);

            if (character == '"') {
                if (insideQuotes
                        && index + 1 < line.length()
                        && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    insideQuotes = !insideQuotes;
                }

            } else if (character == ',' && !insideQuotes) {
                values.add(current.toString());
                current.setLength(0);

            } else {
                current.append(character);
            }
        }

        values.add(current.toString());
        return values;
    }

    private static String toCsvLine(List<String> values) {
        StringBuilder output = new StringBuilder();

        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                output.append(',');
            }

            String value = values.get(index);

            if (value == null) {
                value = "";
            }

            boolean quote =
                    value.contains(",")
                            || value.contains("\"")
                            || value.contains("\n")
                            || value.contains("\r");

            if (quote) {
                output.append('"');
                output.append(value.replace("\"", "\"\""));
                output.append('"');
            } else {
                output.append(value);
            }
        }

        return output.toString();
    }

    private static String removeBom(String value) {
        if (value != null && value.startsWith("\uFEFF")) {
            return value.substring(1);
        }

        return value;
    }

    private static String defaultValue(
            String value,
            String fallback
    ) {
        return value == null ? fallback : value;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();

        return message == null || message.trim().isEmpty()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
