package io.temporal.workflowcheck;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

public class WorkflowCheckTest {
  static {
    try (InputStream is = WorkflowCheckTest.class.getClassLoader().
            getResourceAsStream("logging.properties")) {
      LogManager.getLogManager().readConfiguration(is);
    } catch (IOException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private final LoggingCaptureHandler classInfoVisitorLogs = new LoggingCaptureHandler();

  @BeforeEach
  public void beforeEach() {
    Logger.getLogger(ClassInfoVisitor.class.getName()).addHandler(classInfoVisitorLogs);
  }

  @AfterEach
  public void afterEach() {
    Logger.getLogger(ClassInfoVisitor.class.getName()).removeHandler(classInfoVisitorLogs);
  }

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
  * Recursive on self (both positive and negative)
  * Recursive pair of functions calling each other in workflow code (both with failures and not)
  * Recursive pair of functions calling each other from different classes (both with failures and not)
  * Positive on default impl of workflow method in interface
  * Positive on parent impl of workflow method on abstract class later overridden
  * Covariant return on workflow method (should probably fail)
  * Covariant generic return on workflow method (should probably fail)
  * As lambda/invokedynamic method reference (e.g. Instant::now)
  * Nested classes
  * Test exclusions at all levels (specific descriptor, method, class, and package)
  * Workflows inside module
  */

  @Test
  public void testWorkflowCheck() throws IOException {
    // Load properties
    var configProps = new Properties();
    try (var is = getClass().getResourceAsStream("testdata/workflowcheck.properties")) {
      configProps.load(is);
    }
    // Collect infos
    var config = Config.fromProperties(Config.defaultProperties(), configProps);
    var infos = new WorkflowCheck(config).findWorkflowClasses(System.getProperty("java.class.path"));
    for (var info : infos) {
      for (var methods : info.methods.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
        for (var method : methods.getValue()) {
          if (method.workflowImpl != null) {
            System.out.println(Printer.methodText(info, methods.getKey(), method));
          }
        }
      }
    }

    // Collect actual/expected lists (we accept perf penalty of not being sets)
    var actual = InvalidCallAssertion.fromClassInfos(infos);
    var expected = SourceAssertions.fromTestSource();

    // Check differences in both directions
    var diff = new ArrayList<>(actual);
    diff.removeAll(expected.invalidCalls);
    for (var v : diff) {
      fail("Unexpected invalid call: " + v);
    }
    diff = new ArrayList<>(expected.invalidCalls);
    diff.removeAll(actual);
    for (var v : diff) {
      fail("Missing expected invalid call: " + v);
    }

    // Check that all logs are present
    var actualLogs = classInfoVisitorLogs.collectRecords();
    for (var expectedLog : expected.logs) {
      assertTrue(actualLogs.stream().anyMatch(actualLog ->
                      actualLog.getLevel().equals(expectedLog.level) &&
                              classInfoVisitorLogs.getFormatter().formatMessage(actualLog).equals(expectedLog.message)),
              "Cannot find " + expectedLog.level + " log with message: " + expectedLog.message);
    }
  }

  record SourceAssertions(
          List<InvalidCallAssertion> invalidCalls,
          List<LogAssertion> logs) {

    private static final String[] SOURCE_FILES = new String[]{
            "io/temporal/workflowcheck/testdata/BadCalls.java",
            "io/temporal/workflowcheck/testdata/Configured.java",
            "io/temporal/workflowcheck/testdata/Suppression.java",
            "io/temporal/workflowcheck/testdata/UnsafeIteration.java"
    };

    static SourceAssertions fromTestSource() {
      var invalidCallAsserts = new ArrayList<InvalidCallAssertion>();
      var logAsserts = new ArrayList<LogAssertion>();
      for (var resourcePath : SOURCE_FILES) {
        var fileParts = resourcePath.split("/");
        var fileName = fileParts[fileParts.length - 1];
        // Load lines
        List<String> lines;
        try (var is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
          assertNotNull(is);
          var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
          lines = reader.lines().toList();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }

        // Add asserts
        invalidCallAsserts.addAll(InvalidCallAssertion.fromJavaLines(fileName, lines));
        logAsserts.addAll(LogAssertion.fromJavaLines(lines));
      }
      return new SourceAssertions(invalidCallAsserts, logAsserts);
    }
  }

  record InvalidCallAssertion(
          String fileName,
          int line,
          String className,
          String method,
          String callClass,
          String callMethod,
          // Cause info can be null
          @Nullable
          String callCauseClass,
          @Nullable
          String callCauseMethod
  ) {
    static List<InvalidCallAssertion> fromClassInfos(List<ClassInfo> infos) {
      var assertions = new ArrayList<InvalidCallAssertion>();
      for (var info : infos) {
        for (var methods : info.methods.entrySet()) {
          for (var method : methods.getValue()) {
            // Only invalid workflow impls with calls
            if (method.workflowImpl != null && method.invalidCalls != null) {
              for (var call : method.invalidCalls) {
                // Find first cause
                ClassInfo.MethodInvalidCallInfo causeCall = null;
                if (call.resolvedInvalidMethod != null && call.resolvedInvalidMethod.invalidCalls != null) {
                  causeCall = call.resolvedInvalidMethod.invalidCalls.get(0);
                }
                assertions.add(new InvalidCallAssertion(
                        info.fileName,
                        Objects.requireNonNull(call.line),
                        info.name,
                        methods.getKey() + method.descriptor,
                        call.className,
                        call.methodName + call.methodDescriptor,
                        causeCall == null ? null : causeCall.className,
                        causeCall == null ? null : causeCall.methodName + causeCall.methodDescriptor));
              }
            }
          }
        }
      }
      return assertions;
    }

    static List<InvalidCallAssertion> fromJavaLines(String fileName, List<String> lines) {
      var assertions = new ArrayList<InvalidCallAssertion>();
      for (var lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
        var line = lines.get(lineIdx).trim();
        // Confirm INVALID_CALL
        if (!line.startsWith("// INVALID_CALL")) {
          continue;
        }
        // Collect indented bullets
        var bullets = new HashMap<String, String>(6);
        while (lines.get(lineIdx + 1).trim().startsWith("//   * ")) {
          lineIdx++;
          line = lines.get(lineIdx).substring(lines.get(lineIdx).indexOf("/") + 7);
          var colonIndex = line.indexOf(": ");
          assertTrue(colonIndex > 0);
          bullets.put(line.substring(0, colonIndex), line.substring(colonIndex + 2));
        }
        assertions.add(new InvalidCallAssertion(
                fileName,
                lineIdx + 2,
                Objects.requireNonNull(bullets.get("class")),
                Objects.requireNonNull(bullets.get("method")),
                Objects.requireNonNull(bullets.get("callClass")),
                Objects.requireNonNull(bullets.get("callMethod")),
                bullets.get("callCauseClass"),
                bullets.get("callCauseMethod")));
      }
      return assertions;
    }
  }

  record LogAssertion(Level level, String message) {
    static List<LogAssertion> fromJavaLines(List<String> lines) {
      return lines.stream().
              map(String::trim).
              filter(line -> line.startsWith("// LOG: ")).
              map(line -> {
                var dashIndex = line.indexOf('-');
                assertTrue(dashIndex > 0);
                return new LogAssertion(
                        Level.parse(line.substring(8, dashIndex).trim()),
                        line.substring(dashIndex + 1).trim());
              }).toList();
    }
  }
}
