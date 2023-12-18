package io.temporal.workflowcheck;

import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import com.kohlschutter.jdk.standaloneutil.ToolProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class WorkflowCheckProcessorTest {
  @Test
  public void testProcessor() {
    var comp = Compiler.
            compiler(ToolProvider.getSystemJavaCompiler()).
            withOptions("--release", "17").
            withProcessors(new WorkflowCheckProcessor()).
            compile(JavaFileObjects.forResource("io/temporal/workflowcheck/testdata/BadWorkflowCalls.java"));
    CompilationSubject.assertThat(comp).succeeded();
  }
}
