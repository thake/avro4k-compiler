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

import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Protocol;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Ant task to generate Java interface and classes for a protocol.
 */
public class ProtocolTask extends Task {
  private File src;
  private File dest = new File(".");

  private final ArrayList<FileSet> filesets = new ArrayList<>();

  /**
   * Set the schema file.
   */
  public void setFile(File file) {
    this.src = file;
  }

  /** Set the output directory */
  public void setDestdir(File dir) {
    this.dest = dir;
  }


  /** Add a fileset. */
  public void addFileset(FileSet set) {
    filesets.add(set);
  }

  /** Run the compiler. */
  @Override
  public void execute() {
    if (src == null && filesets.size() == 0)
      throw new BuildException("No file or fileset specified.");

    if (src != null)
      compile(src);

    Project myProject = getProject();
    for (FileSet fs : filesets) {
      DirectoryScanner ds = fs.getDirectoryScanner(myProject);
      File dir = fs.getDir(myProject);
      String[] srcs = ds.getIncludedFiles();
      for (String src1 : srcs) {
        compile(new File(dir, src1));
      }
    }
  }

  protected void doCompile(File src, File dir) throws IOException {
    Protocol protocol = Protocol.parse(src);
    Avro4kCompiler compiler = new Avro4kCompiler(protocol);
    compiler.compileToDestination(src, dest);
  }

  private void compile(File file) {
    try {
      doCompile(file, dest);
    } catch (AvroRuntimeException | IOException e) {
      throw new BuildException(e);
    }
  }
}
