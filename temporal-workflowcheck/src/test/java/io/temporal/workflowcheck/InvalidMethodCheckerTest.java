package io.temporal.workflowcheck;

import org.junit.jupiter.api.Test;

public class InvalidMethodCheckerTest {
  /*
  TODO(cretz): Tests:
  * Positive if direct a workflow method, signal, or update
  * Negative if direct not a workflow method
  * Negative if direct in an uncalled method on the workflow class
  * Positive if indirect via same-class call
  * Positive if indirect via another-class-same-compilation-unit call
  * Positive if indirect via external JAR
  * Positive if indirect via two external JARs
  * Negative if direct not included due to config
  * Negative if indirect not included due to config
  * Negative if suppressed at class, method, etc level
  * Recursive pair of functions calling each other in workflow code
  * Positive on default impl of workflow method in interface
  * Positive on parent impl of workflow method on abstract class later overridden
  */

  @Test
  public void testLoadInvalidMethods() {
    var classPath = System.getProperty("java.class.path");
    System.out.println("!!! CP: " + classPath);
    var checker = new InvalidMethodChecker(classPath);
    checker.loadInvalidMethods();
  }
}
