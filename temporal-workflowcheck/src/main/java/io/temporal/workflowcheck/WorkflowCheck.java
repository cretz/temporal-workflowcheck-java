package io.temporal.workflowcheck;

import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.*;

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
    // Remove unimportant methods (i.e. without workflow info and are valid),
    // and remove entire list if none left
    info.methods.entrySet().removeIf(methods -> {
      methods.getValue().removeIf(method -> {
        // If the method has an impl and decl class not already trimmed, trim it
        if (method.workflowImpl != null && !done.contains(method.workflowImpl.declClassInfo)) {
          trimUnimportantClassInfo(method.workflowImpl.declClassInfo, done);
        }
        // Recursively trim classes on calls too for each not already done
        if (method.invalidCalls != null) {
          for (var call : method.invalidCalls) {
            if (call.resolvedInvalidClass != null && !done.contains(call.resolvedInvalidClass)) {
              trimUnimportantClassInfo(call.resolvedInvalidClass, done);
            }
          }
        }
        // Set to remove if nothing important on it
        return method.workflowDecl == null &&
                method.workflowImpl == null &&
                (method.configuredInvalid == null || method.configuredInvalid) &&
                method.invalidCalls == null;
      });
      return methods.getValue().isEmpty();
    });
  }

}
