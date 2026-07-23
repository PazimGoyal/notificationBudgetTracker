package com.pazim.bankbudget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int REQUEST_IMPORT_CSV = 4001;
    private static final int REQUEST_EXPORT_CSV = 4002;

    private final NumberFormat money =
            NumberFormat.getCurrencyInstance(Locale.CANADA);

    private final Calendar selectedMonth =
            Calendar.getInstance();

    private LinearLayout dashboard;
    private LinearLayout transactionList;

    private TextView monthLabel;
    private TextView spentValue;
    private TextView incomeValue;
    private TextView netValue;

    private long start;
    private long end;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void build() {
        ScrollView scrollView = new ScrollView(this);

        LinearLayout root = vertical();
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scrollView.addView(root);

        TextView title = label("Bank Budget Tracker", 28, true);
        root.addView(title);

        TextView subtitle = label(
                "Track notifications, import statements and manage monthly budgets.",
                14,
                false
        );
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        LinearLayout monthRow = horizontal();
        monthRow.setGravity(Gravity.CENTER_VERTICAL);

        Button previous = button("‹");
        previous.setOnClickListener(view -> {
            selectedMonth.add(Calendar.MONTH, -1);
            refresh();
        });

        monthLabel = label("", 20, true);
        monthLabel.setGravity(Gravity.CENTER);

        Button next = button("›");
        next.setOnClickListener(view -> {
            selectedMonth.add(Calendar.MONTH, 1);
            refresh();
        });

        monthRow.addView(
                previous,
                new LinearLayout.LayoutParams(dp(54), dp(48))
        );

        monthRow.addView(
                monthLabel,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        monthRow.addView(
                next,
                new LinearLayout.LayoutParams(dp(54), dp(48))
        );

        root.addView(monthRow);

        LinearLayout summary = horizontal();
        summary.setPadding(0, dp(10), 0, dp(10));

        spentValue = summaryCard(summary, "Spent");
        incomeValue = summaryCard(summary, "Income");
        netValue = summaryCard(summary, "Net");

        root.addView(summary);

        TextView actionsTitle = label("Quick actions", 18, true);
        actionsTitle.setPadding(0, dp(8), 0, dp(6));
        root.addView(actionsTitle);

        Button notificationAccess =
                button("Enable notification access");

        notificationAccess.setOnClickListener(view -> {
            try {
                startActivity(
                        new Intent(
                                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                        )
                );
            } catch (Exception exception) {
                Toast.makeText(
                        this,
                        "Open Settings and search Notification access",
                        Toast.LENGTH_LONG
                ).show();
            }
        });

        root.addView(notificationAccess);

        LinearLayout importExportRow = horizontal();

        Button importCsv = button("Import CSV");
        importCsv.setOnClickListener(view -> openCsvImporter());

        Button exportCsv = button("Export CSV");
        exportCsv.setOnClickListener(view -> openCsvExporter());

        importExportRow.addView(
                importCsv,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        importExportRow.addView(
                exportCsv,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        root.addView(importExportRow);

        LinearLayout addRow = horizontal();

        Button manual = button("Add transaction");
        manual.setOnClickListener(view -> transactionDialog(null));

        Button category = button("Add category");
        category.setOnClickListener(view -> addCategory());

        addRow.addView(
                manual,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        addRow.addView(
                category,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        root.addView(addRow);

        TextView budgetsTitle = label("Category budgets", 20, true);
        budgetsTitle.setPadding(0, dp(18), 0, dp(6));
        root.addView(budgetsTitle);

        dashboard = vertical();
        root.addView(dashboard);

        TextView transactionsTitle = label("Transactions", 20, true);
        transactionsTitle.setPadding(0, dp(18), 0, dp(6));
        root.addView(transactionsTitle);

        transactionList = vertical();
        root.addView(transactionList);

        Button refresh = button("Refresh");
        refresh.setOnClickListener(view -> refresh());
        root.addView(refresh);

        setContentView(scrollView);
    }

    private TextView summaryCard(
            LinearLayout parent,
            String heading
    ) {
        LinearLayout card = vertical();
        card.setPadding(dp(10), dp(12), dp(10), dp(12));

        TextView title = label(heading, 13, false);
        TextView value = label("$0.00", 18, true);

        card.addView(title);
        card.addView(value);

        parent.addView(
                card,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        return value;
    }

    private void refresh() {
        calculateRange();

        monthLabel.setText(
                new SimpleDateFormat(
                        "MMMM yyyy",
                        Locale.CANADA
                ).format(selectedMonth.getTime())
        );

        BudgetDatabase database =
                BudgetDatabase.get(this);

        List<String> categories =
                database.getCategories();

        List<Transaction> transactions =
                database.getTransactions(start, end);

        double expenseTotal = 0;
        double incomeTotal = 0;

        for (Transaction transaction : transactions) {
            String type =
                    BudgetDatabase.normalizeType(transaction.type);

            if ("EXPENSE".equals(type)) {
                expenseTotal += Math.abs(transaction.amount);
            } else if ("INCOME".equals(type)) {
                incomeTotal += Math.abs(transaction.amount);
            }
        }

        spentValue.setText(money.format(expenseTotal));
        incomeValue.setText(money.format(incomeTotal));
        netValue.setText(money.format(incomeTotal - expenseTotal));

        dashboard.removeAllViews();

        for (String category : categories) {
            if ("Income".equals(category)
                    || "Transfers".equals(category)) {
                continue;
            }

            double spent =
                    database.monthSpending(category, start, end);

            double budget =
                    database.getBudget(category);

            dashboard.addView(
                    budgetCard(
                            category,
                            spent,
                            budget
                    )
            );
        }

        transactionList.removeAllViews();

        if (transactions.isEmpty()) {
            TextView empty = label(
                    "No transactions for this month.",
                    15,
                    false
            );
            empty.setPadding(0, dp(10), 0, dp(10));
            transactionList.addView(empty);
            return;
        }

        SimpleDateFormat dateFormat =
                new SimpleDateFormat(
                        "MMM d, h:mm a",
                        Locale.CANADA
                );

        for (Transaction transaction : transactions) {
            transactionList.addView(
                    transactionCard(
                            transaction,
                            dateFormat
                    )
            );
        }
    }

    private LinearLayout budgetCard(
            String category,
            double spent,
            double budget
    ) {
        LinearLayout card = vertical();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView heading = label(category, 16, true);

        String amountText =
                budget > 0
                        ? money.format(spent)
                        + " of "
                        + money.format(budget)
                        : money.format(spent)
                        + " spent";

        TextView amount = label(amountText, 14, false);

        ProgressBar progress =
                new ProgressBar(
                        this,
                        null,
                        android.R.attr.progressBarStyleHorizontal
                );

        progress.setMax(100);

        int percent =
                budget > 0
                        ? (int) Math.min(
                        100,
                        Math.round((spent / budget) * 100)
                )
                        : 0;

        progress.setProgress(percent);

        card.addView(heading);
        card.addView(amount);
        card.addView(progress);

        if (budget > 0) {
            double remaining = budget - spent;

            TextView remainingText = label(
                    remaining >= 0
                            ? money.format(remaining) + " remaining"
                            : money.format(Math.abs(remaining)) + " over budget",
                    13,
                    false
            );

            card.addView(remainingText);
        }

        card.setOnClickListener(
                view -> budgetDialog(category, budget)
        );

        return card;
    }

    private LinearLayout transactionCard(
            Transaction transaction,
            SimpleDateFormat dateFormat
    ) {
        LinearLayout card = vertical();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        String type =
                BudgetDatabase.normalizeType(transaction.type);

        String sign =
                "INCOME".equals(type)
                        ? "+"
                        : "EXPENSE".equals(type)
                        ? "-"
                        : "";

        TextView merchant = label(
                transaction.merchant,
                16,
                true
        );

        TextView amount = label(
                sign
                        + money.format(
                        Math.abs(transaction.amount)
                ),
                17,
                true
        );

        TextView details = label(
                transaction.category
                        + " • "
                        + transaction.source
                        + "\n"
                        + dateFormat.format(
                        transaction.timestamp
                ),
                13,
                false
        );

        card.addView(merchant);
        card.addView(amount);
        card.addView(details);

        card.setOnClickListener(
                view -> transactionDialog(transaction)
        );

        return card;
    }

    private void openCsvImporter() {
        Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");

        startActivityForResult(
                intent,
                REQUEST_IMPORT_CSV
        );
    }

    private void openCsvExporter() {
        Intent intent =
                new Intent(Intent.ACTION_CREATE_DOCUMENT);

        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");

        String fileName =
                "BankBudgetTransactions_"
                        + new SimpleDateFormat(
                                "yyyy-MM-dd",
                                Locale.CANADA
                        ).format(
                                System.currentTimeMillis()
                        )
                        + ".csv";

        intent.putExtra(
                Intent.EXTRA_TITLE,
                fileName
        );

        startActivityForResult(
                intent,
                REQUEST_EXPORT_CSV
        );
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (resultCode != RESULT_OK
                || data == null
                || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        if (requestCode == REQUEST_IMPORT_CSV) {
            CsvManager.ImportResult result =
                    CsvManager.importTransactions(this, uri);

            new AlertDialog.Builder(this)
                    .setTitle("CSV import complete")
                    .setMessage(result.summary())
                    .setPositiveButton("OK", null)
                    .show();

            refresh();
            return;
        }

        if (requestCode == REQUEST_EXPORT_CSV) {
            try {
                CsvManager.exportTransactions(this, uri);

                Toast.makeText(
                        this,
                        "CSV export completed",
                        Toast.LENGTH_LONG
                ).show();

            } catch (Exception exception) {
                Toast.makeText(
                        this,
                        "Export failed: "
                                + exception.getMessage(),
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    private void transactionDialog(Transaction existing) {
        BudgetDatabase database =
                BudgetDatabase.get(this);

        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(4), dp(18), 0);

        EditText amount = input(
                "Amount",
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        EditText merchant = input(
                "Merchant or description",
                InputType.TYPE_CLASS_TEXT
        );

        Spinner category =
                spinner(database.getCategories());

        Spinner type =
                spinner(
                        Arrays.asList(
                                "EXPENSE",
                                "INCOME",
                                "TRANSFER"
                        )
                );

        CheckBox remember = new CheckBox(this);
        remember.setText("Remember this merchant category");

        form.addView(amount);
        form.addView(merchant);
        form.addView(category);
        form.addView(type);
        form.addView(remember);

        if (existing != null) {
            amount.setText(
                    String.valueOf(
                            Math.abs(existing.amount)
                    )
            );

            merchant.setText(existing.merchant);
            select(category, existing.category);

            select(
                    type,
                    BudgetDatabase.normalizeType(existing.type)
            );
        }

        AlertDialog dialog =
                new AlertDialog.Builder(this)
                        .setTitle(
                                existing == null
                                        ? "Add transaction"
                                        : "Edit transaction"
                        )
                        .setView(form)
                        .setPositiveButton("Save", null)
                        .setNegativeButton("Cancel", null)
                        .setNeutralButton(
                                existing == null
                                        ? null
                                        : "Delete",
                                null
                        )
                        .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(view -> {
                double parsedAmount;

                try {
                    parsedAmount =
                            Double.parseDouble(
                                    amount.getText()
                                            .toString()
                                            .trim()
                            );
                } catch (Exception exception) {
                    amount.setError("Enter a valid amount");
                    return;
                }

                String merchantValue =
                        merchant.getText()
                                .toString()
                                .trim();

                if (merchantValue.isEmpty()) {
                    merchant.setError("Enter a merchant");
                    return;
                }

                String categoryValue =
                        String.valueOf(
                                category.getSelectedItem()
                        );

                String typeValue =
                        BudgetDatabase.normalizeType(
                                String.valueOf(
                                        type.getSelectedItem()
                                )
                        );

                if (existing == null) {
                    Transaction transaction =
                            new Transaction();

                    transaction.timestamp =
                            System.currentTimeMillis();
                    transaction.amount =
                            Math.abs(parsedAmount);
                    transaction.currency = "CAD";
                    transaction.merchant = merchantValue;
                    transaction.category = categoryValue;
                    transaction.source = "Manual";
                    transaction.type = typeValue;
                    transaction.rawText = "Manually added";
                    transaction.manual = true;
                    transaction.fingerprint =
                            "manual-" + System.nanoTime();

                    database.insertTransaction(transaction);

                    if (remember.isChecked()) {
                        database.saveMerchantRule(
                                merchantValue,
                                categoryValue
                        );
                    }

                } else {
                    database.updateTransaction(
                            existing.id,
                            Math.abs(parsedAmount),
                            merchantValue,
                            categoryValue,
                            typeValue,
                            remember.isChecked()
                    );
                }

                dialog.dismiss();
                refresh();
            });

            if (existing != null) {
                dialog.getButton(
                        AlertDialog.BUTTON_NEUTRAL
                ).setOnClickListener(view -> {
                    database.deleteTransaction(existing.id);
                    dialog.dismiss();
                    refresh();
                });
            }
        });

        dialog.show();
    }

    private void addCategory() {
        EditText input = input(
                "Category name",
                InputType.TYPE_CLASS_TEXT
        );

        new AlertDialog.Builder(this)
                .setTitle("Add category")
                .setView(input)
                .setPositiveButton(
                        "Add",
                        (dialog, which) -> {
                            BudgetDatabase.get(this)
                                    .addCategory(
                                            input.getText()
                                                    .toString()
                                    );
                            refresh();
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void budgetDialog(
            String category,
            double current
    ) {
        EditText input = input(
                "Monthly budget for " + category,
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        if (current > 0) {
            input.setText(String.valueOf(current));
        }

        new AlertDialog.Builder(this)
                .setTitle(category + " budget")
                .setView(input)
                .setPositiveButton(
                        "Save",
                        (dialog, which) -> {
                            try {
                                double value =
                                        Double.parseDouble(
                                                input.getText()
                                                        .toString()
                                                        .trim()
                                        );

                                BudgetDatabase.get(this)
                                        .setBudget(category, value);

                                refresh();

                            } catch (Exception exception) {
                                Toast.makeText(
                                        this,
                                        "Invalid budget",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                )
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void calculateRange() {
        Calendar monthStart =
                (Calendar) selectedMonth.clone();

        monthStart.set(Calendar.DAY_OF_MONTH, 1);
        monthStart.set(Calendar.HOUR_OF_DAY, 0);
        monthStart.set(Calendar.MINUTE, 0);
        monthStart.set(Calendar.SECOND, 0);
        monthStart.set(Calendar.MILLISECOND, 0);

        start = monthStart.getTimeInMillis();

        Calendar monthEnd =
                (Calendar) monthStart.clone();

        monthEnd.add(Calendar.MONTH, 1);
        monthEnd.add(Calendar.MILLISECOND, -1);

        end = monthEnd.getTimeInMillis();
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView label(
            String value,
            float size,
            boolean bold
    ) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);

        if (bold) {
            text.setTypeface(
                    text.getTypeface(),
                    Typeface.BOLD
            );
        }

        return text;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private EditText input(
            String hint,
            int type
    ) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(type);
        input.setPadding(
                dp(8),
                dp(8),
                dp(8),
                dp(8)
        );
        return input;
    }

    private Spinner spinner(List<String> values) {
        Spinner spinner = new Spinner(this);

        spinner.setAdapter(
                new ArrayAdapter<>(
                        this,
                        android.R.layout
                                .simple_spinner_dropdown_item,
                        values
                )
        );

        return spinner;
    }

    private void select(
            Spinner spinner,
            String value
    ) {
        for (int index = 0;
             index < spinner.getCount();
             index++) {

            if (value.equals(
                    String.valueOf(
                            spinner.getItemAtPosition(index)
                    )
            )) {
                spinner.setSelection(index);
                return;
            }
        }
    }

    private int dp(int value) {
        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }
}
