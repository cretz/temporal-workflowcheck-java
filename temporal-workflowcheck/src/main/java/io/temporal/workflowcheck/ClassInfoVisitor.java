package io.temporal.workflowcheck;

import org.objectweb.asm.*;

import javax.annotation.Nullable;
import java.util.*;

class ClassInfoVisitor extends ClassVisitor {
  private static final System.Logger logger = System.getLogger(ClassInfoVisitor.class.getName());

  final ClassInfo info = new ClassInfo();
  final ClassInfoLoading loading = new ClassInfoLoading();

  private final ClassInfoLoader classInfoLoader;
  private final MethodHandler methodHandler = new MethodHandler();
  // Keyed by unqualified method name + descriptor
  @Nullable
  private Map<String, ExternalWorkflowMethodDecl> workflowMethodDeclsInherited;
  @Nullable
  private SuppressionStack suppressionStack;

  ClassInfoVisitor(ClassInfoLoader classInfoLoader) {
    super(Opcodes.ASM9);
    this.classInfoLoader = classInfoLoader;
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    info.className = name;
    // Collect inherited method decls at any level
    if (!ClassPath.isStandardLibraryClass(name)) {
      if (superName != null) {
        collectWorkflowMethodDeclsInherited(superName);
      }
      if (interfaces != null) {
        for (var iface : interfaces) {
          collectWorkflowMethodDeclsInherited(iface);
        }
      }
    }
  }

