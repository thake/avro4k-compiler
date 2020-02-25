package com.github.thake.avro4k.maven;

import java.util.Objects;

public class Conversion {
    private String logicalTypeName;
    private String kotlinType;
    private String kotlinSerializer;

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Conversion that = (Conversion) o;
        return Objects.equals(logicalTypeName, that.logicalTypeName) && Objects.equals(kotlinType, that.kotlinType)
                && Objects.equals(kotlinSerializer, that.kotlinSerializer);
    }

    @Override public int hashCode() {
        return Objects.hash(logicalTypeName, kotlinType, kotlinSerializer);
    }

    public String getLogicalTypeName() {
        return logicalTypeName;
    }

    public void setLogicalTypeName(String logicalTypeName) {
        this.logicalTypeName = logicalTypeName;
    }

    public String getKotlinType() {
        return kotlinType;
    }

    public void setKotlinType(String kotlinType) {
        this.kotlinType = kotlinType;
    }

    public String getKotlinSerializer() {
        return kotlinSerializer;
    }

    public void setKotlinSerializer(String kotlinSerializer) {
        this.kotlinSerializer = kotlinSerializer;
    }
}
