package io.temporal.workflowcheck;

import org.objectweb.asm.Type;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

class Printer {
  static String methodText(ClassInfo classInfo, String methodName, ClassInfo.MethodInfo methodInfo) {
    var printer = new Printer();
    printer.appendMethod(classInfo, methodName, methodInfo, "", Collections.newSetFromMap(new IdentityHashMap<>()));
    return printer.bld.toString();
  }

  private final StringBuilder bld = new StringBuilder();

  private void appendMethod(
          ClassInfo classInfo,
          String methodName,
          ClassInfo.MethodInfo methodInfo,
          String indent,
          Set<ClassInfo.MethodInfo> seenMethods) {
    seenMethods.add(methodInfo);
    bld.append(indent);
    if (methodInfo.workflowImpl != null) {
      bld.append("Workflow method ");
      appendFriendlyMethod(classInfo.name, methodName, methodInfo.descriptor);
      bld.append(" (declared on ");
      appendFriendlyClassName(methodInfo.workflowImpl.declClassInfo.name);
      bld.append(")");
    } else {
      bld.append("Method ");
      appendFriendlyMethod(classInfo.name, methodName, methodInfo.descriptor);
    }
    if (!methodInfo.isInvalid()) {
      bld.append(" is valid\n");
    } else if (methodInfo.configuredInvalid != null) {
      bld.append(" is configured as invalid\n");
    } else if (seenMethods.size() > 30) {
      bld.append(" is invalid (stack depth exceeded, stopping here)\n");
    } else if (methodInfo.invalidCalls != null) {
      bld.append(" has ").append(methodInfo.invalidCalls.size()).append(" invalid call");
      if (methodInfo.invalidCalls.size() > 1) {
        bld.append('s');
      }
      bld.append(":\n");
      for (var call : methodInfo.invalidCalls) {
        appendInvalidCall(classInfo, call, indent + "  ", seenMethods);
      }
    } else {
      // Should not happen
      bld.append(" is invalid for unknown reasons\n");
    }
    seenMethods.remove(methodInfo);
  }

  private void appendInvalidCall(
          ClassInfo callerClassInfo,
          ClassInfo.MethodInvalidCallInfo callInfo,
          String indent,
          Set<ClassInfo.MethodInfo> seenMethods) {
    bld.append(indent);
    if (callerClassInfo.fileName == null) {
      bld.append("<unknown-file>");
    } else {
      bld.append(callerClassInfo.fileName);
      if (callInfo.line != null) {
        bld.append(':').append(callInfo.line);
      }
    }
    bld.append(" invokes ");
    appendFriendlyMethod(callInfo.className, callInfo.methodName, callInfo.methodDescriptor);
    if (callInfo.resolvedInvalidClass == null) {
      // Should never happen
      bld.append(" (resolution failed)\n");
    } else if (callInfo.resolvedInvalidMethod == null) {
      bld.append(" which is configured as invalid\n");
    } else if (seenMethods.contains(callInfo.resolvedInvalidMethod)) {
      // Should not happen
      bld.append(" (unexpected recursion)\n");
    } else {
      bld.append(":\n");
      appendMethod(
              callInfo.resolvedInvalidClass,
              callInfo.methodName,
              callInfo.resolvedInvalidMethod,
              indent + "  ",
              seenMethods);
    }
  }

  private void appendFriendlyClassName(String className) {
    bld.append(className.replace('/', '.'));
  }

  private void appendFriendlyMethod(String className, String methodName, String methodDescriptor) {
    appendFriendlyClassName(className);
    bld.append('.').append(methodName).append('(');
    var argTypes = Type.getArgumentTypes(methodDescriptor);
    for (var i = 0; i < argTypes.length; i++) {
      if (i > 0) {
        bld.append(", ");
      }
      bld.append(argTypes[i].getClassName());
    }
    bld.append(')');
  }
}
