package io.temporal.workflowcheck;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

class Loader {
  private final Config config;
  private final ClassPath classPath;
  private final Map<String, ClassInfo> classes = new HashMap<>();

  Loader(Config config, ClassPath classPath) {
    this.config = config;
    this.classPath = classPath;
  }

  ClassInfo loadClass(String className) {
    return classes.computeIfAbsent(className, v -> {
      try (var is = classPath.classLoader.getResourceAsStream(className + ".class")) {
        if (is == null) {
          // We are going to just make a dummy when we can't find a class
          // TODO(cretz): Warn?
          var info = new ClassInfo();
          info.access = Opcodes.ACC_SYNTHETIC;
          info.name = className;
          return info;
        }
        var visitor = new ClassInfoVisitor(config);
        new ClassReader(is).accept(visitor, ClassReader.SKIP_FRAMES);
        return visitor.classInfo;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Nullable
  ClassInfo.MethodWorkflowImplInfo findWorkflowImplInfo(
          ClassInfo on, String implClassName, String implMethodName, String implMethodDescriptor) {
    // Check my own methods
    var methods = on.methods.get(implMethodName);
    if (methods != null) {
      for (var method : methods) {
        if (method.workflowDecl != null && isMethodOverride(on, method, implClassName, implMethodDescriptor)) {
          return new ClassInfo.MethodWorkflowImplInfo(on, method.workflowDecl);
        }
      }
    }
    // Check super class then super interfaces (we don't care about the
    // potential duplicate checks, better than maintaining an already-seen map)
    if (on.superClass != null && !ClassPath.isStandardLibraryClass(on.superClass)) {
      var info = findWorkflowImplInfo(loadClass(on.superClass), implClassName, implMethodName, implMethodDescriptor);
      if (info != null) {
        return info;
      }
    }
    if (on.superInterfaces != null) {
      for (var iface : on.superInterfaces) {
        if (!ClassPath.isStandardLibraryClass(iface)) {
          var info = findWorkflowImplInfo(loadClass(iface), implClassName, implMethodName, implMethodDescriptor);
          if (info != null) {
            return info;
          }
        }
      }
    }
    return null;
  }

  void processMethodValidity(ClassInfo.MethodInfo method, Set<ClassInfo.MethodInfo> processing) {
    // If it has no calls (possibly actually has no calls or just has
    // configured-invalid already set) or already processed, do nothing. This
    // of course means that recursion does not apply for invalidity.
    if (method.calls == null || processing.contains(method)) {
      return;
    }
    // Go over every call and check whether invalid
    processing.add(method);
    for (var call : method.calls) {
      // We need to resolve the called method. A call is considered
      // invalid/valid if:
      // * Configured invalid set in the hierarchy (most-specific wins)
      // * Actual impl of the method has invalid calls
      var callClass = loadClass(call.className);

      var configResolution = new ConfiguredInvalidResolution();
      resolveConfiguredInvalid(callClass, call.methodName, call.methodDescriptor, 0, configResolution);
      if (configResolution.value != null) {
        if (configResolution.value) {
          call.resolvedInvalidClass = configResolution.classFoundOn;
          if (method.invalidCalls == null) {
            method.invalidCalls = new ArrayList<>(1);
          }
          method.invalidCalls.add(call);
        }
        continue;
      }

      var methodResolution = new MethodResolution();
      resolveMethod(loadClass(call.className),
              call.className, call.methodName, call.methodDescriptor, methodResolution);
      if (methodResolution.implClass != null) {
        // Process invalidity on this method, then check if it's invalid
        processMethodValidity(methodResolution.implMethod, processing);
        if (methodResolution.implMethod.isInvalid()) {
          call.resolvedInvalidClass = methodResolution.implClass;
          call.resolvedInvalidMethod = methodResolution.implMethod;
          if (method.invalidCalls == null) {
            method.invalidCalls = new ArrayList<>(1);
          }
          method.invalidCalls.add(call);
        }
      }
    }
    // Unset the calls now that we've processed them
    method.calls = null;
    // Sort invalid calls if there are any
    if (method.invalidCalls != null) {
      method.invalidCalls.sort(Comparator.comparingInt(m -> m.line == null ? -1 : m.line));
    }
    processing.remove(method);
  }

  private static class ConfiguredInvalidResolution {
    private ClassInfo classFoundOn;
    private int depthFoundOn;
    private Boolean value;
  }

  private void resolveConfiguredInvalid(
          ClassInfo on,
          String methodName,
          String methodDescriptor,
          int depth,
          ConfiguredInvalidResolution resolution) {
    // First check myself
    var configuredInvalid = config.invalidMethods.check(on.name, methodName, methodDescriptor);
    if (configuredInvalid != null && isMoreSpecific(resolution.classFoundOn, resolution.depthFoundOn, on, depth)) {
      resolution.classFoundOn = on;
      resolution.depthFoundOn = depth;
      resolution.value = configuredInvalid;
    }

    // Now check super class and super interfaces. We don't care enough to
    // prevent re-checking diamonds.
    if (on.superClass != null) {
      resolveConfiguredInvalid(
              loadClass(on.superClass),
              methodName,
              methodDescriptor,
              depth + 1,
              resolution);
    }
    if (on.superInterfaces != null) {
      for (var iface : on.superInterfaces) {
        resolveConfiguredInvalid(
                loadClass(iface),
                methodName,
                methodDescriptor,
                depth + 1,
                resolution);
      }
    }
  }

  private static class MethodResolution {
    ClassInfo implClass;
    ClassInfo.MethodInfo implMethod;
  }

  private void resolveMethod(
          ClassInfo on,
          String callClassName,
          String callMethodName,
          String callMethodDescriptor,
          MethodResolution resolution) {
    // First, see if the method is even on this class
    var methods = on.methods.get(callMethodName);
    if (methods != null) {
      for (var method : methods) {
        // Only methods with bodies apply
        if ((method.access & Opcodes.ACC_ABSTRACT) != 0 || (method.access & Opcodes.ACC_NATIVE) != 0) {
          continue;
        }
        // To qualify, method descriptor must match if same call class name, or
        // method must be an override if different call class name
        if ((callClassName.equals(on.name) && method.descriptor.equals(callMethodDescriptor)) ||
                isMethodOverride(on, method, callClassName, callMethodDescriptor)) {
          // If we have a body and impl hasn't been sent, this is the impl.
          // Otherwise, we have to check whether it's more specific. Depth does
          // not matter because Java compiler won't allow ambiguity here (i.e.
          // multiple unrelated interface defaults).
          if (isMoreSpecific(resolution.implClass, 0, on, 0)) {
            resolution.implClass = on;
            resolution.implMethod = method;
            // If this is not an interface, we're done trying to find others
            if ((method.access & Opcodes.ACC_INTERFACE) == 0) {
              return;
            }
          }
          break;
        }
      }
    }

    // Now check super class and super interfaces. We don't care enough to
    // prevent re-checking diamonds.
    if (on.superClass != null) {
      resolveMethod(
              loadClass(on.superClass),
              callClassName, callMethodName, callMethodDescriptor,
              resolution);
    }
    if (on.superInterfaces != null) {
      for (var iface : on.superInterfaces) {
        resolveMethod(
                loadClass(iface),
                callClassName, callMethodName, callMethodDescriptor,
                resolution);
      }
    }
  }

  private boolean isMoreSpecific(
          @Nullable ClassInfo prevClass,
          int prevDepth,
          ClassInfo newClass,
          int newDepth) {
    // If there is no prev, this is always more specific
    if (prevClass == null) {
      return true;
    }

    // If the prev class is not an interface, it is always more specific, then
    // apply that logic to new over any interface that may have been seen
    if ((prevClass.access & Opcodes.ACC_INTERFACE) == 0) {
      return false;
    } else if ((newClass.access & Opcodes.ACC_INTERFACE) == 0) {
      return true;
    }

    // Now that we know they are both interfaces, if the new class is a
    // sub-interface of the prev class, it is more specific. For default-method
    // resolution purposes, Java would disallow two independent implementations
    // of the same default method on independent interfaces. But this isn't for
    // default purposes, so there can be multiple. In this rare case, we will
    // choose which has the least depth, and in the rarer case they are the
    // same depth, we just leave previous.
    if (isAssignableFrom(prevClass.name, newClass)) {
      return true;
    } else if (!isAssignableFrom(newClass.name, prevClass)) {
      return false;
    }
    return newDepth < prevDepth;
  }

  // Expects name check to already be done
  private boolean isMethodOverride(
          ClassInfo superClass,
          ClassInfo.MethodInfo superMethod,
          // If null, package-private not verified
          @Nullable String subClassName,
          String subMethodDescriptor) {
    // Final, static, or private are never inherited
    var superAccess = superMethod.access;
    if ((superAccess & Opcodes.ACC_FINAL) != 0 ||
            (superAccess & Opcodes.ACC_STATIC) != 0 ||
            (superAccess & Opcodes.ACC_PRIVATE) != 0) {
      return false;
    }
    // Package-private only inherited if same package
    if (subClassName != null &&
            (superAccess & Opcodes.ACC_PUBLIC) == 0 &&
            (superAccess & Opcodes.ACC_PROTECTED) == 0) {
      var slashIndex = superClass.name.lastIndexOf('/');
      if (slashIndex == 0 || !subClassName.startsWith(superClass.name.substring(0, slashIndex + 1))) {
        return false;
      }
    }
    // Check descriptor. This can have a covariant return, so this must check
    // exact args first then return covariance.
    var superDesc = superMethod.descriptor;
    // Simple equality perf shortcut
    if (superDesc.equals(subMethodDescriptor)) {
      return true;
    }
    // Since it didn't match exact, check up to end paren if both have ")L"
    var endParen = superDesc.lastIndexOf(')');
    if (endParen >= subMethodDescriptor.length() ||
            subMethodDescriptor.charAt(endParen) != ')' ||
            superDesc.charAt(endParen + 1) != 'L' ||
            subMethodDescriptor.charAt(endParen + 1) != 'L') {
      return false;
    }
    // Check args
    if (!subMethodDescriptor.regionMatches(0, superMethod.descriptor, 0, endParen + 1)) {
      return false;
    }
    // Check super return is same or super of sub return (after 'L', before end ';')
    return isAssignableFrom(
            superMethod.descriptor.substring(endParen + 2, superMethod.descriptor.length() - 1),
            subMethodDescriptor.substring(endParen + 2, subMethodDescriptor.length() - 1));
  }

  private boolean isAssignableFrom(String sameOrSuperOfSubject, String subject) {
    if (sameOrSuperOfSubject.equals(subject)) {
      return true;
    }
    return isAssignableFrom(sameOrSuperOfSubject, loadClass(subject));
  }

  private boolean isAssignableFrom(String sameOrSuperOfSubject, ClassInfo subject) {
    if (sameOrSuperOfSubject.equals(subject.name)) {
      return true;
    }
    if (sameOrSuperOfSubject.equals(subject.superClass)) {
      return true;
    }
    if (subject.superInterfaces != null) {
      for (var iface : subject.superInterfaces) {
        if (sameOrSuperOfSubject.equals(iface)) {
          return true;
        }
      }
    }
    // Since there were no direct matches, now check if subject super classes
    // or interfaces match
    if (subject.superClass != null) {
      if (isAssignableFrom(sameOrSuperOfSubject, loadClass(subject.superClass))) {
        return true;
      }
    }
    if (subject.superInterfaces != null) {
      for (var iface : subject.superInterfaces) {
        if (isAssignableFrom(sameOrSuperOfSubject, loadClass(iface))) {
          return true;
        }
      }
    }
    return false;
  }
}
