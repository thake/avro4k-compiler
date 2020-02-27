package com.github.thake.avro4k.compiler;

import com.sksamuel.avro4k.Avro;
import kotlinx.serialization.SerializationStrategy;
import org.apache.avro.Schema;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.script.ScriptException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRoundTrip {

    public static Stream<Path> roundtripFiles() throws IOException {
        return Files.walk(Paths.get("src/test/resources/roundtrip")).filter(((Predicate<Path>) Files::isDirectory).negate());
    }

    @ParameterizedTest @MethodSource("roundtripFiles") public void testScriptExec(Path file, @TempDir Path outputDir)
            throws ScriptException, IOException, NoSuchFieldException, ClassNotFoundException, IllegalAccessException,
            NoSuchMethodException, InvocationTargetException {
        if (file.endsWith("record_with_enum.avsc")) {
            //Disabling test for record_with_enum until https://github.com/sksamuel/avro4k/issues/29 is fixed.
            assertTrue(true);
            return;
        }
        Schema schema = new Schema.Parser().parse(file.toFile());
        Class<?> cl = Class.forName(schema.getFullName());
        Field field = cl.getField("Companion");
        Object companion = field.get(cl);
        Method method = companion.getClass().getMethod("serializer");
        SerializationStrategy serializer = (SerializationStrategy<?>) method.invoke(companion);
        Schema avroCompilerSchema = Avro.Companion.getDefault().schema(serializer);
        assertEquals(schema.toString(true), avroCompilerSchema.toString(true), "Generated avro4k schema does not match");
    }
}
