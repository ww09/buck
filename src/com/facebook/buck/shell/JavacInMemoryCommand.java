/*
 * Copyright 2012-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.shell;

import com.facebook.buck.model.AnnotationProcessingData;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.io.File;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class JavacInMemoryCommand implements Command {

  private final File outputDirectory;

  private final Set<String> javaSourceFilePaths;

  private final Supplier<String> bootclasspathSupplier;

  private final AnnotationProcessingData annotationProcessingData;

  private final String sourceLevel;

  private final String targetLevel;

  protected final ImmutableSet<String> classpathEntries;

  public JavacInMemoryCommand(
      String outputDirectory,
      Set<String> javaSourceFilePaths,
      Set<String> classpathEntries,
      Supplier<String> bootclasspathSupplier,
      AnnotationProcessingData annotationProcessingData) {

    this(
      outputDirectory,
      javaSourceFilePaths,
      classpathEntries,
      bootclasspathSupplier,
        annotationProcessingData,
      JavacOptionsUtil.DEFAULT_SOURCE_LEVEL,
      JavacOptionsUtil.DEFAULT_TARGET_LEVEL);
  }

    public JavacInMemoryCommand(
      String outputDirectory,
      Set<String> javaSourceFilePaths,
      Set<String> classpathEntries,
      Supplier<String> bootclasspathSupplier,
      AnnotationProcessingData annotationProcessingData,
      String sourceLevel,
      String targetLevel) {
    Preconditions.checkNotNull(outputDirectory);
    this.outputDirectory = new File(outputDirectory);
    this.javaSourceFilePaths = ImmutableSet.copyOf(javaSourceFilePaths);
    this.classpathEntries = ImmutableSet.copyOf(classpathEntries);
    this.bootclasspathSupplier = Preconditions.checkNotNull(bootclasspathSupplier);
    this.annotationProcessingData = Preconditions.checkNotNull(annotationProcessingData);
    this.sourceLevel = Preconditions.checkNotNull(sourceLevel);
    this.targetLevel = Preconditions.checkNotNull(targetLevel);
  }

  /**
   * This is public for testing purposes and is not intended for use outside this class.
   *
   * Returns a list of command-line options to pass to javac.  These options reflect
   * the configuration of this javac command.
   *
   * @param context the ExecutionContext with in which javac will run
   * @return list of String command-line options.
   */
  public ImmutableList<String> getOptions(ExecutionContext context,
      Set<String> buildClasspathEntries) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    JavacOptionsUtil.addOptions(builder,
        context,
        outputDirectory,
        buildClasspathEntries,
        bootclasspathSupplier,
        annotationProcessingData,
        sourceLevel,
        targetLevel);
    return builder.build();
  }

  @Override
  public final int execute(ExecutionContext context) {

    return executeBuild(context);
  }

  protected int executeBuild(ExecutionContext context) {
    return buildWithClasspath(context, ImmutableSet.copyOf(classpathEntries));
  }

  protected int buildWithClasspath(ExecutionContext context,
      Set<String> buildClasspathEntries) {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    Preconditions.checkNotNull(compiler,
        "If using JRE instead of JDK, ToolProvider.getSystemJavaCompiler() may be null.");
    StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
    List<String> options = getOptions(context, buildClasspathEntries);
    List<String> classNamesForAnnotationProcessing = ImmutableList.of();
    Iterable<? extends JavaFileObject> compilationUnits =
        fileManager.getJavaFileObjectsFromStrings(javaSourceFilePaths);

    Writer compilerOutputWriter = new PrintWriter(context.getStdErr());
    JavaCompiler.CompilationTask compilationTask = compiler.getTask(
        compilerOutputWriter,
        fileManager,
        diagnostics,
        options,
        classNamesForAnnotationProcessing,
        compilationUnits);
    // Invoke the compilation and inspect the result.
    boolean isSuccess = compilationTask.call();
    if (isSuccess) {
      return 0;
    } else {
      if (context.getVerbosity().shouldPrintStandardInformation()) {
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
          if (!System.getProperty("java.version").startsWith("1.6")) {
            // TODO(simons): When we finally move the world to Java 7, this is all we need.
            context.getStdErr().println(diagnostic);
          } else {
            // TODO(simons): Warn the user that they're using an EOLd JDK.
            String newLine = System.getProperty("line.separator", "\n");
            StringBuilder message = new StringBuilder();

            if (diagnostic.getSource() != null) {
              message.append(diagnostic.getSource().getName())
                  .append(":")
                  .append(diagnostic.getLineNumber())
                  .append(": ").append(diagnostic.getMessage(Locale.getDefault()))
                  .append(newLine);
            } else {
              message.append(diagnostic.getMessage(Locale.getDefault())).append(newLine);
            }
            context.getStdErr().println(message.toString());
          }
        }
      }
      return 1;
    }
  }

  @Override
  public String getDescription(ExecutionContext context) {
    Set<String> buildClassPathEntries = classpathEntries;

    StringBuilder builder = new StringBuilder("javac ");
    Joiner.on(" ").appendTo(builder, getOptions(context, buildClassPathEntries));
    builder.append(" ");
    Joiner.on(" ").appendTo(builder, javaSourceFilePaths);

    return builder.toString();
  }

  @Override
  public String getShortName(ExecutionContext context) {
    return String.format("javac %s", outputDirectory);
  }

  public Set<String> getSrcs() {
    return javaSourceFilePaths;
  }

}
