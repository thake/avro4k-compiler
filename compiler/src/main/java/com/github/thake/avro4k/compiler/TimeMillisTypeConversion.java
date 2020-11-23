package com.github.thake.avro4k.compiler;

import org.apache.avro.Schema;

import java.time.LocalTime;

public class TimeMillisTypeConversion extends SerializableLogicalTypeConversion {
    public TimeMillisTypeConversion() {
        super("time-millis", LocalTime.class, "com.github.avrokotlin.avro4k.serializer.LocalTimeSerializer");
    }

    @Override public String getKotlinDefaultString(Schema schema, Object defaultValue) {
        return "java.time.LocalTime.ofNanoOfDay(" + defaultValue + "000000L * )";
    }
}
