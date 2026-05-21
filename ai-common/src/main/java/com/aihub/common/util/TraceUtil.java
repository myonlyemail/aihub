package com.aihub.common.util;

import cn.hutool.core.util.IdUtil;

public class TraceUtil {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String getTraceId() {
        String traceId = TRACE_ID.get();
        if (traceId == null) {
            traceId = IdUtil.fastSimpleUUID();
            TRACE_ID.set(traceId);
        }
        return traceId;
    }

    public static void remove() {
        TRACE_ID.remove();
    }
}
