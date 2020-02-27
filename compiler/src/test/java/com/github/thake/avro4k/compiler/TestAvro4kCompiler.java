/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.thake.avro4k.compiler;

import com.sksamuel.avro4k.serializer.InstantSerializer;
import kotlin.script.experimental.jvm.util.JvmClasspathUtilKt;
import kotlin.script.experimental.jvm.util.KotlinJars;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys;
import org.jetbrains.kotlin.cli.common.config.ContentRootsKt;
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer;
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler;
import org.jetbrains.kotlin.cli.jvm.config.JvmContentRootsKt;
import org.jetbrains.kotlin.codegen.state.GenerationState;
import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
import org.jetbrains.kotlin.config.CommonConfigurationKeys;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.config.JVMConfigurationKeys;
import org.jetbrains.kotlin.config.JvmTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class TestAvro4kCompiler {
    private static final int JVM_METHOD_ARG_LIMIT = 255;

    /*
     * Note: This is protected instead of private only so it's visible for testing.
     */
    protected static final int MAX_FIELD_PARAMETER_UNIT_COUNT = JVM_METHOD_ARG_LIMIT - 1;
    private static final Logger LOG = LoggerFactory.getLogger(TestAvro4kCompiler.class);
    @TempDir public File OUTPUT_DIR;

    private File outputFile;
    private File src = new File("src/test/resources/simple_record.avsc");

    static void assertCompilesWithKotlinCompiler(File dstDir, Collection<Avro4kCompiler.OutputFile> outputs)
            throws IOException {
        assertCompilesWithKotlinCompiler(dstDir, outputs, false);
    }

    /**
     * Uses the embedded kotlin compiler to actually compile the generated code.
     */
    static void assertCompilesWithKotlinCompiler(File dstDir, Collection<Avro4kCompiler.OutputFile> outputs,
            boolean ignoreWarnings) throws IOException {
        if (outputs.isEmpty()) {
            return; // Nothing to compile!
        }
        for (Avro4kCompiler.OutputFile o : outputs) {
            o.writeToDestination(null, dstDir);
        }
        assertCompilesWithKotlinCompiler(dstDir);
    }

    private static void assertCompilesWithKotlinCompiler(File dir) {
        CompilerConfiguration configuration = new CompilerConfiguration();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        configuration.put(CommonConfigurationKeys.MODULE_NAME, "test.module");
        configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                          new PrintingMessageCollector(ps, MessageRenderer.PLAIN_FULL_PATHS, true));
        configuration.put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_1_8);
        Set<File> classPath = new HashSet<>(
                JvmClasspathUtilKt.classpathFromClassloader(TestAvro4kCompiler.class.getClassLoader(), false));
        classPath.add(KotlinJars.INSTANCE.getStdlib());
        JvmContentRootsKt.addJvmClasspathRoots(configuration, new ArrayList<>(classPath));
        ContentRootsKt.addKotlinSourceRoot(configuration, dir.getAbsolutePath());
        KotlinCoreEnvironment env = KotlinCoreEnvironment.createForProduction(new Disposable() {
            @Override public void dispose() {

            }
        }, configuration, EnvironmentConfigFiles.JVM_CONFIG_FILES);
        GenerationState result = KotlinToJVMBytecodeCompiler.INSTANCE.analyzeAndGenerate(env);
        ps.flush();
        if (result == null) {
            LoggerFactory.getLogger(TestAvro4kCompiler.class)
                    .error("Kotlin compilation error. Details:\n" + baos.toString());
        }
        assertNotNull(result);
    }

    private static Schema createSampleRecordSchema(int numStringFields, int numDoubleFields) {
        SchemaBuilder.FieldAssembler<Schema> sb = SchemaBuilder.record("sample.record").fields();
        for (int i = 0; i < numStringFields; i++) {
            sb.name("sf_" + i).type().stringType().noDefault();
        }
        for (int i = 0; i < numDoubleFields; i++) {
            sb.name("df_" + i).type().doubleType().noDefault();
        }
        return sb.endRecord();
    }

    @BeforeEach public void setUp() {
        this.outputFile = new File(this.OUTPUT_DIR, "SimpleRecord.kt");
    }

    private Avro4kCompiler createCompiler() throws IOException {
        Schema.Parser parser = new Schema.Parser();
        Schema schema = parser.parse(this.src);
        Avro4kCompiler compiler = new Avro4kCompiler(schema);
        String velocityTemplateDir = "src/main/velocity/com/github/thake/avro4k/compiler/templates/";
        compiler.setTemplateDir(velocityTemplateDir);
        return compiler;
    }

    @Test public void testCanReadTemplateFilesOnTheFilesystem() throws IOException {
        Avro4kCompiler compiler = createCompiler();
        compiler.compileToDestination(this.src, OUTPUT_DIR);
        assertTrue(new File(OUTPUT_DIR, "SimpleRecord.kt").exists());
    }

    @Test public void testLogicalTypesWithNull() {

    }

    @Test public void testPublicFieldVisibility() throws IOException {
        Avro4kCompiler compiler = createCompiler();
        compiler.setFieldVisibility(Avro4kCompiler.FieldVisibility.PUBLIC);
        assertTrue(compiler.publicFields());
        assertFalse(compiler.privateFields());
        compiler.compileToDestination(this.src, this.OUTPUT_DIR);
        assertTrue(this.outputFile.exists());
        try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // No line, once trimmed, should start with a deprecated field declaration
                // nor a private field declaration. Since the nested builder uses private
                // fields, we cannot do the second check.
                line = line.trim();
                assertFalse(line.startsWith("@Deprecated public int value"),
                            "Line started with a deprecated field declaration: " + line);
            }
        }
    }

    @Test public void testMaxValidParameterCounts(TestInfo testInfo) throws Exception {
        Schema validSchema1 = createSampleRecordSchema(MAX_FIELD_PARAMETER_UNIT_COUNT, 0);
        assertCompilesWithKotlinCompiler(new File(OUTPUT_DIR, testInfo.getDisplayName() + "1"),
                                         new Avro4kCompiler(validSchema1).compile());

        Schema validSchema2 = createSampleRecordSchema(MAX_FIELD_PARAMETER_UNIT_COUNT - 2, 1);
        assertCompilesWithKotlinCompiler(new File(OUTPUT_DIR, testInfo.getDisplayName() + "2"),
                                         new Avro4kCompiler(validSchema1).compile());
    }

    @Test public void testInvalidParameterCounts(TestInfo testInfo) throws Exception {
        Schema invalidSchema1 = createSampleRecordSchema(MAX_FIELD_PARAMETER_UNIT_COUNT + 1, 0);
        Avro4kCompiler compiler = new Avro4kCompiler(invalidSchema1);
        assertCompilesWithKotlinCompiler(new File(OUTPUT_DIR, testInfo.getDisplayName() + "1"), compiler.compile());

        Schema invalidSchema2 = createSampleRecordSchema(MAX_FIELD_PARAMETER_UNIT_COUNT, 10);
        compiler = new Avro4kCompiler(invalidSchema2);
        assertCompilesWithKotlinCompiler(new File(OUTPUT_DIR, testInfo.getDisplayName() + "2"), compiler.compile());
    }

    @Test public void testMaxParameterCounts() throws Exception {
        Schema validSchema1 = createSampleRecordSchema(MAX_FIELD_PARAMETER_UNIT_COUNT, 0);
        assertTrue(new Avro4kCompiler(validSchema1).compile().size() > 0);

        Schema validSchema2 = createSampleRecordSchema(MAX_FIELD_PARAMETER_UNIT_COUNT - 2, 1);
        assertTrue(new Avro4kCompiler(validSchema2).compile().size() > 0);

        Schema validSchema3 = createSampleRecordSchema(MAX_FIELD_PARAMETER_UNIT_COUNT - 1, 1);
        assertTrue(new Avro4kCompiler(validSchema3).compile().size() > 0);

        Schema validSchema4 = createSampleRecordSchema(MAX_FIELD_PARAMETER_UNIT_COUNT + 1, 0);
        assertTrue(new Avro4kCompiler(validSchema4).compile().size() > 0);
    }

    @Test public void testPrivateFieldVisibility() throws IOException {
        Avro4kCompiler compiler = createCompiler();
        compiler.setFieldVisibility(Avro4kCompiler.FieldVisibility.PRIVATE);
        assertFalse(compiler.publicFields());
        assertTrue(compiler.privateFields());
        compiler.compileToDestination(this.src, this.OUTPUT_DIR);
        assertTrue(this.outputFile.exists());
        try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
            String line = null;
            while ((line = reader.readLine()) != null) {
                // No line, once trimmed, should start with a public field declaration
                // or with a deprecated public field declaration
                line = line.trim();
                assertFalse(line.startsWith("public int value"), "Line started with a public field declaration: " + line);
                assertFalse(line.startsWith("@Deprecated public int value"),
                            "Line started with a deprecated field declaration: " + line);
            }
        }
    }

    @Test public void testSettersCreated() throws IOException {
        Avro4kCompiler compiler = createCompiler();
        assertFalse(compiler.isCreateSetters());
        compiler.setCreateSetters(true);
        compiler.compileToDestination(this.src, this.OUTPUT_DIR);
        assertTrue(this.outputFile.exists());
        int foundSetters = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // We should find the setter in the main class
                line = line.trim();
                if (line.startsWith("var ")) {
                    foundSetters++;
                }
            }
        }
        assertEquals(2, foundSetters, "Found the wrong number of setters");
    }

    @Test public void renamedMultipleClasses() throws IOException {
        assertRenamedClasses(new File("src/test/resources/simple_record_with_subtype.avsc"),
                             Collections.singletonMap("my.namespace.(\\w+)Record", "com.github.thake.avro4k.test.$1"),
                             new RenamedClass("com.github.thake.avro4k.test", "Simple", "my.namespace", "SimpleRecord"),
                             new RenamedClass("com.github.thake.avro4k.test", "Inner", "my.namespace", "InnerRecord"));

    }

    private void assertRenamedClasses(File file, Map<String, String> rules, RenamedClass... renamedClasses)
            throws IOException {
        Schema.Parser parser = new Schema.Parser();
        Schema schema = parser.parse(file);
        Avro4kCompiler compiler = new Avro4kCompiler(schema);
        String velocityTemplateDir = "src/main/velocity/com/github/thake/avro4k/compiler/templates/";
        compiler.setTemplateDir(velocityTemplateDir);
        compiler.setRenamedClasses(rules);

        compiler.compileToDestination(file, this.OUTPUT_DIR);
        int foundSetters = 0;
        String readPackageName = null;
        String readClassName = null;
        String namespaceAnnotation = null;
        String nameAnnotation = null;
        for (RenamedClass renamedClass : renamedClasses) {
            //Read file from expected folder
            File outputFile = new File(this.OUTPUT_DIR,
                                       renamedClass.packageName.replace(".", "/") + "/" + renamedClass.className + ".kt");
            assertTrue(outputFile.exists(),
                       "Output file hasn't been generated or has been generated in the wrong package. Expected path: "
                               + outputFile.getAbsolutePath());
            try (BufferedReader reader = new BufferedReader(new FileReader(outputFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // We should find the setter in the main class
                    line = line.trim();
                    if (line.startsWith("package ")) {
                        readPackageName = line.substring("package".length()).trim();
                    } else if (line.startsWith("data class")) {
                        Matcher matcher = Pattern.compile("data class (\\w+) .*").matcher(line);
                        assertTrue(matcher.find());
                        readClassName = matcher.group(1);
                    } else if (line.startsWith("@AvroNamespace")) {
                        Matcher matcher = Pattern.compile("@AvroNamespace\\(\"([\\w.]*)\"\\).*").matcher(line);
                        assertTrue(matcher.find());
                        if (matcher.groupCount() == 1) {
                            namespaceAnnotation = matcher.group(1);
                        } else {
                            namespaceAnnotation = "";
                        }
                    } else if (line.startsWith("@AvroName")) {
                        Matcher matcher = Pattern.compile("@AvroName\\(\"([\\w.]+)\"\\).*").matcher(line);
                        assertTrue(matcher.find());
                        nameAnnotation = matcher.group(1);
                    }
                }
            }
            assertEquals(renamedClass.packageName, readPackageName);
            assertEquals(renamedClass.className, readClassName);
            assertEquals(renamedClass.namespace, namespaceAnnotation);
            assertEquals(renamedClass.avroName, nameAnnotation);
        }
        assertCompilesWithKotlinCompiler(this.OUTPUT_DIR);
    }

    @Test public void testRenamedClasses() throws IOException {
        assertRenamedClasses(this.src, Collections.singletonMap("SimpleRecord", "com.github.thake.SimpleOtherRecord"),
                             new RenamedClass("com.github.thake", "SimpleOtherRecord", "", "SimpleRecord"));
        assertRenamedClasses(new File("src/test/resources/simple_record_with_namespace.avsc"),
                             Collections.singletonMap("SimpleRecord", "SimpleOtherRecord"),
                             new RenamedClass("my.namespace", "SimpleOtherRecord", "my.namespace", "SimpleRecord"));
        assertRenamedClasses(new File("src/test/resources/simple_record_with_namespace.avsc"),
                             Collections.singletonMap("my.namespace.(\\w+)Record", "other.prefix.$1"),
                             new RenamedClass("other.prefix", "Simple", "my.namespace", "SimpleRecord"));

    }

    @Test public void testNullableLogicalTypesJavaUnboxDecimalTypesEnabled() throws Exception {
        Avro4kCompiler compiler = createCompiler();

        // Nullable types should return boxed types instead of primitive types
        Schema nullableDecimalSchema1 = Schema.createUnion(Schema.create(Schema.Type.NULL), LogicalTypes.timestampMillis()
                .addToSchema(Schema.create(Schema.Type.LONG)));
        assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableDecimalSchema1), "java.time.Instant?");
    }

    @Test public void testSettersNotCreatedWhenOptionTurnedOff() throws IOException {
        Avro4kCompiler compiler = createCompiler();
        compiler.setCreateSetters(false);
        assertFalse(compiler.isCreateSetters());
        compiler.compileToDestination(this.src, this.OUTPUT_DIR);
        assertTrue(this.outputFile.exists());
        try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // No setter should be found
                line = line.trim();
                assertFalse(line.startsWith("public void setValue("), "No line should include the setter: " + line);
            }
        }
    }

    @Test public void testSettingOutputCharacterEncoding() throws Exception {
        Avro4kCompiler compiler = createCompiler();
        // Generated file in default encoding
        compiler.compileToDestination(this.src, this.OUTPUT_DIR);
        byte[] fileInDefaultEncoding = new byte[(int) this.outputFile.length()];
        FileInputStream is = new FileInputStream(this.outputFile);
        is.read(fileInDefaultEncoding);
        is.close(); // close input stream otherwise delete might fail
        if (!this.outputFile.delete()) {
            throw new IllegalStateException("unable to delete " + this.outputFile); // delete otherwise compiler might not
            // overwrite because src timestamp hasn't
            // changed.
        }
        // Generate file in another encoding (make sure it has different number of bytes
        // per character)
        String differentEncoding = Charset.defaultCharset().equals(StandardCharsets.UTF_16) ? "UTF-32" : "UTF-16";
        compiler.setOutputCharacterEncoding(differentEncoding);
        compiler.compileToDestination(this.src, this.OUTPUT_DIR);
        byte[] fileInDifferentEncoding = new byte[(int) this.outputFile.length()];
        is = new FileInputStream(this.outputFile);
        is.read(fileInDifferentEncoding);
        is.close();
        // Compare as bytes
        assertNotEquals(fileInDifferentEncoding, fileInDefaultEncoding,
                        "Generated file should contain different bytes after setting non-default encoding");
        // Compare as strings
        assertEquals(new String(fileInDifferentEncoding, differentEncoding), new String(fileInDefaultEncoding),
                     "Generated files should contain the same characters in the proper encodings");
    }

    @Test public void testJavaTypeWithJsr310DateTimeTypes() throws Exception {
        Avro4kCompiler compiler = createCompiler();

        Schema dateSchema = LogicalTypes.date().addToSchema(Schema.create(Schema.Type.INT));
        Schema timeSchema = LogicalTypes.timeMillis().addToSchema(Schema.create(Schema.Type.INT));
        Schema timeMicrosSchema = LogicalTypes.timeMicros().addToSchema(Schema.create(Schema.Type.LONG));
        Schema timestampSchema = LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
        Schema timestampMicrosSchema = LogicalTypes.timestampMicros().addToSchema(Schema.create(Schema.Type.LONG));

        // Date/time types should always use upper level java classes
        assertEquals("Should use java.time.LocalDate for date type", "java.time.LocalDate", compiler.kotlinType(dateSchema));
        assertEquals("Should use java.time.LocalTime for time-millis type", "java.time.LocalTime",
                     compiler.kotlinType(timeSchema));
        assertEquals("Should use java.time.Instant for timestamp-millis type", "java.time.Instant",
                     compiler.kotlinType(timestampSchema));
    }

    @Test public void testJavaUnbox() throws Exception {
        Avro4kCompiler compiler = createCompiler();

        Schema intSchema = Schema.create(Schema.Type.INT);
        Schema longSchema = Schema.create(Schema.Type.LONG);
        Schema floatSchema = Schema.create(Schema.Type.FLOAT);
        Schema doubleSchema = Schema.create(Schema.Type.DOUBLE);
        Schema boolSchema = Schema.create(Schema.Type.BOOLEAN);
        assertEquals("Should use int for Type.INT", "Int", compiler.kotlinUnbox(intSchema));
        assertEquals("Should use long for Type.LONG", "Long", compiler.kotlinUnbox(longSchema));
        assertEquals("Should use float for Type.FLOAT", "Float", compiler.kotlinUnbox(floatSchema));
        assertEquals("Should use double for Type.DOUBLE", "Double", compiler.kotlinUnbox(doubleSchema));
        assertEquals("Should use boolean for Type.BOOLEAN", "Boolean", compiler.kotlinUnbox(boolSchema));

        Schema dateSchema = LogicalTypes.date().addToSchema(Schema.create(Schema.Type.INT));
        Schema timeSchema = LogicalTypes.timeMillis().addToSchema(Schema.create(Schema.Type.INT));
        Schema timeMicroSchema = LogicalTypes.timeMicros().addToSchema(Schema.create(Schema.Type.LONG));
        Schema timestampSchema = LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
        Schema timestampMicrosSchema = LogicalTypes.timestampMicros().addToSchema(Schema.create(Schema.Type.LONG));
        // Date/time types should always use upper level java classes, even though
        // their underlying representations are primitive types
        assertEquals("Should use java.time.LocalDate for date type", "java.time.LocalDate",
                     compiler.kotlinUnbox(dateSchema));
        assertEquals("Should use java.time.LocalTime for time-millis type", "java.time.LocalTime",
                     compiler.kotlinUnbox(timeSchema));
        assertEquals("Should use java.time.Instant for timestamp-millis type", "java.time.Instant",
                     compiler.kotlinUnbox(timestampSchema));
    }

    @Test public void testNullableLogicalTypesKotlinSerializer() throws IOException {
        Avro4kCompiler compiler = createCompiler();

        // Nullable types should return serializer for logical type
        Schema nullablInstant = Schema.createUnion(Schema.create(Schema.Type.NULL), LogicalTypes.timestampMillis()
                .addToSchema(Schema.create(Schema.Type.LONG)));
        assertEquals("Should return instant serializer", InstantSerializer.class.getCanonicalName(),
                     compiler.serializerClass(nullablInstant).orElse(null));
    }

    @Test public void testDefaultValueAsString() throws IOException {
        Avro4kCompiler compiler = createCompiler();
        Schema schema = SchemaBuilder.record("record")
                .fields()
                .name("nullDefault")
                .type(SchemaBuilder.unionOf().nullType().and().longType().endUnion())
                .withDefault(null)
                .name("intDefault")
                .type(Schema.create(Schema.Type.INT))
                .withDefault(1)
                .name("longDefault")
                .type(Schema.create(Schema.Type.LONG))
                .withDefault(1l)
                .endRecord();
        assertEquals("null", compiler.defaultValue(schema.getField("nullDefault")));
        assertEquals("1", compiler.defaultValue(schema.getField("intDefault")));
        assertEquals("1", compiler.defaultValue(schema.getField("longDefault")));
    }

    @Test public void testNullableTypesJavaUnbox() throws Exception {
        Avro4kCompiler compiler = createCompiler();

        // Nullable types should return boxed types instead of primitive types
        Schema nullableIntSchema1 = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.INT));
        Schema nullableIntSchema2 = Schema.createUnion(Schema.create(Schema.Type.INT), Schema.create(Schema.Type.NULL));
        assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableIntSchema1), "Int?");
        assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableIntSchema2), "Int?");

        Schema nullableLongSchema1 = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.LONG));
        Schema nullableLongSchema2 = Schema.createUnion(Schema.create(Schema.Type.LONG), Schema.create(Schema.Type.NULL));
        assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableLongSchema1), "Long?");
        assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableLongSchema2), "Long?");

        Schema nullableFloatSchema1 = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.FLOAT));
        Schema nullableFloatSchema2 = Schema.createUnion(Schema.create(Schema.Type.FLOAT), Schema.create(Schema.Type.NULL));
        assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableFloatSchema1), "Float?");
        assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableFloatSchema2), "Float?");

        Schema nullableDoubleSchema1 = Schema.createUnion(Schema.create(Schema.Type.NULL),
                                                          Schema.create(Schema.Type.DOUBLE));
        Schema nullableDoubleSchema2 = Schema.createUnion(Schema.create(Schema.Type.DOUBLE),
                                                          Schema.create(Schema.Type.NULL));
        assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableDoubleSchema1), "Double?");
        assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableDoubleSchema2), "Double?");

        Schema nullableBooleanSchema1 = Schema.createUnion(Schema.create(Schema.Type.NULL),
                                                           Schema.create(Schema.Type.BOOLEAN));
        Schema nullableBooleanSchema2 = Schema.createUnion(Schema.create(Schema.Type.BOOLEAN),
                                                           Schema.create(Schema.Type.NULL));
        assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableBooleanSchema1), "Boolean?");
        assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableBooleanSchema2), "Boolean?");
    }

    @Test public void testUnionAndFixedFields(TestInfo testInfo) throws Exception {
        Schema unionTypesWithMultipleFields = new Schema.Parser().parse(
                new File("src/test/resources/union_and_fixed_fields.avsc"));
        assertCompilesWithKotlinCompiler(new File(this.outputFile, testInfo.getDisplayName()),
                                         new Avro4kCompiler(unionTypesWithMultipleFields).compile());
    }

    @Test public void testLogicalTypesWithMultipleFieldsJsr310DateTime(TestInfo testInfo) throws Exception {
        Schema logicalTypesWithMultipleFields = new Schema.Parser().parse(
                new File("src/test/resources/logical_types_with_multiple_fields.avsc"));
        assertCompilesWithKotlinCompiler(new File(this.outputFile, testInfo.getDisplayName()),
                                         new Avro4kCompiler(logicalTypesWithMultipleFields).compile());
    }

    @Test public void testPojoWithOptionalTurnedOffByDefault() throws IOException {
        Avro4kCompiler compiler = createCompiler();
        compiler.compileToDestination(this.src, OUTPUT_DIR);
        assertTrue(this.outputFile.exists());
        try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                assertFalse(line.contains("Optional"));
            }
        }
    }

    @Test public void testAdditionalToolsAreInjectedIntoTemplate() throws Exception {
        Avro4kCompiler compiler = createCompiler();
        List<Object> customTools = new ArrayList<>();
        customTools.add("");
        compiler.setAdditionalVelocityTools(customTools);
        compiler.setTemplateDir("src/test/resources/templates_with_custom_tools/");
        compiler.compileToDestination(this.src, this.OUTPUT_DIR);
        assertTrue(this.outputFile.exists());
        int itWorksFound = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.contains("It works!")) {
                    itWorksFound++;
                }
            }
        }
        assertEquals(1, itWorksFound);
    }

    private static class RenamedClass {
        String packageName;
        String className;
        String namespace;
        String avroName;

        public RenamedClass(String packageName, String className, String namespace, String avroName) {
            this.packageName = packageName;
            this.className = className;
            this.namespace = namespace;
            this.avroName = avroName;
        }
    }
}
