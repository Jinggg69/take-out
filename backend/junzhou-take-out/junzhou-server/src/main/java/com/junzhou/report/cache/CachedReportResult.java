package com.junzhou.report.cache;

import java.io.Serializable;
import java.util.Map;

/**
 * Serialized Redis envelope for one cached report result.
 *
 * <p>Only the display payload is stored: never a {@code Result}, an HTTP response, an employee or a
 * permission decision.</p>
 */
public class CachedReportResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Bumped together with the key version whenever the payload contract changes. */
    public static final int SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final Map<String, Object> data;

    public CachedReportResult(Map<String, Object> data) {
        this(SCHEMA_VERSION, data);
    }

    public CachedReportResult(int schemaVersion, Map<String, Object> data) {
        this.schemaVersion = schemaVersion;
        this.data = data;
    }

    public int getSchemaVersion() { return schemaVersion; }

    public Map<String, Object> getData() { return data; }
}
