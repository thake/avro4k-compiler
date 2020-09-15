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

package com.github.thake.avro4k.maven;

import com.github.thake.avro4k.compiler.Avro4kCompiler;
import com.github.thake.avro4k.compiler.LogicalTypeConversion;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Base for Avro Compiler Mojos.
 */
public abstract class AbstractAvroMojo extends AbstractMojo {
  /**
   * A list of files or directories that should be compiled first thus making them
   * importable by subsequently compiled schemas. Note that imported files should
   * not reference each other.
   */
  @Parameter protected String[] imports;
  /**
   * A set of Ant-like exclusion patterns used to prevent certain files from being
   * processed. By default, this set is empty such that no files are excluded.
   */
  @Parameter protected String[] excludes = new String[0];
  /**
   * A set of Ant-like exclusion patterns used to prevent certain files from being
   * processed. By default, this set is empty such that no files are excluded.
   */
  @Parameter protected String[] testExcludes = new String[0];
  /**
   * The directory (within the java classpath) that contains the velocity
   * templates to use for code generation. The default value points to the
   * templates included with the avro4k-maven-plugin.
   */
  @Parameter(defaultValue = "/com/github/thake/avro4k/compiler/templates/") protected String templateDirectory = "/com/github/thake/avro4k/compiler/templates/";
  /**
   * The qualified names of classes which the plugin will look up, instantiate
   * (through an empty constructor that must exist) and set up to be injected into
   * Velocity templates by Avro4k compiler.
   */
  @Parameter protected String[] velocityToolsClassesNames = new String[0];
  @Parameter protected RenamedClass[] renamedClasses = new RenamedClass[0];
  /**
   * Determines whether or not to create setters for the fields of the record. The
   * default is to create setters.
   */
  @Parameter(defaultValue = "false") protected boolean createSetters = false;
  /**
   * The current Maven project.
   */
  @Parameter(defaultValue = "${project}", required = true, readonly = true) protected MavenProject project;
  /**
   * Classes implementing {@link com.github.thake.avro4k.compiler.LogicalTypeConversion} for a custom treatment
   * of a logical type.
   */
  @Parameter protected String[] logicalTypeConversions = new String[0];
  /**
   * The source directory of avro files. This directory is added to the classpath
   * at schema compiling time. All files can therefore be referenced as classpath
   * resources following the directory structure under the source directory.
   */
  @Parameter(defaultValue = "${basedir}/src/main/avro")
  private File sourceDirectory;
  @Parameter(defaultValue = "${project.build.directory}/generated-sources/avro")
  private File outputDirectory;
  @Parameter(defaultValue = "${basedir}/src/test/avro")
  private File testSourceDirectory;
  /**
   * The field visibility indicator for the fields of the generated class, as
   * string values of {@link com.github.thake.avro4k.compiler.Avro4kCompiler.FieldVisibility}. The text is case
   * insensitive.
   */
  @Parameter(defaultValue = "PUBLIC")
  private final Avro4kCompiler.FieldVisibility fieldVisibility = Avro4kCompiler.FieldVisibility.PUBLIC;
  @Parameter(defaultValue = "${project.build.directory}/generated-test-sources/avro")
  private File testOutputDirectory;

  @Override
  public void execute() throws MojoExecutionException {
    boolean hasSourceDir = null != sourceDirectory && sourceDirectory.isDirectory();
    boolean hasImports = null != imports;
    boolean hasTestDir = null != testSourceDirectory && testSourceDirectory.isDirectory();
    if (!hasSourceDir && !hasTestDir) {
      throw new MojoExecutionException(
              "neither sourceDirectory: " + sourceDirectory + " or testSourceDirectory: " + testSourceDirectory
                      + " are directories");
    }

    if (hasImports) {
      for (String importedFile : imports) {
        File file = new File(importedFile);
        if (file.isDirectory()) {
          String[] includedFiles = getIncludedFiles(file.getAbsolutePath(), excludes, getIncludes());
          getLog().info("Importing Directory: " + file.getAbsolutePath());
          getLog().debug("Importing Directory Files: " + Arrays.toString(includedFiles));
          compileFiles(includedFiles, file, outputDirectory);
        } else if (file.isFile()) {
          getLog().info("Importing File: " + file.getAbsolutePath());
          compileFiles(new String[] { file.getName() }, file.getParentFile(), outputDirectory);
        }
      }
    }

    if (hasSourceDir) {
      String[] includedFiles = getIncludedFiles(sourceDirectory.getAbsolutePath(), excludes, getIncludes());
      compileFiles(includedFiles, sourceDirectory, outputDirectory);
    }

    if (hasImports || hasSourceDir) {
      project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
      getLog().info("Added " + outputDirectory.getAbsolutePath() + " as a source directory");
    }

    if (hasTestDir) {
      String[] includedFiles = getIncludedFiles(testSourceDirectory.getAbsolutePath(), testExcludes, getTestIncludes());
      compileFiles(includedFiles, testSourceDirectory, testOutputDirectory);
      project.addTestCompileSourceRoot(testOutputDirectory.getAbsolutePath());
    }
  }

