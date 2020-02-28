package com.github.thake.avro4k.compiler;

import org.apache.avro.Schema;

import java.time.Instant;

public class TimestampMillisTypeConversion extends SerializableLogicalTypeConversion {
    public TimestampMillisTypeConversion() {
        super("timestamp-millis", Instant.class, "com.sksamuel.avro4k.serializer.InstantSerializer");
    }

    @Override public String getKotlinDefaultString(Schema schema, Object defaultValue) {
        return "java.time.Instant.ofEpochMilli(" + defaultValue + "L" + ")";
    }
}
