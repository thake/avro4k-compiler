package com.github.thake.avro4k.compiler;

import org.apache.avro.Schema;

import java.time.LocalDate;

public class DateLogicalTypeConversion extends SerializableLogicalTypeConversion {
    public DateLogicalTypeConversion() {
        super("date", LocalDate.class, "com.github.avrokotlin.avro4k.serializer.LocalDateSerializer");
    }

    @Override public String getKotlinDefaultString(Schema schema, Object defaultValue) {
        return "java.time.LocalDate.ofEpochDay(" + defaultValue + "L)";
    }
}
