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
    var actual = InvalidMemberAccessAssertion.fromClassInfos(infos);
    var expected = SourceAssertions.fromTestSource();

    // Check differences in both directions
    var diff = new ArrayList<>(actual);
    diff.removeAll(expected.invalidAccesses);
    for (var v : diff) {
      fail("Unexpected invalid access: " + v);
    }
    diff = new ArrayList<>(expected.invalidAccesses);
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
          List<InvalidMemberAccessAssertion> invalidAccesses,
          List<LogAssertion> logs) {

    private static final String[] SOURCE_FILES = new String[]{
            "io/temporal/workflowcheck/testdata/BadCalls.java",
            "io/temporal/workflowcheck/testdata/Configured.java",
            "io/temporal/workflowcheck/testdata/Suppression.java",
            "io/temporal/workflowcheck/testdata/UnsafeIteration.java"
    };

    static SourceAssertions fromTestSource() {
      var invalidAccesses = new ArrayList<InvalidMemberAccessAssertion>();
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
        invalidAccesses.addAll(InvalidMemberAccessAssertion.fromJavaLines(fileName, lines));
        logAsserts.addAll(LogAssertion.fromJavaLines(lines));
      }
      return new SourceAssertions(invalidAccesses, logAsserts);
    }
  }

  record InvalidMemberAccessAssertion(
          String fileName,
          int line,
          String className,
          String member,
          String accessedClass,
          String accessedMember,
          // Cause info can be null
          @Nullable
          String accessedCauseClass,
          @Nullable
          String accessedCauseMethod
  ) {
    static List<InvalidMemberAccessAssertion> fromClassInfos(List<ClassInfo> infos) {
      var assertions = new ArrayList<InvalidMemberAccessAssertion>();
      for (var info : infos) {
        for (var methods : info.methods.entrySet()) {
          for (var method : methods.getValue()) {
            // Only invalid workflow impls with invalid accesses
            if (method.workflowImpl != null && method.invalidMemberAccesses != null) {
              for (var access : method.invalidMemberAccesses) {
                // Find first cause
                ClassInfo.MethodInvalidMemberAccessInfo causeAccess = null;
                if (access.resolvedInvalidMethod != null &&
                        access.resolvedInvalidMethod.invalidMemberAccesses != null) {
                  causeAccess = access.resolvedInvalidMethod.invalidMemberAccesses.get(0);
                }
                assertions.add(new InvalidMemberAccessAssertion(
                        info.fileName,
                        Objects.requireNonNull(access.line),
                        info.name,
                        methods.getKey() + method.descriptor,
                        access.className,
                        access.operation == ClassInfo.MethodInvalidMemberAccessInfo.Operation.METHOD_CALL ?
                                access.memberName + access.memberDescriptor : access.memberName,
                        causeAccess == null ? null : causeAccess.className,
                        causeAccess == null ? null :
                                causeAccess.operation == ClassInfo.MethodInvalidMemberAccessInfo.Operation.METHOD_CALL ?
                                        causeAccess.memberName + causeAccess.memberDescriptor : causeAccess.memberName));
              }
            }
          }
        }
      }
      return assertions;
    }

    static List<InvalidMemberAccessAssertion> fromJavaLines(String fileName, List<String> lines) {
      var assertions = new ArrayList<InvalidMemberAccessAssertion>();
      for (var lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
        var line = lines.get(lineIdx).trim();
        // Confirm INVALID
        if (!line.startsWith("// INVALID")) {
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
        assertions.add(new InvalidMemberAccessAssertion(
                fileName,
                lineIdx + 2,
                Objects.requireNonNull(bullets.get("class")),
                Objects.requireNonNull(bullets.get("method")),
                Objects.requireNonNull(bullets.get("accessedClass")),
                Objects.requireNonNull(bullets.get("accessedMember")),
                bullets.get("accessedCauseClass"),
                bullets.get("accessedCauseMethod")));
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
