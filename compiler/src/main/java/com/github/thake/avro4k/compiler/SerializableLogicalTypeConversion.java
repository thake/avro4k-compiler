package com.github.thake.avro4k.compiler;

import org.apache.avro.Schema;

public class SerializableLogicalTypeConversion implements LogicalTypeConversion {
    private String logicalTypeName;
    private String kotlinType;
    private String kotlinSerializer;

    public SerializableLogicalTypeConversion(String logicalTypeName, String kotlinType, String kotlinSerializer) {
        this.logicalTypeName = logicalTypeName;
        this.kotlinType = kotlinType;
        this.kotlinSerializer = kotlinSerializer;
    }

    public SerializableLogicalTypeConversion(String logicalTypeName, Class<?> kotlinType, String kotlinSerializer) {
        this(logicalTypeName, kotlinType.getCanonicalName(), kotlinSerializer);
    }

    @Override public String getLogicalTypeName() {
        return logicalTypeName;
    }

    @Override public String getKotlinType() {
        return kotlinType;
    }

    public String getKotlinSerializer() {
        return kotlinSerializer;
    }

    @Override public String getSerializationAnnotation(Schema schema) {
        return "@Serializable(with=" + kotlinSerializer + "::class)";
    }
}
