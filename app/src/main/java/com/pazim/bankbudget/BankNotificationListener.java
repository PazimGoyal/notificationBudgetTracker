package com.pazim.bankbudget;
import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.service.notification.*;
public class BankNotificationListener extends NotificationListenerService {
 @Override public void onNotificationPosted(StatusBarNotification sbn){
  try{
   String pkg=sbn.getPackageName(),label=label(pkg);if(!NotificationParser.supportedSource(pkg,label))return;
   Bundle e=sbn.getNotification().extras;
   Transaction t=NotificationParser.parse(pkg,label,s(e.getCharSequence(Notification.EXTRA_TITLE)),s(e.getCharSequence(Notification.EXTRA_TEXT)),s(e.getCharSequence(Notification.EXTRA_BIG_TEXT)),sbn.getPostTime());
   if(t==null)return;BudgetDatabase db=BudgetDatabase.get(this);if(db.isDuplicate(t))return;t.category=CategoryEngine.categorize(this,t.merchant,t.rawText);db.insertTransaction(t);
  }catch(Exception ignored){}
 }
 private String label(String pkg){try{ApplicationInfo i=getPackageManager().getApplicationInfo(pkg,0);return getPackageManager().getApplicationLabel(i).toString();}catch(Exception e){return pkg;}}
 private String s(CharSequence x){return x==null?"":x.toString();}
}
