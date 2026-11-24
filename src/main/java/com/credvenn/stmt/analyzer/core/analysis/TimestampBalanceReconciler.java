package com.credvenn.stmt.analyzer.core.analysis;

import com.credvenn.stmt.analyzer.core.AnalysisResult.*;
import com.credvenn.stmt.analyzer.core.parsing.ParsedStatement.TransactionDraft;
import java.math.BigDecimal;
import java.util.*;

/** Resolve timestamp-group boundaries without inspecting the following transaction. */
final class TimestampBalanceReconciler {
    record Result(BigDecimal closing, boolean completeChain, BigDecimal absoluteDiscrepancy) { }
    private static BigDecimal signed(TransactionDraft row) {
        return row.direction()==Direction.CREDIT ? row.amount() : row.amount().negate();
    }
    private static boolean sameGroup(TransactionDraft a, TransactionDraft b) {
        return "Completed".equalsIgnoreCase(a.transactionStatus()) && "Completed".equalsIgnoreCase(b.transactionStatus())
                && a.transactionAt()!=null && b.transactionAt()!=null && a.transactionAt().isEqual(b.transactionAt());
    }
    Result check(List<TransactionDraft> rows, BigDecimal opening, List<Diagnostic> errors, List<Diagnostic> warnings) {
        BigDecimal carry=opening;
        BigDecimal discrepancy=BigDecimal.ZERO;
        boolean complete=opening!=null;
        int resolved=0,withoutBalances=0;
        for(int start=0;start<rows.size();) {
            int end=start+1;
            while(end<rows.size() && sameGroup(rows.get(start),rows.get(end))) end++;
            var group=rows.subList(start,end);
            if(group.size()==1) {
                var row=group.getFirst();
                BigDecimal expected=carry==null?null:carry.add(signed(row));
                if(expected!=null && row.runningBalance()!=null && expected.compareTo(row.runningBalance())!=0) {
                    var difference=row.runningBalance().subtract(expected);
                    discrepancy=discrepancy.add(difference.abs());
                    errors.add(new Diagnostic("RUNNING_BALANCE_MISMATCH",
                            "Expected balance="+expected+", observed balance="+row.runningBalance()+", difference="+difference,
                            row.sourceRowKey(),null));
                }
                if(row.runningBalance()==null) complete=false;
                carry=row.runningBalance()==null?expected:row.runningBalance();
            } else {
                BigDecimal movement=group.stream().map(TimestampBalanceReconciler::signed).reduce(BigDecimal.ZERO,BigDecimal::add);
                boolean any=group.stream().anyMatch(r->r.runningBalance()!=null);
                if(group.stream().anyMatch(r->r.runningBalance()==null)) complete=false;
                if(!any) {
                    carry=carry==null?null:carry.add(movement);
                    withoutBalances++;complete=false;
                } else if(carry!=null) {
                    BigDecimal expected=carry.add(movement);
                    // The ending balance must be witnessed within the group. We never
                    // choose a candidate simply because it matches the NEXT row.
                    if(group.stream().anyMatch(r->r.runningBalance()!=null && r.runningBalance().compareTo(expected)==0)) {
                        carry=expected;resolved++;
                    } else {
                        errors.add(StatementReconciler.issue("TIMESTAMP_GROUP_BALANCE_MISMATCH",group.getFirst().sourceRowKey()));
                        carry=null;complete=false;
                    }
                } else {
                    BigDecimal inferred=uniqueTrailEnd(group);
                    if(inferred!=null) {carry=inferred;resolved++;}
                    else {
                        errors.add(StatementReconciler.issue("TIMESTAMP_GROUP_END_UNRESOLVED",group.getFirst().sourceRowKey()));
                        complete=false;
                    }
                }
            }
            start=end;
        }
        if(resolved>0) warnings.add(new Diagnostic("TIMESTAMP_GROUP_BALANCE_RESOLVED",
                "Resolved ending balances for "+resolved+" completed timestamp groups using group arithmetic and displayed balances.",null,null));
        if(withoutBalances>0) warnings.add(new Diagnostic("TIMESTAMP_GROUP_BALANCES_UNAVAILABLE",
                "No displayed balances for "+withoutBalances+" timestamp groups; summary totals are needed for reconciliation.",null,null));
        return new Result(carry,complete,discrepancy);
    }
    /** With unknown opening, require every row to form one connected directed Euler trail.
     * A multi-node cycle has no identifiable ending balance and remains unresolved. */
    private static BigDecimal uniqueTrailEnd(List<TransactionDraft> rows) {
        var degree=new HashMap<BigDecimal,Integer>();
        var graph=new HashMap<BigDecimal,Set<BigDecimal>>();
        for(var r:rows) {
            if(r.runningBalance()==null) return null;
            var after=r.runningBalance().stripTrailingZeros();
            var before=after.subtract(signed(r)).stripTrailingZeros();
            degree.merge(before,1,Integer::sum);degree.merge(after,-1,Integer::sum);
            graph.computeIfAbsent(before,k->new HashSet<>()).add(after);
            graph.computeIfAbsent(after,k->new HashSet<>()).add(before);
        }
        BigDecimal first=null,last=null;
        for(var entry:degree.entrySet()) {
            if(entry.getValue()==1 && first==null) first=entry.getKey();
            else if(entry.getValue()==-1 && last==null) last=entry.getKey();
            else if(entry.getValue()!=0) return null;
        }
        if(first==null || last==null) return null;
        var visited=new HashSet<BigDecimal>();var todo=new ArrayDeque<BigDecimal>();todo.add(first);
        while(!todo.isEmpty()) {var node=todo.remove();if(visited.add(node))todo.addAll(graph.get(node));}
        return visited.size()==graph.size()?last:null;
    }
}
