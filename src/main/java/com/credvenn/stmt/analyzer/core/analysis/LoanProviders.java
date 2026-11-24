package com.credvenn.stmt.analyzer.core.analysis;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Source-backed, repayment-only identifiers. Generic banking paybills are deliberately absent. */
final class LoanProviders {
    record Match(String name, String basis) { }
    private record Provider(String name, List<String> paybills, List<String> aliases) { }
    private static final List<Provider> PROVIDERS = load();
    private static final Pattern RECIPIENT = Pattern.compile("\\b(?:TO|FROM(?: BUSINESS)?) +[0-9]{5,8} +- +(.+)");
    static String recipient(String text) {
        var matcher=RECIPIENT.matcher(text);
        return matcher.find()?matcher.group(1).split(" (?:ACC\\.?|ACCOUNT|VIA)\\b",2)[0].strip():"";
    }
    static Match match(String paybill, String text, boolean allowPaybill) {
        if(allowPaybill) for(var p:PROVIDERS)if(p.paybills().contains(paybill))return new Match(p.name(),"verified repayment paybill");
        String recipient=recipient(text);
        for(var p:PROVIDERS)for(var alias:p.aliases())
            if(Pattern.compile("^"+Pattern.quote(alias)+"(?:$|[^A-Z0-9])").matcher(recipient).find())
                return new Match(p.name(),"recipient-name alias");
        return null;
    }
    static boolean genericLender(String text) {
        return Pattern.compile("\\b(?:MICROFINANCE|CREDIT)\\b").matcher(recipient(text)).find();
    }
    private static List<Provider> load() {
        try(var in=LoanProviders.class.getResourceAsStream("/loan-providers.tsv")) {
            if(in==null)throw new IllegalStateException("Missing loan provider registry");
            var rows=new String(in.readAllBytes(),StandardCharsets.UTF_8).lines().filter(l->!l.isBlank()&&!l.startsWith("#")).toList();
            var result=new ArrayList<Provider>();var paybills=new HashSet<String>();
            for(var line:rows) {
                var fields=line.split("\t",-1);
                if(fields.length!=5)throw new IllegalStateException("Invalid provider registry row");
                var bills=fields[1].isBlank()?List.<String>of():List.of(fields[1].split(";"));
                for(var bill:bills)if(!bill.matches("[0-9]{5,8}")||!paybills.add(bill))throw new IllegalStateException("Invalid or duplicate provider paybill");
                result.add(new Provider(fields[0],bills,Arrays.stream(fields[2].split(";")).map(NarrationClassifier::normalize).toList()));
            }
            return List.copyOf(result);
        }catch(IOException e){throw new IllegalStateException("Cannot load provider registry",e);}
    }
}
