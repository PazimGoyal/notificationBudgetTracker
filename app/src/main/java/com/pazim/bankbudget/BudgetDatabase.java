package com.pazim.bankbudget;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
public final class BudgetDatabase extends SQLiteOpenHelper {
 private static BudgetDatabase instance;
 public static synchronized BudgetDatabase get(Context c){if(instance==null)instance=new BudgetDatabase(c.getApplicationContext());return instance;}
 private BudgetDatabase(Context c){super(c,"bank_budget.db",null,1);}
 public void onCreate(SQLiteDatabase db){
  db.execSQL("CREATE TABLE transactions(id INTEGER PRIMARY KEY AUTOINCREMENT,timestamp INTEGER NOT NULL,amount REAL NOT NULL,currency TEXT NOT NULL,merchant TEXT NOT NULL,category TEXT NOT NULL,source TEXT NOT NULL,type TEXT NOT NULL,raw_text TEXT NOT NULL,manual INTEGER NOT NULL DEFAULT 0,fingerprint TEXT UNIQUE)");
  db.execSQL("CREATE TABLE categories(name TEXT PRIMARY KEY COLLATE NOCASE,monthly_budget REAL NOT NULL DEFAULT 0)");
  db.execSQL("CREATE TABLE merchant_rules(keyword TEXT PRIMARY KEY COLLATE NOCASE,category TEXT NOT NULL)");
  for(String c:new String[]{"Bills","Groceries","Shopping","Eating Out","Fuel","Transport","Entertainment","Health","Transfers","Income","Other"}){ContentValues v=new ContentValues();v.put("name",c);db.insert("categories",null,v);}
 }
 public void onUpgrade(SQLiteDatabase db,int o,int n){}
 public synchronized long insertTransaction(Transaction t){ContentValues v=new ContentValues();v.put("timestamp",t.timestamp);v.put("amount",t.amount);v.put("currency",t.currency);v.put("merchant",t.merchant);v.put("category",t.category);v.put("source",t.source);v.put("type",t.type);v.put("raw_text",t.rawText==null?"":t.rawText);v.put("manual",t.manual?1:0);v.put("fingerprint",t.fingerprint);return getWritableDatabase().insertWithOnConflict("transactions",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 public synchronized boolean isDuplicate(Transaction t){
  try(Cursor c=getReadableDatabase().rawQuery("SELECT source,merchant FROM transactions WHERE ABS(amount-?)<0.01 AND timestamp BETWEEN ? AND ? AND type=? ORDER BY timestamp DESC LIMIT 10",new String[]{String.valueOf(t.amount),String.valueOf(t.timestamp-120000L),String.valueOf(t.timestamp+120000L),t.type})){
   while(c.moveToNext()){String s=c.getString(0),m=c.getString(1);boolean wallet=("Google Wallet".equals(t.source)&&!"Google Wallet".equals(s))||(!"Google Wallet".equals(t.source)&&"Google Wallet".equals(s));String a=CategoryEngine.normalize(m),b=CategoryEngine.normalize(t.merchant);boolean same=!a.isEmpty()&&!b.isEmpty()&&(a.contains(b)||b.contains(a));if(wallet||same)return true;}
  }return false;
 }
 public synchronized List<Transaction> getTransactions(long start,long end){List<Transaction> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,timestamp,amount,currency,merchant,category,source,type,raw_text,manual,fingerprint FROM transactions WHERE timestamp BETWEEN ? AND ? ORDER BY timestamp DESC",new String[]{String.valueOf(start),String.valueOf(end)})){while(c.moveToNext())out.add(from(c));}return out;}
 public synchronized void updateTransaction(long id,double amount,String merchant,String category,String type,boolean remember){ContentValues v=new ContentValues();v.put("amount",amount);v.put("merchant",merchant.trim());v.put("category",category);v.put("type",type);getWritableDatabase().update("transactions",v,"id=?",new String[]{String.valueOf(id)});if(remember&&!merchant.trim().isEmpty())saveMerchantRule(merchant,category);}
 public synchronized void deleteTransaction(long id){getWritableDatabase().delete("transactions","id=?",new String[]{String.valueOf(id)});}
 public synchronized List<String> getCategories(){List<String> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM categories ORDER BY name COLLATE NOCASE",null)){while(c.moveToNext())out.add(c.getString(0));}return out;}
 public synchronized void addCategory(String name){if(name==null||name.trim().isEmpty())return;ContentValues v=new ContentValues();v.put("name",name.trim());getWritableDatabase().insertWithOnConflict("categories",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
 public synchronized void setBudget(String cat,double budget){ContentValues v=new ContentValues();v.put("monthly_budget",Math.max(0,budget));getWritableDatabase().update("categories",v,"name=?",new String[]{cat});}
 public synchronized double getBudget(String cat){try(Cursor c=getReadableDatabase().rawQuery("SELECT monthly_budget FROM categories WHERE name=?",new String[]{cat})){return c.moveToFirst()?c.getDouble(0):0;}}
 public synchronized double monthSpending(String cat,long start,long end){try(Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE category=? AND type='DEBIT' AND timestamp BETWEEN ? AND ?",new String[]{cat,String.valueOf(start),String.valueOf(end)})){return c.moveToFirst()?c.getDouble(0):0;}}
 public synchronized String findLearnedCategory(String text){try(Cursor c=getReadableDatabase().rawQuery("SELECT keyword,category FROM merchant_rules ORDER BY LENGTH(keyword) DESC",null)){while(c.moveToNext()){String k=CategoryEngine.normalize(c.getString(0));if(!k.isEmpty()&&text.contains(k))return c.getString(1);}}return null;}
 public synchronized void saveMerchantRule(String merchant,String cat){ContentValues v=new ContentValues();v.put("keyword",CategoryEngine.normalize(merchant));v.put("category",cat);getWritableDatabase().insertWithOnConflict("merchant_rules",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
 private Transaction from(Cursor c){Transaction t=new Transaction();t.id=c.getLong(0);t.timestamp=c.getLong(1);t.amount=c.getDouble(2);t.currency=c.getString(3);t.merchant=c.getString(4);t.category=c.getString(5);t.source=c.getString(6);t.type=c.getString(7);t.rawText=c.getString(8);t.manual=c.getInt(9)==1;t.fingerprint=c.getString(10);return t;}
}
