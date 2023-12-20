package io.temporal.workflowcheck;

import java.util.*;

class ClassInfoLoading {
  final Set<String> methodsVisited = new HashSet<>();
  final Map<String, List<PostProcessCheck>> postProcessChecks = new HashMap<>();

  static class PostProcessCheck {
    final ClassInfo callingClassInfo;
    final String callingMethod;
    final Integer callingLineNumber;

    PostProcessCheck(ClassInfo callingClassInfo, String callingMethod, Integer callingLineNumber) {
      this.callingClassInfo = callingClassInfo;
      this.callingMethod = callingMethod;
      this.callingLineNumber = callingLineNumber;
    }
  }
}
