package com.junzhou.test.support;

import com.junzhou.mapper.ReportMapper;
import com.junzhou.report.ReportFilter;
import com.junzhou.report.ReportRow;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Delegating mapper that counts aggregate queries so cache hits can be evidenced by SQL calls. */
public final class CountingReportMapper implements ReportMapper {
    private final ReportMapper delegate;
    private final Map<String, AtomicInteger> calls = new TreeMap<>();

    public CountingReportMapper(ReportMapper delegate) {
        this.delegate = delegate;
    }

    private <T> T count(String statement, Supplier<T> call) {
        synchronized (calls) {
            calls.computeIfAbsent(statement, key -> new AtomicInteger()).incrementAndGet();
        }
        return call.get();
    }

    public int calls(String statement) {
        synchronized (calls) {
            AtomicInteger value = calls.get(statement);
            return value == null ? 0 : value.get();
        }
    }

    public int total() {
        synchronized (calls) {
            int total = 0;
            for (AtomicInteger value : calls.values()) total += value.get();
            return total;
        }
    }

    public void reset() {
        synchronized (calls) {
            calls.clear();
        }
    }

    @Override public List<ReportRow> daily(ReportFilter f) { return count("daily", () -> delegate.daily(f)); }
    @Override public List<ReportRow> sales(ReportFilter f) { return count("sales", () -> delegate.sales(f)); }
    @Override public List<ReportRow> users(ReportFilter f) { return count("users", () -> delegate.users(f)); }
    @Override public List<ReportRow> itemDetails(ReportFilter f) { return count("itemDetails", () -> delegate.itemDetails(f)); }
    @Override public List<ReportRow> ledger(ReportFilter f) { return count("ledger", () -> delegate.ledger(f)); }
    @Override public List<ReportRow> overview(ReportFilter f) { return count("overview", () -> delegate.overview(f)); }
    @Override public List<ReportRow> menu(ReportFilter f, String kind) { return count("menu", () -> delegate.menu(f, kind)); }
}
