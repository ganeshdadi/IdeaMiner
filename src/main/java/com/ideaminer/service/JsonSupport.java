package com.ideaminer.service;

import java.util.List;

final class JsonSupport {

    private JsonSupport() {
    }

    static String array(List<String> values) {
        return values.stream()
                .map(JsonSupport::quote)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    static String object(String... keyValues) {
        StringBuilder builder = new StringBuilder("{");
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(quote(keyValues[index])).append(':').append(quote(keyValues[index + 1]));
        }
        return builder.append('}').toString();
    }

    static String sourceSpan(int beginLine, int endLine) {
        return "{\"beginLine\":" + beginLine + ",\"endLine\":" + endLine + "}";
    }

    static String quote(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
