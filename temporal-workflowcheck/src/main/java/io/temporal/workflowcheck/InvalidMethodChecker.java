package io.temporal.workflowcheck;

import sootup.core.model.SootMethod;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.ClassType;
import sootup.java.bytecode.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.bytecode.inputlocation.JrtFileSystemAnalysisInputLocation;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;
import sootup.java.core.views.JavaView;

import java.util.*;
import java.util.stream.Stream;

public class InvalidMethodChecker {

  private static final Set<String> workflowMethodAnnotations = Set.of(
          "QueryMethod", "SignalMethod", "UpdateMethod", "UpdateValidatorMethod", "WorkflowMethod");

  private final JavaView view;

  public InvalidMethodChecker(String classPath) {
    // TODO(cretz): This is too big for common Java heap space, figure out a cache-based solution
    // view = new JavaView(List.of(new JavaClassPathAnalysisInputLocation(classPath), new JrtFileSystemAnalysisInputLocation()));
    view = new JavaView(List.of(new JavaClassPathAnalysisInputLocation(classPath)));
  }

  public List<InvalidMethod> loadInvalidMethods() {
    // Collect method impls
    for (var m : collectWorkflowMethodImpls()) {
      System.out.println("Workflow method: " + m.impl);
    }
    throw new UnsupportedOperationException();
  }

  private List<WorkflowMethodImpl> collectWorkflowMethodImpls() {
    var impls = new ArrayList<WorkflowMethodImpl>();
    var implsCache = new HashMap<JavaSootClass, Boolean>();
    var declCache = new HashMap<JavaSootClass, Set<JavaSootMethod>>();
    for (var cls : view.getClasses()) {
      if (cls.isJavaLibraryClass() || !implsWorkflow(cls, implsCache)) {
        continue;
      }
      // Check if any direct methods impl any of the decls
      for (var decl : collectWorkflowMethodDecls(cls, declCache)) {
        for (var impl : cls.getMethods()) {
          if (impl.hasBody() && impl.getSignature().getSubSignature().equals(decl.getSignature().getSubSignature())) {
            impls.add(new WorkflowMethodImpl(decl, impl));
            break;
          }
        }
      }
    }
    // Sort the list by qualified signature of impls
    impls.sort(Comparator.comparing(m -> m.impl.getSignature().toString()));
    return impls;
  }

  private boolean implsWorkflow(JavaSootClass cls, Map<JavaSootClass, Boolean> cache) {
    // Check cache (not using compute if absent due to recursion)
    var cached = cache.get(cls);
    if (cached != null) {
      return cached;
    }
    cache.put(cls, false);
    // Self
    for (var ann : cls.getAnnotations(Optional.of(view))) {
      if ("io.temporal.workflow".equals(ann.getAnnotation().getPackageName().getName()) &&
              "WorkflowInterface".equals(ann.getAnnotation().getClassName())) {
        cache.put(cls, true);
        return true;
      }
    }
    // Super
    if (cls.getSuperclass().isPresent() && !cls.getSuperclass().get().isBuiltInClass()) {
      var superClass = view.getClass(cls.getSuperclass().get());
      if (superClass.isPresent() && implsWorkflow(superClass.get(), cache)) {
        cache.put(cls, true);
        return true;
      }
    }
    // Interface
    for (var iface : cls.getInterfaces()) {
      if (iface.isBuiltInClass()) {
        continue;
      }
      var ifaceClass = view.getClass(iface);
      if (ifaceClass.isPresent() && implsWorkflow(ifaceClass.get(), cache)) {
        cache.put(cls, true);
        return true;
      }
    }
    return false;
  }

  private Set<JavaSootMethod> collectWorkflowMethodDecls(JavaSootClass cls, Map<JavaSootClass, Set<JavaSootMethod>> cache) {
    // Check cache (not using compute if absent due to recursion)
    var cached = cache.get(cls);
    if (cached != null) {
      return cached;
    }
    cache.put(cls, Collections.emptySet());

    // Build up the set. We intentionally don't use streams here for performance reasons
    Set<JavaSootMethod> set = null;
    // Self
    for (var method : cls.getMethods()) {
      if (!method.isBuiltInMethod() && isWorkflowMethodDecl(method)) {
        if (set == null) {
          set = new HashSet<>();
        }
        set.add(method);
      }
    }
    // Super
    if (cls.getSuperclass().isPresent() && !cls.getSuperclass().get().isBuiltInClass()) {
      var superClass = view.getClass(cls.getSuperclass().get());
      if (superClass.isPresent()) {
        var other = collectWorkflowMethodDecls(superClass.get(), cache);
        if (!other.isEmpty()) {
          if (set == null) {
            set = new HashSet<>();
          }
          set.addAll(other);
        }
      }
    }
    // Interface
    for (var iface : cls.getInterfaces()) {
      if (iface.isBuiltInClass()) {
        continue;
      }
      var ifaceClass = view.getClass(iface);
      if (ifaceClass.isPresent()) {
        var other = collectWorkflowMethodDecls(ifaceClass.get(), cache);
        if (!other.isEmpty()) {
          if (set == null) {
            set = new HashSet<>();
          }
          set.addAll(other);
        }
      }
    }

    // Add to cache if non-null
    if (set != null) {
      cache.put(cls, set);
      return set;
    }
    return Collections.emptySet();
  }

  private boolean isWorkflowMethodDecl(JavaSootMethod method) {
    for (var ann : method.getAnnotations(Optional.of(view))) {
      if ("io.temporal.workflow".equals(ann.getAnnotation().getPackageName().getName()) &&
              workflowMethodAnnotations.contains(ann.getAnnotation().getClassName())) {
        return true;
      }
    }
    return false;
  }

  // Impl guaranteed to have body, but could be the same as decl if it's a default of an interface
  record WorkflowMethodImpl(JavaSootMethod decl, JavaSootMethod impl) {
  }
}
