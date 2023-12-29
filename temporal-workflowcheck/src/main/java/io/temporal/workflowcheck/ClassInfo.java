package io.temporal.workflowcheck;

import javax.annotation.Nullable;
import java.util.*;

public class ClassInfo {
  int access;
  String name;
  @Nullable
  String fileName;
  @Nullable
  String superClass;
  @Nullable
  String[] superInterfaces;

  // Keyed by method name, each is an overload in no particular order. This may
  // not include unimportant methods after class loading.
  Map<String, List<MethodInfo>> methods = new HashMap<>();

  public int getAccess() {
    return access;
  }

  public String getName() {
    return name;
  }

  @Nullable
  public String getFileName() {
    return fileName;
  }

  @Nullable
  public String getSuperClass() {
    return superClass;
  }

  @Nullable
  public String[] getSuperInterfaces() {
    return superInterfaces;
  }

  public Map<String, List<MethodInfo>> getMethods() {
    return methods;
  }

  public static class MethodInfo {
    final int access;
    final String descriptor;
    @Nullable
    final Boolean configuredInvalid;
    @Nullable
    MethodWorkflowDeclInfo workflowDecl;
    // Set after loading
    @Nullable
    MethodWorkflowImplInfo workflowImpl;
    // Removed after loading (if null then invalidCalls is now canonical set).
    // May be null when loading if configuredInvalid already set.
    @Nullable
    List<MethodInvalidCallInfo> calls;
    // Set after loading (but can still be null), never non-null+empty
    @Nullable
    List<MethodInvalidCallInfo> invalidCalls;

    MethodInfo(int access, String descriptor, @Nullable Boolean configuredInvalid) {
      this.access = access;
      this.descriptor = descriptor;
      this.configuredInvalid = configuredInvalid;
    }

    public int getAccess() {
      return access;
    }

    public String getDescriptor() {
      return descriptor;
    }

    @Nullable
    public Boolean getConfiguredInvalid() {
      return configuredInvalid;
    }

    @Nullable
    public MethodWorkflowDeclInfo getWorkflowDecl() {
      return workflowDecl;
    }

    @Nullable
    public MethodWorkflowImplInfo getWorkflowImpl() {
      return workflowImpl;
    }

    @Nullable
    public List<MethodInvalidCallInfo> getInvalidCalls() {
      return invalidCalls;
    }

    public boolean isInvalid() {
      return configuredInvalid != null ? configuredInvalid : invalidCalls != null;
    }
  }

  public static class MethodWorkflowDeclInfo {
    final Kind kind;

    MethodWorkflowDeclInfo(Kind kind) {
      this.kind = kind;
    }

    public Kind getKind() {
      return kind;
    }

    public enum Kind {
      WORKFLOW,
      QUERY,
      SIGNAL,
      UPDATE,
      UPDATE_VALIDATOR;

      static final Map<String, Kind> annotationDescriptors = Map.of(
              "Lio/temporal/workflow/WorkflowMethod;", WORKFLOW,
              "Lio/temporal/workflow/QueryMethod;", QUERY,
              "Lio/temporal/workflow/SignalMethod;", SIGNAL,
              "Lio/temporal/workflow/UpdateMethod;", UPDATE,
              "Lio/temporal/workflow/UpdateValidatorMethod;", UPDATE_VALIDATOR);
    }
  }

  public static class MethodWorkflowImplInfo {
    final ClassInfo declClassInfo;
    final MethodWorkflowDeclInfo workflowDecl;

    MethodWorkflowImplInfo(ClassInfo declClassInfo, MethodWorkflowDeclInfo workflowDecl) {
      this.declClassInfo = declClassInfo;
      this.workflowDecl = workflowDecl;
    }

    public ClassInfo getDeclClassInfo() {
      return declClassInfo;
    }

    public MethodWorkflowDeclInfo getWorkflowDecl() {
      return workflowDecl;
    }
  }

  public static class MethodInvalidCallInfo {
    final String className;
    final String methodName;
    final String methodDescriptor;
    @Nullable
    final Integer line;

    // Set in second phase
    @Nullable
    ClassInfo resolvedInvalidClass;
    // This is null even when above is not if the method is configured invalid
    @Nullable
    MethodInfo resolvedInvalidMethod;

    MethodInvalidCallInfo(String className, String methodName, String methodDescriptor, @Nullable Integer line) {
      this.className = className;
      this.methodName = methodName;
      this.methodDescriptor = methodDescriptor;
      this.line = line;
    }

    public String getClassName() {
      return className;
    }

    public String getMethodName() {
      return methodName;
    }

    public String getMethodDescriptor() {
      return methodDescriptor;
    }

    @Nullable
    public Integer getLine() {
      return line;
    }

    @Nullable
    public ClassInfo getResolvedInvalidClass() {
      return resolvedInvalidClass;
    }

    @Nullable
    public MethodInfo getResolvedInvalidMethod() {
      return resolvedInvalidMethod;
    }
  }
}