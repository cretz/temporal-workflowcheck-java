package io.temporal.workflowcheck;

import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.*;

/**
 * Utilities to help validate workflow correctness.
 */
public class WorkflowCheck {
  /**
   * Suppress all invalid-workflow warnings until the matching call to
   * {@link #restoreWarnings()}. This must be accompanied by a closing
   * {@link #restoreWarnings()}. A more specific form of this that suppresses
   * only certain warnings is at {@link #suppressWarnings(String)}. Note, this
   * does not respect logical order, but rather bytecode order. Users are
   * encouraged to use the {@link SuppressWarnings} annotation instead.
   */
  public static void suppressWarnings() {}

  /**
   * Suppress invalid-workflow warnings that apply to this descriptor until the
   * matching call to {@link #restoreWarnings()}. This must be accompanied by a
   * closing {@link #restoreWarnings()}. A more generic form of this that
   * suppresses only certain warnings is at {@link #suppressWarnings()}. Note,
   * this does not respect logical order, but rather bytecode order. Users are
   * encouraged to use the {@link SuppressWarnings} annotation instead.
   */
  public static void suppressWarnings(String specificDesc) {}

  /** Restore warnings suppressed via suppressWarnings calls. */
  public static void restoreWarnings() { }

  /**
   * Suppress warnings on the class or method this is put on. If
   * <c>invalidMembers</c> is provided, this only suppresses those specific
   * descriptors. Otherwise this suppresses all.
   */
  @Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
  public @interface SuppressWarnings {
    // Note, intentionally not called "value" for the default because there may
    // be other warnings to suppress in the future
    /**
     * Descriptors for invalid members to suppress. If empty/unset, this
     * suppresses all.
     */
    String[] invalidMembers() default {};
  }

  private final Config config;

  /** Create a new workflow check with the given config. */
  public WorkflowCheck(Config config) {
    this.config = config;
  }

  /**
   * Scan the given classpaths finding all classes with workflow implementation
   * methods, and check them for validity. This returns all classes that have
   * at least one method whose {@link ClassInfo.MethodInfo#getWorkflowImpl()}
   * is non-null.
   */
  public List<ClassInfo> findWorkflowClasses(String... classPaths) throws IOException {
    // Load all non-built-in classes' methods to find workflow impls
    var workflowClasses = new ArrayList<ClassInfo>();
    try (var classPath = new ClassPath(classPaths)) {
      var loader = new Loader(config, classPath);
      for (String className : classPath.classes) {
        var info = loader.loadClass(className);
        var hasWorkflowImpl = false;
        for (var methodEntry : info.methods.entrySet()) {
          for (var method : methodEntry.getValue()) {
            // Workflow impl method must be non-static public with a body
            if ((method.access & Opcodes.ACC_STATIC) == 0 &&
                    (method.access & Opcodes.ACC_PUBLIC) != 0 &&
                    (method.access & Opcodes.ACC_ABSTRACT) == 0 &&
                    (method.access & Opcodes.ACC_NATIVE) == 0) {
              method.workflowImpl = loader.findWorkflowImplInfo(
                      info, info.name, methodEntry.getKey(), method.descriptor);
              // We need to check for method validity only if it's an impl
              if (method.workflowImpl != null) {
                hasWorkflowImpl = true;
                loader.processMethodValidity(method, Collections.newSetFromMap(new IdentityHashMap<>()));
              }
            }
          }
        }
        if (hasWorkflowImpl) {
          workflowClasses.add(info);
        }
      }
    }

    // Now that we have processed all invalidity on each class, trim off
    // unimportant class pieces
    var trimmed = Collections.<ClassInfo>newSetFromMap(new IdentityHashMap<>());
    workflowClasses.forEach(info -> trimUnimportantClassInfo(info, trimmed));

    // Sort classes by class name and return
    workflowClasses.sort(Comparator.comparing(c -> c.name));
    return workflowClasses;
  }

  private void trimUnimportantClassInfo(ClassInfo info, Set<ClassInfo> done) {
    done.add(info);
    // Remove non-final static fields, they are only needed during processing
    info.nonFinalStaticFields = null;
    // Remove unimportant methods (i.e. without workflow info and are valid),
    // and remove entire list if none left
    info.methods.entrySet().removeIf(methods -> {
      methods.getValue().removeIf(method -> {
        // If the method has an impl and decl class not already trimmed, trim it
        if (method.workflowImpl != null && !done.contains(method.workflowImpl.declClassInfo)) {
          trimUnimportantClassInfo(method.workflowImpl.declClassInfo, done);
        }
        // Recursively trim classes on calls too for each not already done
        if (method.invalidMemberAccesses != null) {
          for (var access : method.invalidMemberAccesses) {
            if (access.resolvedInvalidClass != null && !done.contains(access.resolvedInvalidClass)) {
              trimUnimportantClassInfo(access.resolvedInvalidClass, done);
            }
          }
        }
        // Set to remove if nothing important on it
        return method.workflowDecl == null &&
                method.workflowImpl == null &&
                (method.configuredInvalid == null || method.configuredInvalid) &&
                method.invalidMemberAccesses == null;
      });
      return methods.getValue().isEmpty();
    });
  }

}
