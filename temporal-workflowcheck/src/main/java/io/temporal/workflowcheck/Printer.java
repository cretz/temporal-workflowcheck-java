package io.temporal.workflowcheck;

import org.objectweb.asm.Type;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

class Printer {
  static String invalidWorkflowMethodText(ClassInfo info, String method) {
    var printer = new Printer();
    printer.appendInvalidWorkflowMethod(info, method);
    return printer.bld.toString();
  }

  private final StringBuilder bld = new StringBuilder();

  private void appendInvalidWorkflowMethod(ClassInfo info, String method) {
    bld.append("Workflow method ");
    appendFriendlyMethod(info.className, method);
    var invalid = Objects.requireNonNull(info.invalidMethods.get(method), "expected invalid method");
    bld.append(" has ").append(invalid.invalidCalls().size()).append(" invalid call");
    if (invalid.invalidCalls().size() > 1) {
      bld.append('s');
    }
    bld.append(":\n");
    appendInvalidMethod(info, invalid, "  ");
  }

  private void appendInvalidMethod(ClassInfo info, ClassInfo.InvalidMethod method, String indent) {
    for (var call : method.invalidCalls()) {
      appendInvalidCall(info, call, indent, new HashSet<>());
    }
  }

  private void appendInvalidCall(ClassInfo info, ClassInfo.InvalidCall call, String indent, Set<String> seen) {
    bld.append(indent);
    if (info.fileName == null) {
      bld.append("<unknown-file>");
    } else {
      bld.append(info.fileName);
      if (call.line() != null) {
        bld.append(':').append(call.line());
      }
    }
    bld.append(" invokes ");
    appendFriendlyMethod(call.className(), call.method());
    if (call.classInfo() == null || call.classInfo().invalidMethods == null ||
            !call.classInfo().invalidMethods.containsKey(call.method())) {
      bld.append(" which is inherently invalid\n");
    } else if (!seen.add(call.className() + "." + call.method())) {
      bld.append(" (recursive)\n");
    } else {
      var next = call.classInfo().invalidMethods.get(call.method());
      bld.append(" which itself makes ").append(next.invalidCalls().size()).append(" invalid call");
      if (next.invalidCalls().size() > 1) {
        bld.append('s');
      }
      bld.append(":\n");
      appendInvalidMethod(call.classInfo(), next, indent + "  ");
    }
  }

  private void appendFriendlyMethod(String className, String method) {
    bld.append(className.replace('/', '.')).append('.');
    // Assume the paren is always present
    var parenIndex = method.indexOf('(');
    bld.append(method, 0, parenIndex).append('(');
    var argTypes = Type.getArgumentTypes(method.substring(parenIndex));
    for (var i = 0; i < argTypes.length; i++) {
      if (i > 0) {
        bld.append(", ");
      }
      bld.append(argTypes[i].getClassName());
    }
    bld.append(')');
  }
}
