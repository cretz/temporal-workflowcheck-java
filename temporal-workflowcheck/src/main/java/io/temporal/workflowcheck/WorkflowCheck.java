package io.temporal.workflowcheck;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class WorkflowCheck {
  public static void suppressWarnings() {}

  public static void suppressWarnings(String specificDesc) {}

  public static void restoreWarnings() { }

  @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
  public @interface SuppressWarnings {
    // Note, intentionally not called "value" for the default because there may
    // be other warnings to suppress in the future
    String[] invalidCalls() default {};
  }

  private final Config config;

  public WorkflowCheck(Config config) {
    this.config = config;
  }

  public List<ClassInfo> findInvalidWorkflowImpls(String... classPaths) throws IOException {
    var infosWithInvalidImpls = new ArrayList<ClassInfo>();
    try (var cp = new ClassPath(classPaths)) {
      var context = new ClassInfoLoader(config, cp.classLoader);
      // Read all non-built-in classes to find workflows
      for (String className : cp.classes) {
        var info = context.load(className);
        // Check if there are any impls and any of them are also invalid methods
        if (info != null && info.workflowMethodImpls != null && info.invalidMethods != null &&
                !Collections.disjoint(info.workflowMethodImpls.keySet(), info.invalidMethods.keySet())) {
          infosWithInvalidImpls.add(info);
        }
      }
    }
    // Sort the infos by the class name for deterministic output
    infosWithInvalidImpls.sort(Comparator.comparing(i -> i.className));
    return infosWithInvalidImpls;
  }
}