  protected void configure(Avro4kCompiler compiler) throws MojoExecutionException {
    compiler.setTemplateDir(templateDirectory);
    compiler.setFieldVisibility(fieldVisibility);
    compiler.setCreateSetters(createSetters);
    compiler.setAdditionalVelocityTools(instantiateAdditionalVelocityTools());
    configureLogicalTypeConversions(compiler);
    Map<String, String> classRenameRules = new HashMap<>();
    for (RenamedClass clazz : renamedClasses) {
      classRenameRules.put(clazz.getRegex(), clazz.getReplacement());
    }
    compiler.setRenamedClasses(classRenameRules);
    compiler.setOutputCharacterEncoding(project.getProperties().getProperty("project.build.sourceEncoding"));
  }

  private void configureLogicalTypeConversions(Avro4kCompiler compiler) throws MojoExecutionException {
    try {
      final URLClassLoader classLoader = createClassLoader();
      for (String customConversion : logicalTypeConversions) {
        try {
          compiler.addLogicalTypeConversion((LogicalTypeConversion) classLoader.loadClass(customConversion).newInstance());
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
          throw new MojoExecutionException("Could not load custom logicalTypeConversion with class " + customConversion, e);
        }
      }
    } catch (DependencyResolutionRequiredException | MalformedURLException e) {
      throw new MojoExecutionException("Could not create classloader for logicalTypeConversion resolution.", e);
    }
  }

  private String[] getIncludedFiles(String absPath, String[] excludes, String[] includes) {
    FileSetManager fileSetManager = new FileSetManager();
    FileSet fs = new FileSet();
    fs.setDirectory(absPath);
    fs.setFollowSymlinks(false);

    // exclude imports directory since it has already been compiled.
    if (imports != null) {
      String importExclude = null;

      for (String importFile : this.imports) {
        File file = new File(importFile);

        if (file.isDirectory()) {
          importExclude = file.getName() + "/**";
        } else if (file.isFile()) {
          importExclude = "**/" + file.getName();
        }

        fs.addExclude(importExclude);
      }
    }
    for (String include : includes) {
      fs.addInclude(include);
    }
    for (String exclude : excludes) {
      fs.addExclude(exclude);
    }
    return fileSetManager.getIncludedFiles(fs);
  }

  private void compileFiles(String[] files, File sourceDir, File outDir) throws MojoExecutionException {
    for (String filename : files) {
      try {
        doCompile(filename, sourceDir, outDir);
      } catch (IOException e) {
        throw new MojoExecutionException("Error compiling protocol file " + filename + " to " + outDir, e);
      }
    }
  }

  protected List<Object> instantiateAdditionalVelocityTools() {
    List<Object> velocityTools = new ArrayList<>(velocityToolsClassesNames.length);
    for (String velocityToolClassName : velocityToolsClassesNames) {
      try {
        Class<?> klass = Class.forName(velocityToolClassName);
        velocityTools.add(klass.newInstance());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return velocityTools;
  }

  protected abstract void doCompile(String filename, File sourceDirectory, File outputDirectory)
          throws IOException, MojoExecutionException;

  protected URLClassLoader createClassLoader() throws DependencyResolutionRequiredException, MalformedURLException {
    List<URL> urls = appendElements(project.getRuntimeClasspathElements());
    urls.addAll(appendElements(project.getTestClasspathElements()));
    return new URLClassLoader(urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
  }

  private List<URL> appendElements(List<String> runtimeClasspathElements) throws MalformedURLException {
    List<URL> runtimeUrls = new ArrayList<>();
    if (runtimeClasspathElements != null) {
      for (String runtimeClasspathElement : runtimeClasspathElements) {
        runtimeUrls.add(new File(runtimeClasspathElement).toURI().toURL());
      }
    }
    return runtimeUrls;
  }

  protected abstract String[] getIncludes();

  protected abstract String[] getTestIncludes();

}
