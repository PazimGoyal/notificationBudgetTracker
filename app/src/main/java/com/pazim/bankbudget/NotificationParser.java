package com.pazim.bankbudget;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
public final class NotificationParser {
 private static final Pattern[] AMOUNTS={Pattern.compile("(?i)(?:CAD|C\\$|\\$)\\s*([0-9][0-9,]*(?:\\.\\d{1,2})?)"),Pattern.compile("(?i)([0-9][0-9,]*(?:\\.\\d{1,2})?)\\s*(?:CAD|C\\$)")};
 // private static final String[] WORDS={"PURCHASE","SPENT","CHARGE","TRANSACTION","PAYMENT","DEBIT","WITHDRAWAL","REFUND","DEPOSIT","CREDIT","TRANSFER","E-TRANSFER","CARD"};
private static final String[] WORDS = new String[]{
        "AUTHORIZATION",
        "PURCHASE",
        "SPENT",
        "CHARGE",
        "CHARGED",
        "TRANSACTION",
        "PAYMENT",
        "DEBIT",
        "WITHDRAWAL",
        "REFUND",
        "DEPOSIT",
        "CREDIT",
        "TRANSFER",
        "E-TRANSFER",
        "E TRANSFER",
        "INTERAC",
        "CARD"
};
 private NotificationParser(){}
 public static boolean supportedSource(String pkg,String label){
  String v=((pkg==null?"":pkg)+" "+(label==null?"":label)).toLowerCase(Locale.CANADA);
  return v.contains("scotia")||v.contains("cibc")||v.contains("google.android.apps.wallet")||v.contains("google wallet")||v.contains("wallet")||v.contains("td");
 }
 public static Transaction parse(String pkg,String label,String title,String text,String big,long posted){
  String raw=join(title,text,big), norm=CategoryEngine.normalize(raw);
  if(raw.trim().isEmpty()||!hasWord(norm))return null;
  Double amount=amount(raw); if(amount==null||amount<=0)return null;
  Transaction tx=new Transaction(); tx.timestamp=posted; tx.amount=amount; tx.currency=raw.toUpperCase(Locale.CANADA).contains("USD")?"USD":"CAD";
  tx.source=source(pkg,label); tx.type=type(norm); tx.rawText=raw; tx.manual=false; tx.merchant=merchant(raw,norm); tx.fingerprint=fingerprint(tx); return tx;
 }
 private static Double amount(String raw){for(Pattern p:AMOUNTS){Matcher m=p.matcher(raw);if(m.find())try{return Double.parseDouble(m.group(1).replace(",",""));}catch(Exception ignored){}}return null;}
 // private static String type(String n){if(any(n,"REFUND","CREDIT","DEPOSIT","PAYROLL"))return "CREDIT";if(any(n,"TRANSFER","E TRANSFER","INTERAC"))return "TRANSFER";return "DEBIT";}
private static String type(String normalized) {

    // Money sent between accounts.
    if (containsAny(
            normalized,
            "INTERAC E TRANSFER SENT",
            "E TRANSFER SENT",
            "TRANSFER SENT",
            "INTERAC SENT"
    )) {
        return "TRANSFER";
    }

    // Credit-card authorizations and purchases are expenses.
    // This must be checked before looking for the word CREDIT.
    if (containsAny(
            normalized,
            "AUTHORIZATION ON YOUR CREDIT ACCOUNT",
            "THERE WAS AN AUTHORIZATION FOR",
            "PURCHASE",
            "SPENT",
            "CHARGED",
            "CHARGE",
            "PAYMENT",
            "DEBIT",
            "WITHDRAWAL",
            "PAID"
    )) {
        return "EXPENSE";
    }

    // Actual incoming money.
    if (containsAny(
            normalized,
            "REFUND",
            "PAYROLL",
            "SALARY",
            "DEPOSIT RECEIVED",
            "CREDIT RECEIVED",
            "MONEY RECEIVED",
            "E TRANSFER RECEIVED",
            "INTERAC E TRANSFER RECEIVED"
    )) {
        return "INCOME";
    }

    return "EXPENSE";
}
 private static String merchant(String raw,String norm){
  String u=raw.toUpperCase(Locale.CANADA);
  for(String marker:new String[]{" AT "," FROM "," TO "," MERCHANT "}){int i=u.indexOf(marker);if(i>=0){String m=raw.substring(i+marker.length()).replaceAll("(?i)\\b(?:ON|USING|WITH|CARD ENDING|CARD)\\b.*$","").replaceAll("[\\r\\n]+"," ").trim();if(!m.isEmpty())return shortv(m,70);}}
  String c=norm.replaceAll("(?i)(CAD|USD|PURCHASE|SPENT|CHARGE|TRANSACTION|PAYMENT|DEBIT|CREDIT)"," ").replaceAll("[0-9,.]+"," ").replaceAll("\\s+"," ").trim();
  return c.isEmpty()?"Unknown merchant":shortv(c,70);
 }
 private static String source(String pkg,String label){String v=((pkg==null?"":pkg)+" "+(label==null?"":label)).toLowerCase(Locale.CANADA);if(v.contains("scotia"))return "Scotiabank";if(v.contains("cibc"))return "CIBC";if(v.contains("google.android.apps.wallet")||v.contains("google wallet"))return "Google Wallet";if(v.contains("td"))return "TD";return label==null||label.isEmpty()?pkg:label;}
 private static String fingerprint(Transaction tx){String b=String.format(Locale.CANADA,"%.2f|%s|%s|%d",tx.amount,CategoryEngine.normalize(tx.merchant),tx.type,tx.timestamp/120000L);try{byte[] x=MessageDigest.getInstance("SHA-256").digest(b.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte y:x)s.append(String.format("%02x",y));return s.toString();}catch(Exception e){return String.valueOf(b.hashCode());}}
 private static String join(String...v){StringBuilder s=new StringBuilder();for(String x:v)if(x!=null&&!x.trim().isEmpty()){if(s.length()>0)s.append("\n");s.append(x.trim());}return s.toString();}
 private static boolean hasWord(String n){for(String w:WORDS)if(n.contains(w))return true;return false;}
 private static boolean any(String s,String...v){for(String x:v)if(s.contains(x))return true;return false;}
 private static String shortv(String s,int n){return s.length()<=n?s:s.substring(0,n);}
}
