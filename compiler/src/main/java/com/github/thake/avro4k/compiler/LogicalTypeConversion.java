package com.github.thake.avro4k.compiler;

public class LogicalTypeConversion {
    private String logicalTypeName;
    private String kotlinType;
    private String kotlinSerializer;

    public LogicalTypeConversion(String logicalTypeName, String kotlinType, String kotlinSerializer) {
        this.logicalTypeName = logicalTypeName;
        this.kotlinType = kotlinType;
        this.kotlinSerializer = kotlinSerializer;
    }

    public LogicalTypeConversion(String logicalTypeName, Class<?> kotlinType, String kotlinSerializer) {
        this(logicalTypeName, kotlinType.getCanonicalName(), kotlinSerializer);
    }

    public String getLogicalTypeName() {
        return logicalTypeName;
    }

    public String getKotlinType() {
        return kotlinType;
    }

    public String getKotlinSerializer() {
        return kotlinSerializer;
    }
}