  @Override
  public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
    return maybeSuppressionAttributeHandler(descriptor);
  }

  @Override
  public void visitSource(String source, String debug) {
    info.fileName = source;
  }

  @Override
  public void visitEnd() {
    // Sort the calls inside every method by their line number
    if (info.invalidMethods != null) {
      for (var meth : info.invalidMethods.values()) {
        if (meth.invalidCalls() != null) {
          meth.invalidCalls().sort(Comparator.comparingInt(c -> c.line() != null ? c.line() : -1));
        }
      }
    }
  }

  @Override
  public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
    // Check if this is a method with a body that is declared elsewhere. If it
    // is, add as an impl. Note, we are intentionally not doing a proper
    // override check here with covariant returns or generic signatures causing
    // overload difference. We believe workflow methods are not allowed to
    // differ in these ways allowing us to do a simple performant descriptor
    // equality match.
    // TODO(cretz): Add some extra check somewhere that will fail if a covariant
    // return or a generic is used on a workflow method or its override.
    var method = name + descriptor;
    if ((access & Opcodes.ACC_ABSTRACT) == 0 && (access & Opcodes.ACC_NATIVE) == 0 &&
            workflowMethodDeclsInherited != null) {
      var externalDecl = workflowMethodDeclsInherited.get(method);
      if (externalDecl != null) {
        logger.log(System.Logger.Level.DEBUG, "Found workflow method impl {0}.{1}", info.className, method);
        if (info.workflowMethodImpls == null) {
          info.workflowMethodImpls = new HashMap<>(1);
        }
        info.workflowMethodImpls.put(method, new ClassInfo.WorkflowMethodImpl(externalDecl.declClass, externalDecl.decl));
      }
    }

    // Reset and reuse the handler
    methodHandler.reset(access, method);
    return methodHandler;
  }

  private AnnotationVisitor maybeSuppressionAttributeHandler(String descriptor) {
    if (descriptor.equals("Lio/temporal/workflowcheck/WorkflowCheck$SuppressWarnings;")) {
      return new SuppressionAttributeHandler();
    }
    return null;
  }

  private void collectWorkflowMethodDeclsInherited(String className) {
    if (ClassPath.isStandardLibraryClass(className)) {
      return;
    }
    var classInfo = classInfoLoader.load(className);
    if (classInfo != null && classInfo.workflowMethodDecls != null) {
      if (workflowMethodDeclsInherited == null) {
        workflowMethodDeclsInherited = new HashMap<>(classInfo.workflowMethodDecls.size());
      }
      for (var entry : classInfo.workflowMethodDecls.entrySet()) {
        workflowMethodDeclsInherited.put(entry.getKey(), new ExternalWorkflowMethodDecl(className, entry.getValue()));
      }
    }
  }

  private void markInvalid(String callingMethod, ClassInfo.InvalidCall call) {
    // Get or create invalid method
    if (info.invalidMethods == null) {
      info.invalidMethods = new HashMap<>(1);
    }
    var invalidMethod = info.invalidMethods.computeIfAbsent(callingMethod, k ->
            new ClassInfo.InvalidMethod(new ArrayList<>(1)));
    // Add the invalid call
    invalidMethod.invalidCalls().add(call);
  }

  private class SuppressionAttributeHandler extends AnnotationVisitor {
    private final List<String> specificDescriptors = new ArrayList<>();

    SuppressionAttributeHandler() {
      super(Opcodes.ASM9);
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
      return this;
    }

    @Override
    public void visit(String name, Object value) {
      // For now there is only one annotation param possible
      if (value instanceof String string) {
        specificDescriptors.add(string);
      }
    }

    @Override
    public void visitEnd() {
      if (suppressionStack == null) {
        suppressionStack = new SuppressionStack();
      }
      suppressionStack.push(specificDescriptors.isEmpty() ? null : specificDescriptors.toArray(new String[0]));
    }
  }

  private class MethodHandler extends MethodVisitor {
    private int methodAccess;
    private String method;
    @Nullable
    private Integer methodLineNumber;
    private int methodSuppressions;
    private boolean methodSuppressionAnnotation;
    @Nullable
    private String prevInsnLdcString;

    MethodHandler() {
      super(Opcodes.ASM9);
    }

    void reset(int methodAccess, String method) {
      this.methodAccess = methodAccess;
      this.method = method;
      this.methodLineNumber = null;
      this.methodSuppressions = 0;
      this.methodSuppressionAnnotation = false;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      // Check if suppression annotation
      var suppressionVisitor = maybeSuppressionAttributeHandler(descriptor);
      if (suppressionVisitor != null) {
        methodSuppressions++;
        methodSuppressionAnnotation = true;
        return suppressionVisitor;
      }

      // If this descriptor is a known decl kind, set as a decl
      var declKind = ClassInfo.WorkflowMethodDeclKind.annotationDescriptors.get(descriptor);
      if (declKind != null) {
        logger.log(System.Logger.Level.DEBUG, "Found workflow method decl {0}.{1}", info.className, method);
        if (info.workflowMethodDecls == null) {
          info.workflowMethodDecls = new HashMap<>(1);
        }
        var decl = new ClassInfo.WorkflowMethodDecl(declKind);
        info.workflowMethodDecls.put(method, decl);
        // If the method has a body (e.g. default on interface), add it as an
        // impl of self. Otherwise, if it is an override of a parent, it will
        // be added at class visitor level.
        if ((methodAccess & Opcodes.ACC_ABSTRACT) == 0 && (methodAccess & Opcodes.ACC_NATIVE) == 0) {
          logger.log(System.Logger.Level.DEBUG, "Found workflow method impl {0}.{1}", info.className, method);
          if (info.workflowMethodImpls == null) {
            info.workflowMethodImpls = new HashMap<>(1);
          }
          info.workflowMethodImpls.put(method, new ClassInfo.WorkflowMethodImpl(info.className, decl));
        }
      }
      return null;
    }

    @Override
    public void visitLineNumber(int line, Label start) {
      methodLineNumber = line;
    }

    @Override
    public void visitEnd() {
      // If this method was marked invalid, go over every post process and set
      // as such
      if (info.invalidMethods != null && info.invalidMethods.containsKey(method)) {
        var postProcessChecks = loading.postProcessChecks.remove(method);
        if (postProcessChecks != null) {
          for (var postProcessCheck : postProcessChecks) {
            markInvalid(postProcessCheck.callingMethod,
                    new ClassInfo.InvalidCall(info.className, info, method, postProcessCheck.callingLineNumber));
          }
        }
      }
      // Mark the method as visited
      loading.methodsVisited.add(method);
      // Pop any remaining suppressions
      if (suppressionStack != null && methodSuppressions > 0) {
        for (var i = 0; i < methodSuppressions; i++) {
          suppressionStack.pop();
        }
        // Also warn if there were un-restored suppressions
        var expectedMethodSuppressions = methodSuppressionAnnotation ? 1 : 0;
        if (methodSuppressions > expectedMethodSuppressions) {
          logger.log(
                  System.Logger.Level.WARNING,
                  "{0} warning suppression(s) not restored in {1}.{2}",
                  methodSuppressions - expectedMethodSuppressions,
                  info.className,
                  method);
        }
      }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
      if (maybeSuppressMethodInsn(owner, name, descriptor)) {
        return;
      }

      // Check if configured invalid or valid
      var callMethod = name + descriptor;
      var configuredInvalid = classInfoLoader.config.invalidMethods.check(owner, name, descriptor);
      if (configuredInvalid != null) {
        if (configuredInvalid) {
          markInvalid(method, new ClassInfo.InvalidCall(owner, null, callMethod, methodLineNumber));
        }
        return;
      }

      // Load the class info and if null (excluded), go no further
      var callClassInfo = classInfoLoader.load(owner);
      if (callClassInfo == null) {
        return;
      }

      // If the info has this as an invalid method, mark as such
      if (callClassInfo.invalidMethods != null && callClassInfo.invalidMethods.containsKey(callMethod)) {
        markInvalid(method, new ClassInfo.InvalidCall(owner, callClassInfo, callMethod, methodLineNumber));
        return;
      }

      // If the method already visited, there is no more to check
      var callClassLoading = classInfoLoader.loading.get(owner);
      if (callClassLoading == null || callClassLoading.methodsVisited.contains(callMethod)) {
        return;
      }

      // The class can still be loading and not have reached this method yet,
      // so we will install a post-process check. This is a good way to solve
      // recursion while still streaming the loading.
      var postProcessChecks = callClassLoading.postProcessChecks.computeIfAbsent(
              callMethod, k -> new ArrayList<>(1));
      postProcessChecks.add(new ClassInfoLoading.PostProcessCheck(info, method, methodLineNumber));
    }

    // True if method call should not be checked for invalidity
    private boolean maybeSuppressMethodInsn(String owner, String name, String descriptor) {
      try {
        // Check if suppression call
        if ("io/temporal/workflowcheck/WorkflowCheck".equals(owner)) {
          if ("suppressWarnings".equals(name)) {
            String[] specificDescriptors = null;
            // If there's a string, it must be an LDC or we ignore
            if ("(Ljava/lang/String;)V".equals(descriptor)) {
              // TODO(cretz): Should we throw instead of warn if this is not a constant string?
              if (prevInsnLdcString == null) {
                logger.log(
                        System.Logger.Level.WARNING,
                        "WorkflowCheck.suppressWarnings call not using string literal at {0}.{1} ({2})",
                        info.className,
                        method,
                        fileLoc());
                return true;
              }
              specificDescriptors = new String[]{prevInsnLdcString};
            }
            if (suppressionStack == null) {
              suppressionStack = new SuppressionStack();
            }
            methodSuppressions++;
            suppressionStack.push(specificDescriptors);
            prevInsnLdcString = null;
            return true;
          } else if ("restoreWarnings".equals(name)) {
            if (suppressionStack != null && methodSuppressions > 0) {
              methodSuppressions--;
              suppressionStack.pop();
            } else {
              logger.log(System.Logger.Level.WARNING, "Restore with no previous suppression at {0}.{1} ({2})",
                      info.className,
                      method,
                      fileLoc());
            }
            return true;
          }
        }

        // If suppressed, don't go any further
        return suppressionStack != null && suppressionStack.checkSuppressed(owner, name, descriptor);
      } finally {
        prevInsnLdcString = null;
      }
    }

    private String fileLoc() {
      if (info.fileName == null) {
        if (methodLineNumber == null) {
          return "<unknown file:line>";
        }
        return "<unknown file>:" + methodLineNumber;
      }
      return info.fileName + ":" + (methodLineNumber == null ? "<unknown line>" : methodLineNumber);
    }

    @Override
    public void visitLdcInsn(Object value) {
      if (value instanceof String string) {
        prevInsnLdcString = string;
      } else {
        prevInsnLdcString = null;
      }
    }

    @Override
    public void visitInsn(int opcode) {
      prevInsnLdcString = null;
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
      prevInsnLdcString = null;
    }

    @Override
    public void visitVarInsn(int opcode, int varIndex) {
      prevInsnLdcString = null;
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
      prevInsnLdcString = null;
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
      prevInsnLdcString = null;
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
      prevInsnLdcString = null;
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
      prevInsnLdcString = null;
    }

    @Override
    public void visitIincInsn(int varIndex, int increment) {
      prevInsnLdcString = null;
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
      prevInsnLdcString = null;
    }
  }

  record ExternalWorkflowMethodDecl(
          String declClass,
          ClassInfo.WorkflowMethodDecl decl) {
  }
}