package com.github.thake.avro4k.compiler;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;

public class DecimalLogicalTypeConversion extends SerializableLogicalTypeConversion {

    public DecimalLogicalTypeConversion() {
        super("decimal", "java.math.BigDecimal", "com.sksamuel.avro4k.serializer.BigDecimalSerializer");
    }

    @Override public String getSerializationAnnotation(Schema schema) {
        LogicalTypes.Decimal logicalType = (LogicalTypes.Decimal) schema.getLogicalType();
        return "@ScalePrecision(" + logicalType.getScale() + "," + logicalType.getPrecision() + ") "
                + super.getSerializationAnnotation(schema);
    }
}
