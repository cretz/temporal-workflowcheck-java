package io.temporal.workflowcheck;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public class ClassInfo {
  String className;
  String fileName;
  // Keyed by method name + descriptor
  @Nullable
  Map<String, WorkflowMethodDecl> workflowMethodDecls;
  // Keyed by method name + descriptor
  @Nullable
  Map<String, WorkflowMethodImpl> workflowMethodImpls;
  // Keyed by method name + descriptor
  @Nullable
  Map<String, InvalidMethod> invalidMethods;

  public String getClassName() {
    return className;
  }

  public String getFileName() {
    return fileName;
  }

  @Nullable
  public Map<String, WorkflowMethodDecl> getWorkflowMethodDecls() {
    return workflowMethodDecls;
  }

  @Nullable
  public Map<String, WorkflowMethodImpl> getWorkflowMethodImpls() {
    return workflowMethodImpls;
  }

  @Nullable
  public Map<String, InvalidMethod> getInvalidMethods() {
    return invalidMethods;
  }

  public record WorkflowMethodDecl(
          WorkflowMethodDeclKind kind) {
  }

  public enum WorkflowMethodDeclKind {
    WORKFLOW,
    QUERY,
    SIGNAL,
    UPDATE,
    UPDATE_VALIDATOR;

    static final Map<String, WorkflowMethodDeclKind> annotationDescriptors = Map.of(
            "Lio/temporal/workflow/WorkflowMethod;", WORKFLOW,
            "Lio/temporal/workflow/QueryMethod;", QUERY,
            "Lio/temporal/workflow/SignalMethod;", SIGNAL,
            "Lio/temporal/workflow/UpdateMethod;", UPDATE,
            "Lio/temporal/workflow/UpdateValidatorMethod;", UPDATE_VALIDATOR);
  }

  public record WorkflowMethodImpl(
          String declClass,
          WorkflowMethodDecl declMethod) {
  }

  public record InvalidMethod(
          @Nullable
          List<InvalidCall> invalidCalls) {
  }

  public record InvalidCall(
          String className,
          // TODO(cretz): Document that this is null for inherently illegal calls
          @Nullable
          ClassInfo classInfo,
          // name + descriptor
          String method,
          // This is null if no known line number
          @Nullable
          Integer line) {
  }
}