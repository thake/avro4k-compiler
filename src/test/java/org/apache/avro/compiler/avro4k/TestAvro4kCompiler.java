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
package org.apache.avro.compiler.avro4k;

import static org.apache.avro.compiler.avro4k.Avro4kCompiler.DateTimeLogicalTypeImplementation.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.equalTo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData.StringType;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

@RunWith(JUnit4.class)
public class TestAvro4kCompiler {
  private static final Logger LOG = LoggerFactory.getLogger(TestAvro4kCompiler.class);

  @Rule
  public TemporaryFolder OUTPUT_DIR = new TemporaryFolder();

  @Rule
  public TestName name = new TestName();

  private File outputFile;

  @Before
  public void setUp() {
    this.outputFile = new File(this.OUTPUT_DIR.getRoot(), "SimpleRecord.java");
  }

  private File src = new File("src/test/resources/simple_record.avsc");

  static void assertCompilesWithJavaCompiler(File dstDir, Collection<Avro4kCompiler.OutputFile> outputs)
      throws IOException {
    assertCompilesWithJavaCompiler(dstDir, outputs, false);
  }

  /** Uses the system's java compiler to actually compile the generated code. */
  static void assertCompilesWithJavaCompiler(File dstDir, Collection<Avro4kCompiler.OutputFile> outputs,
      boolean ignoreWarnings) throws IOException {
    if (outputs.isEmpty()) {
      return; // Nothing to compile!
    }

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

    List<File> javaFiles = new ArrayList<>();
    for (Avro4kCompiler.OutputFile o : outputs) {
      javaFiles.add(o.writeToDestination(null, dstDir));
    }

    final List<Diagnostic<?>> warnings = new ArrayList<>();
    DiagnosticListener<JavaFileObject> diagnosticListener = diagnostic -> {
      switch (diagnostic.getKind()) {
      case ERROR:
        // Do not add these to warnings because they will fail the compile, anyway.
        LOG.error("{}", diagnostic);
        break;
      case WARNING:
      case MANDATORY_WARNING:
        LOG.warn("{}", diagnostic);
        warnings.add(diagnostic);
        break;
      case NOTE:
      case OTHER:
        LOG.debug("{}", diagnostic);
        break;
      }
    };
    JavaCompiler.CompilationTask cTask = compiler.getTask(null, fileManager, diagnosticListener,
        Collections.singletonList("-Xlint:all"), null, fileManager.getJavaFileObjects(javaFiles.toArray(new File[0])));
    boolean compilesWithoutError = cTask.call();
    assertTrue(compilesWithoutError);
    if (!ignoreWarnings) {
      assertEquals("Warnings produced when compiling generated code with -Xlint:all", 0, warnings.size());
    }
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

  private Avro4kCompiler createCompiler() throws IOException {
    return createCompiler(JSR310);
  }

  private Avro4kCompiler createCompiler(
      Avro4kCompiler.DateTimeLogicalTypeImplementation dateTimeLogicalTypeImplementation) throws IOException {
    Schema.Parser parser = new Schema.Parser();
    Schema schema = parser.parse(this.src);
    Avro4kCompiler compiler = new Avro4kCompiler(schema, dateTimeLogicalTypeImplementation);
    String velocityTemplateDir = "src/main/velocity/org/apache/avro/compiler/specific/templates/java/classic/";
    compiler.setTemplateDir(velocityTemplateDir);
    compiler.setStringType(StringType.CharSequence);
    return compiler;
  }

  @Test
  public void testCanReadTemplateFilesOnTheFilesystem() throws IOException {
    Avro4kCompiler compiler = createCompiler();
    compiler.compileToDestination(this.src, OUTPUT_DIR.getRoot());
    assertTrue(new File(OUTPUT_DIR.getRoot(), "SimpleRecord.java").exists());
  }

  @Test
  public void testPublicFieldVisibility() throws IOException {
    Avro4kCompiler compiler = createCompiler();
    compiler.setFieldVisibility(Avro4kCompiler.FieldVisibility.PUBLIC);
    assertFalse(compiler.deprecatedFields());
    assertTrue(compiler.publicFields());
    assertFalse(compiler.privateFields());
    compiler.compileToDestination(this.src, this.OUTPUT_DIR.getRoot());
    assertTrue(this.outputFile.exists());
    try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        // No line, once trimmed, should start with a deprecated field declaration
        // nor a private field declaration. Since the nested builder uses private
        // fields, we cannot do the second check.
        line = line.trim();
        assertFalse("Line started with a deprecated field declaration: " + line,
            line.startsWith("@Deprecated public int value"));
      }
    }
  }

  @Test
  public void testCreateAllArgsConstructor() throws Exception {
    Avro4kCompiler compiler = createCompiler();
    compiler.compileToDestination(this.src, this.OUTPUT_DIR.getRoot());
    assertTrue(this.outputFile.exists());
    boolean foundAllArgsConstructor = false;
    try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
      String line;
      while (!foundAllArgsConstructor && (line = reader.readLine()) != null) {
        foundAllArgsConstructor = line.contains("All-args constructor");
      }
    }
    assertTrue(foundAllArgsConstructor);
  }

  @Test
  public void testMaxValidParameterCounts() throws Exception {
    Schema validSchema1 = createSampleRecordSchema(Avro4kCompiler.MAX_FIELD_PARAMETER_UNIT_COUNT, 0);
    assertCompilesWithJavaCompiler(new File(OUTPUT_DIR.getRoot(), name.getMethodName() + "1"),
        new Avro4kCompiler(validSchema1).compile());

    Schema validSchema2 = createSampleRecordSchema(Avro4kCompiler.MAX_FIELD_PARAMETER_UNIT_COUNT - 2, 1);
    assertCompilesWithJavaCompiler(new File(OUTPUT_DIR.getRoot(), name.getMethodName() + "2"),
        new Avro4kCompiler(validSchema1).compile());
  }

  @Test
  public void testInvalidParameterCounts() throws Exception {
    Schema invalidSchema1 = createSampleRecordSchema(Avro4kCompiler.MAX_FIELD_PARAMETER_UNIT_COUNT + 1, 0);
    Avro4kCompiler compiler = new Avro4kCompiler(invalidSchema1);
    assertCompilesWithJavaCompiler(new File(OUTPUT_DIR.getRoot(), name.getMethodName() + "1"), compiler.compile());

    Schema invalidSchema2 = createSampleRecordSchema(Avro4kCompiler.MAX_FIELD_PARAMETER_UNIT_COUNT, 10);
    compiler = new Avro4kCompiler(invalidSchema2);
    assertCompilesWithJavaCompiler(new File(OUTPUT_DIR.getRoot(), name.getMethodName() + "2"), compiler.compile());
  }

  @Test
  public void testMaxParameterCounts() throws Exception {
    Schema validSchema1 = createSampleRecordSchema(Avro4kCompiler.MAX_FIELD_PARAMETER_UNIT_COUNT, 0);
    assertTrue(new Avro4kCompiler(validSchema1).compile().size() > 0);

    Schema validSchema2 = createSampleRecordSchema(Avro4kCompiler.MAX_FIELD_PARAMETER_UNIT_COUNT - 2, 1);
    assertTrue(new Avro4kCompiler(validSchema2).compile().size() > 0);

    Schema validSchema3 = createSampleRecordSchema(Avro4kCompiler.MAX_FIELD_PARAMETER_UNIT_COUNT - 1, 1);
    assertTrue(new Avro4kCompiler(validSchema3).compile().size() > 0);

    Schema validSchema4 = createSampleRecordSchema(Avro4kCompiler.MAX_FIELD_PARAMETER_UNIT_COUNT + 1, 0);
    assertTrue(new Avro4kCompiler(validSchema4).compile().size() > 0);
  }

  @Test(expected = RuntimeException.class)
  public void testCalcAllArgConstructorParameterUnitsFailure() {
    Schema nonRecordSchema = SchemaBuilder.array().items().booleanType();
    new Avro4kCompiler().calcAllArgConstructorParameterUnits(nonRecordSchema);
  }

  @Test
  public void testPublicDeprecatedFieldVisibility() throws IOException {
    Avro4kCompiler compiler = createCompiler();
    compiler.setFieldVisibility(Avro4kCompiler.FieldVisibility.PUBLIC_DEPRECATED);
    assertTrue(compiler.deprecatedFields());
    assertTrue(compiler.publicFields());
    assertFalse(compiler.privateFields());
    compiler.compileToDestination(this.src, this.OUTPUT_DIR.getRoot());
    assertTrue(this.outputFile.exists());
    try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        // No line, once trimmed, should start with a public field declaration
        line = line.trim();
        assertFalse("Line started with a public field declaration: " + line, line.startsWith("public int value"));
      }
    }
  }

  @Test
  public void testPrivateFieldVisibility() throws IOException {
    Avro4kCompiler compiler = createCompiler();
    compiler.setFieldVisibility(Avro4kCompiler.FieldVisibility.PRIVATE);
    assertFalse(compiler.deprecatedFields());
    assertFalse(compiler.publicFields());
    assertTrue(compiler.privateFields());
    compiler.compileToDestination(this.src, this.OUTPUT_DIR.getRoot());
    assertTrue(this.outputFile.exists());
    try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
      String line = null;
      while ((line = reader.readLine()) != null) {
        // No line, once trimmed, should start with a public field declaration
        // or with a deprecated public field declaration
        line = line.trim();
        assertFalse("Line started with a public field declaration: " + line, line.startsWith("public int value"));
        assertFalse("Line started with a deprecated field declaration: " + line,
            line.startsWith("@Deprecated public int value"));
      }
    }
  }

  @Test
  public void testSettersCreatedByDefault() throws IOException {
    Avro4kCompiler compiler = createCompiler();
    assertTrue(compiler.isCreateSetters());
    compiler.compileToDestination(this.src, this.OUTPUT_DIR.getRoot());
    assertTrue(this.outputFile.exists());
    int foundSetters = 0;
    try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        // We should find the setter in the main class
        line = line.trim();
        if (line.startsWith("public void setValue(")) {
          foundSetters++;
        }
      }
    }
    assertEquals("Found the wrong number of setters", 1, foundSetters);
  }

  @Test
  public void testSettersNotCreatedWhenOptionTurnedOff() throws IOException {
    Avro4kCompiler compiler = createCompiler();
    compiler.setCreateSetters(false);
    assertFalse(compiler.isCreateSetters());
    compiler.compileToDestination(this.src, this.OUTPUT_DIR.getRoot());
    assertTrue(this.outputFile.exists());
    try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        // No setter should be found
        line = line.trim();
        assertFalse("No line should include the setter: " + line, line.startsWith("public void setValue("));
      }
    }
  }

  @Test
  public void testSettingOutputCharacterEncoding() throws Exception {
    Avro4kCompiler compiler = createCompiler();
    // Generated file in default encoding
    compiler.compileToDestination(this.src, this.OUTPUT_DIR.getRoot());
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
    String differentEncoding = Charset.defaultCharset().equals(Charset.forName("UTF-16")) ? "UTF-32" : "UTF-16";
    compiler.setOutputCharacterEncoding(differentEncoding);
    compiler.compileToDestination(this.src, this.OUTPUT_DIR.getRoot());
    byte[] fileInDifferentEncoding = new byte[(int) this.outputFile.length()];
    is = new FileInputStream(this.outputFile);
    is.read(fileInDifferentEncoding);
    is.close();
    // Compare as bytes
    assertThat("Generated file should contain different bytes after setting non-default encoding",
        fileInDefaultEncoding, not(equalTo(fileInDifferentEncoding)));
    // Compare as strings
    assertThat("Generated files should contain the same characters in the proper encodings",
        new String(fileInDefaultEncoding), equalTo(new String(fileInDifferentEncoding, differentEncoding)));
  }


  @Test
  public void testJavaTypeWithJsr310DateTimeTypes() throws Exception {
    Avro4kCompiler compiler = createCompiler(JSR310);

    Schema dateSchema = LogicalTypes.date().addToSchema(Schema.create(Schema.Type.INT));
    Schema timeSchema = LogicalTypes.timeMillis().addToSchema(Schema.create(Schema.Type.INT));
    Schema timeMicrosSchema = LogicalTypes.timeMicros().addToSchema(Schema.create(Schema.Type.LONG));
    Schema timestampSchema = LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
    Schema timestampMicrosSchema = LogicalTypes.timestampMicros().addToSchema(Schema.create(Schema.Type.LONG));

    // Date/time types should always use upper level java classes
    Assert.assertEquals("Should use java.time.LocalDate for date type", "java.time.LocalDate",
        compiler.kotlinType(dateSchema));
    Assert.assertEquals("Should use java.time.LocalTime for time-millis type", "java.time.LocalTime",
        compiler.kotlinType(timeSchema));
    Assert.assertEquals("Should use java.time.Instant for timestamp-millis type", "java.time.Instant",
        compiler.kotlinType(timestampSchema));
    Assert.assertEquals("Should use java.time.LocalTime for time-micros type", "java.time.LocalTime",
        compiler.kotlinType(timeMicrosSchema));
    Assert.assertEquals("Should use java.time.Instant for timestamp-micros type", "java.time.Instant",
        compiler.kotlinType(timestampMicrosSchema));
  }

  @Test
  public void testJavaUnbox() throws Exception {
    Avro4kCompiler compiler = createCompiler(DEFAULT);
    compiler.setEnableDecimalLogicalType(false);

    Schema intSchema = Schema.create(Schema.Type.INT);
    Schema longSchema = Schema.create(Schema.Type.LONG);
    Schema floatSchema = Schema.create(Schema.Type.FLOAT);
    Schema doubleSchema = Schema.create(Schema.Type.DOUBLE);
    Schema boolSchema = Schema.create(Schema.Type.BOOLEAN);
    Assert.assertEquals("Should use int for Type.INT", "int", compiler.kotlinUnbox(intSchema));
    Assert.assertEquals("Should use long for Type.LONG", "long", compiler.kotlinUnbox(longSchema));
    Assert.assertEquals("Should use float for Type.FLOAT", "float", compiler.kotlinUnbox(floatSchema));
    Assert.assertEquals("Should use double for Type.DOUBLE", "double", compiler.kotlinUnbox(doubleSchema));
    Assert.assertEquals("Should use boolean for Type.BOOLEAN", "boolean", compiler.kotlinUnbox(boolSchema));

    Schema dateSchema = LogicalTypes.date().addToSchema(Schema.create(Schema.Type.INT));
    Schema timeSchema = LogicalTypes.timeMillis().addToSchema(Schema.create(Schema.Type.INT));
    Schema timeMicroSchema = LogicalTypes.timeMicros().addToSchema(Schema.create(Schema.Type.LONG));
    Schema timestampSchema = LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
    Schema timestampMicrosSchema = LogicalTypes.timestampMicros().addToSchema(Schema.create(Schema.Type.LONG));
    // Date/time types should always use upper level java classes, even though
    // their underlying representations are primitive types
    Assert.assertEquals("Should use java.time.LocalDate for date type", "java.time.LocalDate",
                        compiler.kotlinUnbox(dateSchema));
    Assert.assertEquals("Should use java.time.LocalTime for time-millis type", "java.time.LocalTime",
                        compiler.kotlinUnbox(timeSchema));
    Assert.assertEquals("Should use java.time.Instant for timestamp-millis type", "java.time.Instant",
                        compiler.kotlinUnbox(timestampSchema));
  }



  @Test
  public void testNullableLogicalTypesJavaUnboxDecimalTypesEnabled() throws Exception {
    Avro4kCompiler compiler = createCompiler();
    compiler.setEnableDecimalLogicalType(true);

    // Nullable types should return boxed types instead of primitive types
    Schema nullableDecimalSchema1 = Schema.createUnion(Schema.create(Schema.Type.NULL),
        LogicalTypes.decimal(9, 2).addToSchema(Schema.create(Schema.Type.BYTES)));
    Schema nullableDecimalSchema2 = Schema.createUnion(
        LogicalTypes.decimal(9, 2).addToSchema(Schema.create(Schema.Type.BYTES)), Schema.create(Schema.Type.NULL));
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableDecimalSchema1), "java.math.BigDecimal?");
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableDecimalSchema2), "java.math.BigDecimal?");
  }

  @Test
  public void testNullableLogicalTypesJavaUnboxDecimalTypesDisabled() throws Exception {
    Avro4kCompiler compiler = createCompiler();
    compiler.setEnableDecimalLogicalType(false);

    // Since logical decimal types are disabled, a ByteBuffer is expected.
    Schema nullableDecimalSchema1 = Schema.createUnion(Schema.create(Schema.Type.NULL),
        LogicalTypes.decimal(9, 2).addToSchema(Schema.create(Schema.Type.BYTES)));
    Schema nullableDecimalSchema2 = Schema.createUnion(
        LogicalTypes.decimal(9, 2).addToSchema(Schema.create(Schema.Type.BYTES)), Schema.create(Schema.Type.NULL));
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableDecimalSchema1), "ByteArray?");
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableDecimalSchema2), "ByteArray?");
  }

  @Test
  public void testNullableTypesJavaUnbox() throws Exception {
    Avro4kCompiler compiler = createCompiler();
    compiler.setEnableDecimalLogicalType(false);

    // Nullable types should return boxed types instead of primitive types
    Schema nullableIntSchema1 = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.INT));
    Schema nullableIntSchema2 = Schema.createUnion(Schema.create(Schema.Type.INT), Schema.create(Schema.Type.NULL));
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableIntSchema1), "Int?");
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableIntSchema2), "Int?");

    Schema nullableLongSchema1 = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.LONG));
    Schema nullableLongSchema2 = Schema.createUnion(Schema.create(Schema.Type.LONG), Schema.create(Schema.Type.NULL));
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableLongSchema1), "Long?");
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableLongSchema2), "Long?");

    Schema nullableFloatSchema1 = Schema.createUnion(Schema.create(Schema.Type.NULL), Schema.create(Schema.Type.FLOAT));
    Schema nullableFloatSchema2 = Schema.createUnion(Schema.create(Schema.Type.FLOAT), Schema.create(Schema.Type.NULL));
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableFloatSchema1), "Float?");
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableFloatSchema2), "Float?");

    Schema nullableDoubleSchema1 = Schema.createUnion(Schema.create(Schema.Type.NULL),
        Schema.create(Schema.Type.DOUBLE));
    Schema nullableDoubleSchema2 = Schema.createUnion(Schema.create(Schema.Type.DOUBLE),
        Schema.create(Schema.Type.NULL));
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableDoubleSchema1), "Double?");
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableDoubleSchema2), "Double?");

    Schema nullableBooleanSchema1 = Schema.createUnion(Schema.create(Schema.Type.NULL),
        Schema.create(Schema.Type.BOOLEAN));
    Schema nullableBooleanSchema2 = Schema.createUnion(Schema.create(Schema.Type.BOOLEAN),
        Schema.create(Schema.Type.NULL));
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableBooleanSchema1), "Boolean?");
    Assert.assertEquals("Should return boxed type", compiler.kotlinUnbox(nullableBooleanSchema2), "Boolean?");
  }


  @Test
  public void testUnionAndFixedFields() throws Exception {
    Schema unionTypesWithMultipleFields = new Schema.Parser()
        .parse(new File("src/test/resources/union_and_fixed_fields.avsc"));
    assertCompilesWithJavaCompiler(new File(this.outputFile, name.getMethodName()),
        new Avro4kCompiler(unionTypesWithMultipleFields).compile());
  }

  @Test
  public void testLogicalTypesWithMultipleFieldsJsr310DateTime() throws Exception {
    Schema logicalTypesWithMultipleFields = new Schema.Parser()
        .parse(new File("src/test/resources/logical_types_with_multiple_fields.avsc"));
    assertCompilesWithJavaCompiler(new File(this.outputFile, name.getMethodName()),
        new Avro4kCompiler(logicalTypesWithMultipleFields, JSR310).compile());
  }

  @Test
  public void testPojoWithOptionalTurnedOffByDefault() throws IOException {
    Avro4kCompiler compiler = createCompiler();
    compiler.compileToDestination(this.src, OUTPUT_DIR.getRoot());
    assertTrue(this.outputFile.exists());
    try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        assertFalse(line.contains("Optional"));
      }
    }
  }

  @Test
  public void testPojoWithOptionalCreatedWhenOptionTurnedOn() throws IOException {
    Avro4kCompiler compiler = createCompiler();
    compiler.setGettersReturnOptional(true);
    // compiler.setCreateOptionalGetters(true);
    compiler.compileToDestination(this.src, OUTPUT_DIR.getRoot());
    assertTrue(this.outputFile.exists());
    int optionalFound = 0;
    try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.contains("Optional")) {
          optionalFound++;
        }
      }
    }
    assertEquals(9, optionalFound);
  }

  @Test
  public void testPojoWithOptionalCreatedWhenOptionalForEverythingTurnedOn() throws IOException {
    Avro4kCompiler compiler = createCompiler();
    // compiler.setGettersReturnOptional(true);
    compiler.setCreateOptionalGetters(true);
    compiler.compileToDestination(this.src, OUTPUT_DIR.getRoot());
    assertTrue(this.outputFile.exists());
    int optionalFound = 0;
    try (BufferedReader reader = new BufferedReader(new FileReader(this.outputFile))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.contains("Optional")) {
          optionalFound++;
        }
      }
    }
    assertEquals(17, optionalFound);
  }

  @Test
  public void testAdditionalToolsAreInjectedIntoTemplate() throws Exception {
    Avro4kCompiler compiler = createCompiler();
    List<Object> customTools = new ArrayList<>();
    customTools.add(new String());
    compiler.setAdditionalVelocityTools(customTools);
    compiler.setTemplateDir("src/test/resources/templates_with_custom_tools/");
    compiler.compileToDestination(this.src, this.OUTPUT_DIR.getRoot());
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
}
