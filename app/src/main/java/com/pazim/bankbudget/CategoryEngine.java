package com.pazim.bankbudget;
import android.content.Context;
import java.util.*;
public final class CategoryEngine {
 private static final Map<String,String[]> RULES=new LinkedHashMap<>();
 static {
  RULES.put("Groceries",new String[]{"WALMART","WAL-MART","FOODASIA","COSTCO","MAXI","METRO","SHOPPERS DRUG MART","MARCHE","SUPERMARKET","FOOD BASICS","FOODBASICS","NO FRILLS","NOFRILLS"});
  RULES.put("Fuel",new String[]{"SHELL","ULTRAMAR","PETRO-CANADA","PETROCANADA","PETRO","ESSO","GAS+","COSTCOGAS","COSTCO GAS","COUCHE-TARD","ESSENCE"});
  RULES.put("Bills",new String[]{"FIDO","INSURANCE","ROGERS","PREMIUM","PRIMMUM","BUZZFIT","RENT","AGENCE","AVIVA","ENBRIDGE","ALECTRA","ENERCARE","BELL","TELUS"});
  RULES.put("Eating Out",new String[]{"TIM HORTONS","TIMHORTONS","KINTON","BOUSTAN","MCDONALD","UBEREATS","UBER EATS","POULET","RAMEN","BURGER","PUB","RESTAURANT","SUBWAY","BERGHAM","CHATIME","DESI","ONROUTE","CHOLEBHATURE"});
  RULES.put("Shopping",new String[]{"AMAZON","WINNERS","MARSHALLS","HOMESENSE","IKEA","CANADIAN TIRE","DOLLARAMA","BEST BUY","HOME DEPOT"});
  RULES.put("Transport",new String[]{"UBER","LYFT","PRESTO","GO TRANSIT","TTC","PARKING"});
  RULES.put("Health",new String[]{"PHARMACY","DENTAL","DENTIST","CLINIC","HOSPITAL"});
  RULES.put("Entertainment",new String[]{"NETFLIX","SPOTIFY","CINEPLEX","DISNEY","YOUTUBE","PRIME VIDEO"});
 }
 private CategoryEngine(){}
 public static String categorize(Context c,String merchant,String raw){
  String combined=normalize(merchant+" "+raw);
  String learned=BudgetDatabase.get(c).findLearnedCategory(combined);
  if(learned!=null&&!learned.isEmpty()) return learned;
  for(Map.Entry<String,String[]> e:RULES.entrySet()) for(String k:e.getValue()) if(combined.contains(normalize(k))) return e.getKey();
  if(any(combined,"PAYROLL","DEPOSIT","SALARY","CREDIT RECEIVED")) return "Income";
  if(any(combined,"TRANSFER","E TRANSFER","INTERAC")) return "Transfers";
  return "Other";
 }
 public static String normalize(String s){
  if(s==null)return "";
  return s.toUpperCase(Locale.CANADA).replace("&AMP;","&").replace("&#39;","'").replaceAll("[^A-Z0-9+]+"," ").replaceAll("\\s+"," ").trim();
 }
 private static boolean any(String s,String...v){for(String x:v)if(s.contains(x))return true;return false;}
}
