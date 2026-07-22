package com.pazim.bankbudget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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

    private static final int COLOR_BACKGROUND = Color.rgb(245, 247, 250);
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(25, 32, 44);
    private static final int COLOR_MUTED = Color.rgb(107, 114, 128);
    private static final int COLOR_PRIMARY = Color.rgb(31, 111, 235);
    private static final int COLOR_EXPENSE = Color.rgb(210, 57, 57);
    private static final int COLOR_INCOME = Color.rgb(27, 145, 90);
    private static final int COLOR_BORDER = Color.rgb(226, 232, 240);

    private final NumberFormat money =
            NumberFormat.getCurrencyInstance(Locale.CANADA);

    private LinearLayout dashboard;
    private LinearLayout list;
    private TextView spentValue;
    private TextView incomeValue;
    private TextView netValue;
    private TextView monthLabel;

    private long start;
    private long end;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void build() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout root = verticalLayout();
        root.setPadding(dp(16), dp(18), dp(16), dp(36));
        scrollView.addView(root);

        TextView title = text("Bank Budget Tracker", 29);
        title.setTextColor(COLOR_TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);

        monthLabel = text("", 15);
        monthLabel.setTextColor(COLOR_MUTED);
        monthLabel.setPadding(0, dp(2), 0, dp(14));
        root.addView(monthLabel);

        LinearLayout summary = horizontalLayout();
        summary.setWeightSum(3f);
        summary.addView(summaryCard("Spent", COLOR_EXPENSE, true), weightedParams());
        summary.addView(space(dp(8)));
        summary.addView(summaryCard("Income", COLOR_INCOME, false), weightedParams());
        summary.addView(space(dp(8)));
        summary.addView(summaryCard("Net", COLOR_PRIMARY, false), weightedParams());
        root.addView(summary);

        TextView actionsTitle = sectionTitle("Quick actions");
        root.addView(actionsTitle);

        LinearLayout actionsRowOne = horizontalLayout();
        Button manual = actionButton("＋ Add", COLOR_PRIMARY);
        manual.setOnClickListener(view -> transactionDialog(null));
        actionsRowOne.addView(manual, weightedParams());
        actionsRowOne.addView(space(dp(8)));

        Button importCsv = actionButton("Import CSV", Color.rgb(75, 85, 99));
        importCsv.setOnClickListener(view -> openCsvImporter());
        actionsRowOne.addView(importCsv, weightedParams());
        root.addView(actionsRowOne);

        LinearLayout actionsRowTwo = horizontalLayout();
        Button exportCsv = actionButton("Export CSV", Color.rgb(75, 85, 99));
        exportCsv.setOnClickListener(view -> openCsvExporter());
        actionsRowTwo.addView(exportCsv, weightedParams());
        actionsRowTwo.addView(space(dp(8)));

        Button notificationAccess = actionButton("Notifications", Color.rgb(75, 85, 99));
        notificationAccess.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception exception) {
                Toast.makeText(this, "Open Settings and search Notification access", Toast.LENGTH_LONG).show();
            }
        });
        actionsRowTwo.addView(notificationAccess, weightedParams());
        root.addView(actionsRowTwo);

        Button category = outlineButton("Manage categories");
        category.setOnClickListener(view -> addCategory());
        LinearLayout.LayoutParams categoryParams = matchWrap();
        categoryParams.topMargin = dp(8);
        root.addView(category, categoryParams);

        root.addView(sectionTitle("Category budgets"));
        dashboard = verticalLayout();
        root.addView(dashboard);

        LinearLayout txHeader = horizontalLayout();
        TextView transactionHeading = sectionTitle("Recent transactions");
        txHeader.addView(transactionHeading, weightedParams());
        Button refresh = compactButton("Refresh");
        refresh.setOnClickListener(view -> refresh());
        txHeader.addView(refresh);
        root.addView(txHeader);

        list = verticalLayout();
        root.addView(list);

        setContentView(scrollView);
    }

    private LinearLayout summaryCard(String label, int accent, boolean spent) {
        LinearLayout card = verticalLayout();
        card.setPadding(dp(12), dp(14), dp(12), dp(14));
        card.setBackground(roundedBackground(COLOR_CARD, dp(16), COLOR_BORDER));

        TextView labelView = text(label, 13);
        labelView.setTextColor(COLOR_MUTED);
        card.addView(labelView);

        TextView value = text("$0.00", 19);
        value.setTextColor(accent);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setPadding(0, dp(5), 0, 0);
        card.addView(value);

        if (spent) {
            spentValue = value;
        } else if ("Income".equals(label)) {
            incomeValue = value;
        } else {
            netValue = value;
        }
        return card;
    }

    private void openCsvImporter() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        startActivityForResult(intent, REQUEST_IMPORT_CSV);
    }

    private void openCsvExporter() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");

        String fileName = "BankBudgetTransactions_"
                + new SimpleDateFormat("yyyy-MM-dd", Locale.CANADA)
                .format(System.currentTimeMillis())
                + ".csv";

        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQUEST_EXPORT_CSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        if (requestCode == REQUEST_IMPORT_CSV) {
            CsvManager.ImportResult result = CsvManager.importTransactions(this, uri);
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
                Toast.makeText(this, "CSV export completed", Toast.LENGTH_LONG).show();
            } catch (Exception exception) {
                Toast.makeText(this, "Export failed: " + exception.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void refresh() {
        calculateRange();

        BudgetDatabase database = BudgetDatabase.get(this);
        List<String> categories = database.getCategories();
        List<Transaction> transactions = database.getTransactions(start, end);

        double expenseTotal = 0;
        double incomeTotal = 0;
        double totalBudget = 0;

        for (Transaction transaction : transactions) {
            String normalizedType = BudgetDatabase.normalizeType(transaction.type);
            if ("EXPENSE".equals(normalizedType)) {
                expenseTotal += Math.abs(transaction.amount);
            } else if ("INCOME".equals(normalizedType)) {
                incomeTotal += Math.abs(transaction.amount);
            }
        }

        for (String category : categories) {
            if (!"Income".equals(category) && !"Transfers".equals(category)) {
                totalBudget += database.getBudget(category);
            }
        }

        monthLabel.setText(new SimpleDateFormat("MMMM yyyy", Locale.CANADA).format(start));
        spentValue.setText(money.format(expenseTotal));
        incomeValue.setText(money.format(incomeTotal));
        netValue.setText(money.format(incomeTotal - expenseTotal));
        netValue.setTextColor(incomeTotal - expenseTotal >= 0 ? COLOR_INCOME : COLOR_EXPENSE);

        dashboard.removeAllViews();

        if (categories.isEmpty()) {
            dashboard.addView(emptyCard("No categories yet."));
        } else {
            for (String category : categories) {
                if ("Income".equals(category) || "Transfers".equals(category)) {
                    continue;
                }

                double spent = database.monthSpending(category, start, end);
                double budget = database.getBudget(category);
                dashboard.addView(categoryCard(category, spent, budget));
            }
        }

        list.removeAllViews();

        if (transactions.isEmpty()) {
            list.addView(emptyCard("No transactions detected this month."));
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, h:mm a", Locale.CANADA);

        for (Transaction transaction : transactions) {
            list.addView(transactionCard(transaction, dateFormat));
        }
    }

    private View categoryCard(String category, double spent, double budget) {
        LinearLayout card = verticalLayout();
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        card.setBackground(roundedBackground(COLOR_CARD, dp(16), COLOR_BORDER));

        LinearLayout.LayoutParams params = matchWrap();
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);

        LinearLayout top = horizontalLayout();
        TextView name = text(category, 16);
        name.setTextColor(COLOR_TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(name, weightedParams());

        TextView amount = text(
                budget > 0
                        ? money.format(spent) + " / " + money.format(budget)
                        : money.format(spent),
                14
        );
        amount.setTextColor(budget > 0 && spent > budget ? COLOR_EXPENSE : COLOR_MUTED);
        top.addView(amount);
        card.addView(top);

        if (budget > 0) {
            ProgressBar progress = new ProgressBar(
                    this,
                    null,
                    android.R.attr.progressBarStyleHorizontal
            );
            progress.setMax(1000);
            int value = (int) Math.min(1000, Math.round((spent / budget) * 1000));
            progress.setProgress(value);
            LinearLayout.LayoutParams progressParams = matchWrap();
            progressParams.topMargin = dp(10);
            progressParams.height = dp(7);
            card.addView(progress, progressParams);

            TextView remaining = text(
                    spent <= budget
                            ? money.format(budget - spent) + " remaining"
                            : money.format(spent - budget) + " over budget",
                    12
            );
            remaining.setTextColor(spent <= budget ? COLOR_MUTED : COLOR_EXPENSE);
            remaining.setPadding(0, dp(7), 0, 0);
            card.addView(remaining);
        } else {
            TextView noBudget = text("Tap to set a monthly budget", 12);
            noBudget.setTextColor(COLOR_MUTED);
            noBudget.setPadding(0, dp(7), 0, 0);
            card.addView(noBudget);
        }

        card.setOnClickListener(view -> budgetDialog(category, budget));
        return card;
    }

    private View transactionCard(Transaction transaction, SimpleDateFormat dateFormat) {
        LinearLayout card = horizontalLayout();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundedBackground(COLOR_CARD, dp(15), COLOR_BORDER));

        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = dp(9);
        card.setLayoutParams(cardParams);

        String normalizedType = BudgetDatabase.normalizeType(transaction.type);
        boolean income = "INCOME".equals(normalizedType);
        boolean transfer = "TRANSFER".equals(normalizedType);

        TextView icon = text(income ? "↓" : transfer ? "↔" : "↑", 20);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(income ? COLOR_INCOME : transfer ? COLOR_PRIMARY : COLOR_EXPENSE);
        icon.setBackground(circleBackground(income ? 0xFFE8F7EF : transfer ? 0xFFEAF2FF : 0xFFFCECEC));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        iconParams.rightMargin = dp(12);
        card.addView(icon, iconParams);

        LinearLayout details = verticalLayout();
        TextView merchant = text(transaction.merchant, 16);
        merchant.setTextColor(COLOR_TEXT);
        merchant.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        merchant.setMaxLines(1);
        details.addView(merchant);

        TextView meta = text(
                transaction.category + " • " + transaction.source
                        + "
" + dateFormat.format(transaction.timestamp),
                12
        );
        meta.setTextColor(COLOR_MUTED);
        meta.setPadding(0, dp(3), 0, 0);
        details.addView(meta);
        card.addView(details, weightedParams());

        TextView amount = text(
                (income ? "+" : transfer ? "" : "-")
                        + money.format(Math.abs(transaction.amount)),
                15
        );
        amount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        amount.setTextColor(income ? COLOR_INCOME : transfer ? COLOR_PRIMARY : COLOR_EXPENSE);
        card.addView(amount);

        card.setOnClickListener(view -> transactionDialog(transaction));
        return card;
    }

    private void transactionDialog(
            Transaction existing
    ) {
        BudgetDatabase database =
                BudgetDatabase.get(this);

        LinearLayout form =
                verticalLayout();

        form.setPadding(
                dp(18),
                dp(4),
                dp(18),
                0
        );

        EditText amount =
                input(
                        "Amount",
                        InputType.TYPE_CLASS_NUMBER
                                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                );

        EditText merchant =
                input(
                        "Merchant or description",
                        InputType.TYPE_CLASS_TEXT
                );

        Spinner category =
                spinner(
                        database.getCategories()
                );

        Spinner type =
                spinner(
                        Arrays.asList(
                                "EXPENSE",
                                "INCOME",
                                "TRANSFER"
                        )
                );

        CheckBox remember =
                new CheckBox(this);

        remember.setText(
                "Remember this merchant category"
        );

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

            merchant.setText(
                    existing.merchant
            );

            select(
                    category,
                    existing.category
            );

            select(
                    type,
                    BudgetDatabase.normalizeType(
                            existing.type
                    )
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
                        .setPositiveButton(
                                "Save",
                                null
                        )
                        .setNegativeButton(
                                "Cancel",
                                null
                        )
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
                                    amount
                                            .getText()
                                            .toString()
                                            .trim()
                            );
                } catch (Exception exception) {
                    amount.setError(
                            "Enter a valid amount"
                    );
                    return;
                }

                String merchantValue =
                        merchant
                                .getText()
                                .toString()
                                .trim();

                if (merchantValue.isEmpty()) {
                    merchant.setError(
                            "Enter a merchant"
                    );
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

                    transaction.currency =
                            "CAD";

                    transaction.merchant =
                            merchantValue;

                    transaction.category =
                            categoryValue;

                    transaction.source =
                            "Manual";

                    transaction.type =
                            typeValue;

                    transaction.rawText =
                            "Manually added";

                    transaction.manual =
                            true;

                    transaction.fingerprint =
                            "manual-"
                                    + System.nanoTime();

                    database.insertTransaction(
                            transaction
                    );

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
                    database.deleteTransaction(
                            existing.id
                    );

                    dialog.dismiss();
                    refresh();
                });
            }
        });

        dialog.show();
    }

    private void addCategory() {
        EditText input =
                input(
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
                                            input
                                                    .getText()
                                                    .toString()
                                    );

                            refresh();
                        }
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void budgetDialog(
            String category,
            double current
    ) {
        EditText input =
                input(
                        "Monthly budget for "
                                + category,
                        InputType.TYPE_CLASS_NUMBER
                                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                );

        if (current > 0) {
            input.setText(
                    String.valueOf(current)
            );
        }

        new AlertDialog.Builder(this)
                .setTitle(
                        category + " budget"
                )
                .setView(input)
                .setPositiveButton(
                        "Save",
                        (dialog, which) -> {
                            try {
                                double value =
                                        Double.parseDouble(
                                                input
                                                        .getText()
                                                        .toString()
                                                        .trim()
                                        );

                                BudgetDatabase.get(this)
                                        .setBudget(
                                                category,
                                                value
                                        );

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
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .show();
    }

    private void calculateRange() {
        Calendar monthStart =
                Calendar.getInstance();

        monthStart.set(
                Calendar.DAY_OF_MONTH,
                1
        );

        monthStart.set(
                Calendar.HOUR_OF_DAY,
                0
        );

        monthStart.set(
                Calendar.MINUTE,
                0
        );

        monthStart.set(
                Calendar.SECOND,
                0
        );

        monthStart.set(
                Calendar.MILLISECOND,
                0
        );

        start =
                monthStart.getTimeInMillis();

        Calendar monthEnd =
                (Calendar) monthStart.clone();

        monthEnd.add(
                Calendar.MONTH,
                1
        );

        monthEnd.add(
                Calendar.MILLISECOND,
                -1
        );

        end =
                monthEnd.getTimeInMillis();
    }

    private LinearLayout verticalLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontalLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 20);
        view.setTextColor(COLOR_TEXT);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(22), 0, dp(10));
        return view;
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(COLOR_TEXT);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private Button actionButton(String value, int backgroundColor) {
        Button button = button(value);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(roundedBackground(backgroundColor, dp(13), backgroundColor));
        button.setMinHeight(dp(48));
        return button;
    }

    private Button outlineButton(String value) {
        Button button = button(value);
        button.setTextColor(COLOR_PRIMARY);
        button.setBackground(roundedBackground(Color.TRANSPARENT, dp(13), COLOR_PRIMARY));
        button.setMinHeight(dp(46));
        return button;
    }

    private Button compactButton(String value) {
        Button button = button(value);
        button.setTextColor(COLOR_PRIMARY);
        button.setTextSize(13);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(10), dp(5), dp(10), dp(5));
        return button;
    }

    private View emptyCard(String message) {
        TextView empty = text(message, 14);
        empty.setTextColor(COLOR_MUTED);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(20), dp(24), dp(20), dp(24));
        empty.setBackground(roundedBackground(COLOR_CARD, dp(16), COLOR_BORDER));
        return empty;
    }

    private EditText input(String hint, int type) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(type);
        input.setPadding(dp(10), dp(10), dp(10), dp(10));
        return input;
    }

    private Spinner spinner(List<String> values) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                values
        ));
        return spinner;
    }

    private void select(Spinner spinner, String value) {
        for (int index = 0; index < spinner.getCount(); index++) {
            if (value.equals(String.valueOf(spinner.getItemAtPosition(index)))) {
                spinner.setSelection(index);
                return;
            }
        }
    }

    private LinearLayout.LayoutParams weightedParams() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private View space(int width) {
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(width, 1));
        return space;
    }

    private GradientDrawable roundedBackground(int fillColor, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable circleBackground(int fillColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fillColor);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
