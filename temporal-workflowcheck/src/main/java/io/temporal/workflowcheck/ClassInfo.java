package io.temporal.workflowcheck;

import javax.annotation.Nullable;
import java.util.*;

/** Information about a class. */
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
  // not include unimportant methods after processing.
  Map<String, List<MethodInfo>> methods = new HashMap<>();

  // This may be removed after processing.
  @Nullable
  Set<String> nonFinalStaticFields;

  /** JVM access flag for the class as defined in JVM spec. */
  public int getAccess() {
    return access;
  }

  /** Full binary class name as defined in JVM spec (i.e. using '/' instead of '.'). */
  public String getName() {
    return name;
  }

  /** File name the class was defined in if known. */
  @Nullable
  public String getFileName() {
    return fileName;
  }

  /** Super class of this class. Only null for java/lang/Object. */
  @Nullable
  public String getSuperClass() {
    return superClass;
  }

  /** Super interfaces of this class if any. */
  @Nullable
  public String[] getSuperInterfaces() {
    return superInterfaces;
  }

  /**
   * Methods of note on this class. This may not include all methods, but
   * rather only the methods that are important (i.e. are a workflow decl/impl
   * or are invalid methods).
   */
  public Map<String, List<MethodInfo>> getMethods() {
    return methods;
  }

  /** Information about a method. */
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
    // Removed after loading (if null then invalidMemberAccesses is now the
    // canonical set). May be null when loading if configuredInvalid already
    // set.
    @Nullable
    List<MethodInvalidMemberAccessInfo> memberAccesses;
    // Set after loading (but can still be null), never non-null+empty
    @Nullable
    List<MethodInvalidMemberAccessInfo> invalidMemberAccesses;

    MethodInfo(int access, String descriptor, @Nullable Boolean configuredInvalid) {
      this.access = access;
      this.descriptor = descriptor;
      this.configuredInvalid = configuredInvalid;
    }

    /** JVM access flag for the class as defined in JVM spec. */
    public int getAccess() {
      return access;
    }

    /** JVM descriptor for the method. */
    public String getDescriptor() {
      return descriptor;
    }

    /**
     * Gets whether configured invalid. This is null if not configured one way
     * or another.
     */
    @Nullable
    public Boolean getConfiguredInvalid() {
      return configuredInvalid;
    }

    /** Get workflow declaration info if this is a workflow declaration. */
    @Nullable
    public MethodWorkflowDeclInfo getWorkflowDecl() {
      return workflowDecl;
    }

    /** Get workflow implementation info if this is a workflow implementation. */
    @Nullable
    public MethodWorkflowImplInfo getWorkflowImpl() {
      return workflowImpl;
    }

    /**
     * Get all invalid members accessed within this method. This may be null if
     * {@link #getConfiguredInvalid()} is non-null which supersedes this.
     */
    @Nullable
    public List<MethodInvalidMemberAccessInfo> getInvalidMemberAccesses() {
      return invalidMemberAccesses;
    }

    /**
     * Whether this method is invalid (i.e. configured invalid or accesses
     * invalid members).
     */
    public boolean isInvalid() {
      return configuredInvalid != null ? configuredInvalid : invalidMemberAccesses != null;
    }
  }

  /** Information about a workflow method declaration. */
  public static class MethodWorkflowDeclInfo {
    final Kind kind;

    MethodWorkflowDeclInfo(Kind kind) {
      this.kind = kind;
    }

    /** Kind of workflow method. */
    public Kind getKind() {
      return kind;
    }

    /** Kinds of workflow methods. */
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

  /** Information about a workflow method implementation. */
  public static class MethodWorkflowImplInfo {
    final ClassInfo declClassInfo;
    final MethodWorkflowDeclInfo workflowDecl;

    MethodWorkflowImplInfo(ClassInfo declClassInfo, MethodWorkflowDeclInfo workflowDecl) {
      this.declClassInfo = declClassInfo;
      this.workflowDecl = workflowDecl;
    }

    /** Class information about the declaring class. */
    public ClassInfo getDeclClassInfo() {
      return declClassInfo;
    }

    /** Information about the declaration. */
    public MethodWorkflowDeclInfo getWorkflowDecl() {
      return workflowDecl;
    }
  }

  /** Information about invalid member access. */
  public static class MethodInvalidMemberAccessInfo {
    final String className;
    final String memberName;
    final String memberDescriptor;
    @Nullable
    final Integer line;
    final Operation operation;

    // Set in second phase
    @Nullable
    ClassInfo resolvedInvalidClass;
    // This is null if not a method or if the method is configured invalid
    // directly
    @Nullable
    MethodInfo resolvedInvalidMethod;

    MethodInvalidMemberAccessInfo(
            String className,
            String memberName,
            String memberDescriptor,
            @Nullable Integer line,
            Operation operation) {
      this.className = className;
      this.memberName = memberName;
      this.memberDescriptor = memberDescriptor;
      this.line = line;
      this.operation = operation;
    }

    /** Qualified class name used when accessing. */
    public String getClassName() {
      return className;
    }

    /** Member name accessed. */
    public String getMemberName() {
      return memberName;
    }

    /** Descriptor of the member (different for fields and methods). */
    public String getMemberDescriptor() {
      return memberDescriptor;
    }

    /** Line access occurred on if known. */
    @Nullable
    public Integer getLine() {
      return line;
    }

    /** Operation that makes this invalid. */
    public Operation getOperation() {
      return operation;
    }

    /**
     * Class information about the true class the invalid check occurred on if
     * it can be determined.
     */
    @Nullable
    public ClassInfo getResolvedInvalidClass() {
      return resolvedInvalidClass;
    }

    /**
     * If this invalid access is a method call, this is the resolved method
     * information if any which shows why it was invalid.
     */
    @Nullable
    public MethodInfo getResolvedInvalidMethod() {
      return resolvedInvalidMethod;
    }

    /** Invalid operations. */
    public enum Operation {
      METHOD_CALL,
      FIELD_STATIC_GET,
      FIELD_STATIC_PUT,
      FIELD_CONFIGURED_INVALID,
    }
  }
}