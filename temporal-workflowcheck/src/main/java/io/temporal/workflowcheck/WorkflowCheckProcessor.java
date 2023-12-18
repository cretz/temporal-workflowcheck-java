package io.temporal.workflowcheck;

import standalone.com.sun.source.tree.MethodInvocationTree;
import standalone.com.sun.source.tree.MethodTree;
import standalone.com.sun.source.util.TreeScanner;
import standalone.com.sun.source.util.Trees;
import standalone.com.sun.tools.javac.code.Symbol;
import standalone.com.sun.tools.javac.code.Types;
import standalone.com.sun.tools.javac.processing.JavacProcessingEnvironment;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.Set;

@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes("*")
public class WorkflowCheckProcessor extends AbstractProcessor {
  private final String[] workflowMethodAnnotations = new String[] {
          "io.temporal.workflow.QueryMethod",
          "io.temporal.workflow.SignalMethod",
          "io.temporal.workflow.UpdateMethod",
          "io.temporal.workflow.UpdateValidatorMethod",
          "io.temporal.workflow.WorkflowMethod",
  };

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    var trees = Trees.instance(processingEnv);
    for (var elem : roundEnv.getRootElements()) {
      var treePath = trees.getPath(elem);
      treePath.getCompilationUnit().accept(new TreeScanner<Void, Void>() {
        @Override
        public Void visitMethod(MethodTree node, Void unused) {
          var elem = (Symbol.MethodSymbol) trees.getElement(trees.getPath(treePath.getCompilationUnit(), node));
          // We only visit if there is a workflow annotation on it
          if (!hasAnyAnnotationInHierarchy(elem, workflowMethodAnnotations)) {
            return null;
          }
          return super.visitMethod(node, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
          // Get method, enclosing class, and enclosing class resource
          var method = trees.getElement(trees.getPath(treePath.getCompilationUnit(), node));
          if (method.getEnclosingElement() instanceof TypeElement clazz) {
            String location;
            if (trees.getPath(clazz) != null) {
              location = "<local-code>";
            } else {
              var resourceName = processingEnv.getElementUtils().
                      getBinaryName(clazz).toString().replace('.', '/') + ".class";
              try {
                location = "<user-classpath: " + processingEnv.getFiler().getResource(
                        StandardLocation.CLASS_PATH, "", resourceName).toUri() + ">";
              } catch (IOException e) {
                var resource = getClass().getClassLoader().getResource(resourceName);
                if (resource != null) {
                  location = "<javac-classpath: " + resource + ">";
                } else {
                  location = "<unknown>";
                }
              }
              System.out.println("Calls " + method + " on " + clazz + " located at " + location);
            }
          }
          return super.visitMethodInvocation(node, unused);
        }
      }, null);
    }
    return false;
  }

  private boolean hasAnyAnnotationInHierarchy(Symbol.MethodSymbol sym, String... anns) {
    if (hasAnyAnnotation(sym, anns)) {
      return true;
    }
    // Check on super methods
    var types = Types.instance(((JavacProcessingEnvironment) processingEnv).getContext());
    return ASTHelpers.streamSuperMethods(sym, types).anyMatch(superSym -> hasAnyAnnotation(superSym, anns));
  }

  private boolean hasAnyAnnotation(Symbol.MethodSymbol sym, String... anns) {
    for (var attr : sym.getAnnotationMirrors()) {
      for (var ann : anns) {
        if (attr.type.toString().equals(ann)) {
          return true;
        }
      }
    }
    return false;
  }
}
