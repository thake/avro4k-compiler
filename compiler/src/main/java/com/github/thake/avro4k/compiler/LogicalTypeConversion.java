package com.github.thake.avro4k.compiler;

import org.apache.avro.Schema;

public interface LogicalTypeConversion {
    String getLogicalTypeName();

    String getKotlinType();

    String getSerializationAnnotation(Schema schema);

    String getKotlinDefaultString(Schema schema, Object defaultValue);
}
