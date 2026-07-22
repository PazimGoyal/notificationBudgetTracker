package com.pazim.bankbudget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    private final NumberFormat money = NumberFormat.getCurrencyInstance(Locale.CANADA);
    private LinearLayout dashboard;
    private LinearLayout list;
    private TextView total;
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
        LinearLayout root = verticalLayout();
        root.setPadding(dp(18), dp(18), dp(18), dp(30));
        scrollView.addView(root);

        root.addView(text("Bank Budget Tracker", 28));

        TextView privacy = text(
                "All detected transactions remain on this phone. Enable notification access, then bank and Google Wallet transaction notifications will be analyzed automatically.",
                14
        );
        privacy.setPadding(0, dp(7), 0, dp(12));
        root.addView(privacy);

        Button notificationAccess = button("Enable notification access");
        notificationAccess.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception exception) {
                Toast.makeText(this, "Open Settings and search Notification access", Toast.LENGTH_LONG).show();
            }
        });
        root.addView(notificationAccess);

        Button manual = button("Add transaction manually");
        manual.setOnClickListener(view -> transactionDialog(null));
        root.addView(manual);

        Button category = button("Add category");
        category.setOnClickListener(view -> addCategory());
        root.addView(category);

        total = text("", 22);
        total.setPadding(0, dp(18), 0, dp(6));
        root.addView(total);

        root.addView(text("Category budgets", 20));
        dashboard = verticalLayout();
        root.addView(dashboard);

        TextView transactionHeading = text("Transactions", 20);
        transactionHeading.setPadding(0, dp(18), 0, dp(6));
        root.addView(transactionHeading);

        list = verticalLayout();
        root.addView(list);

        Button refresh = button("Refresh");
        refresh.setOnClickListener(view -> refresh());
        root.addView(refresh);

        setContentView(scrollView);
    }

    private void refresh() {
        calculateRange();

        BudgetDatabase database = BudgetDatabase.get(this);
        List<String> categories = database.getCategories();
        List<Transaction> transactions = database.getTransactions(start, end);

        double expenseTotal = 0;
        double incomeTotal = 0;

        for (Transaction transaction : transactions) {
            String normalizedType = BudgetDatabase.normalizeType(transaction.type);

            if ("EXPENSE".equals(normalizedType)) {
                expenseTotal += Math.abs(transaction.amount);
            } else if ("INCOME".equals(normalizedType)) {
                incomeTotal += Math.abs(transaction.amount);
            }
        }

        total.setText(
                "Spent: " + money.format(expenseTotal)
                        + "\nIncome: " + money.format(incomeTotal)
                        + "\nNet: " + money.format(incomeTotal - expenseTotal)
        );

        dashboard.removeAllViews();
        for (String category : categories) {
            if ("Income".equals(category) || "Transfers".equals(category)) {
                continue;
            }

            double spent = database.monthSpending(category, start, end);
            double budget = database.getBudget(category);

            Button row = button(
                    category + ": " + money.format(spent)
                            + (budget > 0 ? " / " + money.format(budget) : " / no budget")
            );
            row.setAllCaps(false);
            row.setOnClickListener(view -> budgetDialog(category, budget));
            dashboard.addView(row);
        }

        list.removeAllViews();
        if (transactions.isEmpty()) {
            list.addView(text("No transactions detected this month.", 15));
            return;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, h:mm a", Locale.CANADA);
        for (Transaction transaction : transactions) {
            String normalizedType = BudgetDatabase.normalizeType(transaction.type);
            String sign = "INCOME".equals(normalizedType) ? "+" : "EXPENSE".equals(normalizedType) ? "-" : "";

            Button item = button(
                    sign + money.format(Math.abs(transaction.amount))
                            + " • " + transaction.merchant
                            + "\n" + transaction.category
                            + " • " + transaction.source
                            + " • " + dateFormat.format(transaction.timestamp)
            );
            item.setAllCaps(false);
            item.setOnClickListener(view -> transactionDialog(transaction));
            list.addView(item);
        }
    }

    private void transactionDialog(Transaction existing) {
        BudgetDatabase database = BudgetDatabase.get(this);
        LinearLayout form = verticalLayout();
        form.setPadding(dp(18), dp(4), dp(18), 0);

        EditText amount = input(
                "Amount",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        EditText merchant = input("Merchant or description", InputType.TYPE_CLASS_TEXT);
        Spinner category = spinner(database.getCategories());
        Spinner type = spinner(Arrays.asList("EXPENSE", "INCOME", "TRANSFER"));
        CheckBox remember = new CheckBox(this);
        remember.setText("Remember this merchant category");

        form.addView(amount);
        form.addView(merchant);
        form.addView(category);
        form.addView(type);
        form.addView(remember);

        if (existing != null) {
            amount.setText(String.valueOf(Math.abs(existing.amount)));
            merchant.setText(existing.merchant);
            select(category, existing.category);
            select(type, BudgetDatabase.normalizeType(existing.type));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add transaction" : "Edit transaction")
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton(existing == null ? null : "Delete", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                double parsedAmount;
                try {
                    parsedAmount = Double.parseDouble(amount.getText().toString().trim());
                } catch (Exception exception) {
                    amount.setError("Enter a valid amount");
                    return;
                }

                String merchantValue = merchant.getText().toString().trim();
                if (merchantValue.isEmpty()) {
                    merchant.setError("Enter a merchant");
                    return;
                }

                String categoryValue = String.valueOf(category.getSelectedItem());
                String typeValue = BudgetDatabase.normalizeType(String.valueOf(type.getSelectedItem()));

                if (existing == null) {
                    Transaction transaction = new Transaction();
                    transaction.timestamp = System.currentTimeMillis();
                    transaction.amount = Math.abs(parsedAmount);
                    transaction.currency = "CAD";
                    transaction.merchant = merchantValue;
                    transaction.category = categoryValue;
                    transaction.source = "Manual";
                    transaction.type = typeValue;
                    transaction.rawText = "Manually added";
                    transaction.manual = true;
                    transaction.fingerprint = "manual-" + System.nanoTime();

                    database.insertTransaction(transaction);

                    if (remember.isChecked()) {
                        database.saveMerchantRule(merchantValue, categoryValue);
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
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                    database.deleteTransaction(existing.id);
                    dialog.dismiss();
                    refresh();
                });
            }
        });

        dialog.show();
    }

    private void addCategory() {
        EditText input = input("Category name", InputType.TYPE_CLASS_TEXT);

        new AlertDialog.Builder(this)
                .setTitle("Add category")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    BudgetDatabase.get(this).addCategory(input.getText().toString());
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void budgetDialog(String category, double current) {
        EditText input = input(
                "Monthly budget for " + category,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );

        if (current > 0) {
            input.setText(String.valueOf(current));
        }

        new AlertDialog.Builder(this)
                .setTitle(category + " budget")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        double value = Double.parseDouble(input.getText().toString().trim());
                        BudgetDatabase.get(this).setBudget(category, value);
                        refresh();
                    } catch (Exception exception) {
                        Toast.makeText(this, "Invalid budget", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void calculateRange() {
        Calendar monthStart = Calendar.getInstance();
        monthStart.set(Calendar.DAY_OF_MONTH, 1);
        monthStart.set(Calendar.HOUR_OF_DAY, 0);
        monthStart.set(Calendar.MINUTE, 0);
        monthStart.set(Calendar.SECOND, 0);
        monthStart.set(Calendar.MILLISECOND, 0);
        start = monthStart.getTimeInMillis();

        Calendar monthEnd = (Calendar) monthStart.clone();
        monthEnd.add(Calendar.MONTH, 1);
        monthEnd.add(Calendar.MILLISECOND, -1);
        end = monthEnd.getTimeInMillis();
    }

    private LinearLayout verticalLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, float size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        return text;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        return button;
    }

    private EditText input(String hint, int type) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setInputType(type);
        input.setPadding(dp(8), dp(8), dp(8), dp(8));
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
