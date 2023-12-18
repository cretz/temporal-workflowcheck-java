package io.temporal.workflowcheck;

// TODO(cretz): Copied from ErrorProne, add copyright and such...

import standalone.com.sun.source.tree.*;
import standalone.com.sun.tools.javac.code.Scope;
import standalone.com.sun.tools.javac.code.*;
import standalone.com.sun.tools.javac.tree.JCTree;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toCollection;

public class ASTHelpers {


  /**
   * Gets the symbol declared by a tree. Returns null if {@code tree} does not declare a symbol or
   * is null.
   */
  public static Symbol getDeclaredSymbol(Tree tree) {
    if (tree instanceof PackageTree) {
      return getSymbol((PackageTree) tree);
    }
    if (tree instanceof TypeParameterTree) {
      Type type = ((JCTree.JCTypeParameter) tree).type;
      return type == null ? null : type.tsym;
    }
    if (tree instanceof ClassTree) {
      return getSymbol((ClassTree) tree);
    }
    if (tree instanceof MethodTree) {
      return getSymbol((MethodTree) tree);
    }
    if (tree instanceof VariableTree) {
      return getSymbol((VariableTree) tree);
    }
    return null;
  }

  /**
   * Gets the symbol for a tree. Returns null if this tree does not have a symbol because it is of
   * the wrong type, if {@code tree} is null, or if the symbol cannot be found due to a compilation
   * error.
   */
  public static Symbol getSymbol(Tree tree) {
    if (tree instanceof AnnotationTree) {
      return getSymbol(((AnnotationTree) tree).getAnnotationType());
    }
    if (tree instanceof JCTree.JCFieldAccess) {
      return ((JCTree.JCFieldAccess) tree).sym;
    }
    if (tree instanceof JCTree.JCIdent) {
      return ((JCTree.JCIdent) tree).sym;
    }
    if (tree instanceof JCTree.JCMethodInvocation) {
      return ASTHelpers.getSymbol((MethodInvocationTree) tree);
    }
    if (tree instanceof JCTree.JCNewClass) {
      return ASTHelpers.getSymbol((NewClassTree) tree);
    }
    if (tree instanceof MemberReferenceTree) {
      return ((JCTree.JCMemberReference) tree).sym;
    }
    if (tree instanceof JCTree.JCAnnotatedType) {
      return getSymbol(((JCTree.JCAnnotatedType) tree).underlyingType);
    }
    if (tree instanceof ParameterizedTypeTree) {
      return getSymbol(((ParameterizedTypeTree) tree).getType());
    }
    if (tree instanceof ClassTree) {
      return getSymbol((ClassTree) tree);
    }

    return getDeclaredSymbol(tree);
  }

  /** Gets the symbol for a class. */
  public static Symbol.ClassSymbol getSymbol(ClassTree tree) {
    return requireNonNull(((JCTree.JCClassDecl) tree).sym);
  }

  /** Gets the symbol for a package. */
  public static Symbol.PackageSymbol getSymbol(PackageTree tree) {
    return requireNonNull(((JCTree.JCPackageDecl) tree).packge);
  }

  /** Gets the symbol for a method. */
  public static Symbol.MethodSymbol getSymbol(MethodTree tree) {
    return requireNonNull(((JCTree.JCMethodDecl) tree).sym);
  }

  /** Gets the method symbol for a new class. */
  public static Symbol.MethodSymbol getSymbol(NewClassTree tree) {
    Symbol sym = ((JCTree.JCNewClass) tree).constructor;
    if (!(sym instanceof Symbol.MethodSymbol)) {
      // Defensive. Would only occur if there are errors in the AST.
      throw new IllegalArgumentException(tree.toString());
    }
    return (Symbol.MethodSymbol) sym;
  }

  /** Gets the symbol for a variable. */
  public static Symbol.VarSymbol getSymbol(VariableTree tree) {
    return requireNonNull(((JCTree.JCVariableDecl) tree).sym);
  }

  /** Gets the symbol for a method invocation. */
  public static Symbol.MethodSymbol getSymbol(MethodInvocationTree tree) {
    Symbol sym = ASTHelpers.getSymbol(tree.getMethodSelect());
    if (!(sym instanceof Symbol.MethodSymbol)) {
      // Defensive. Would only occur if there are errors in the AST.
      throw new IllegalArgumentException(tree.toString());
    }
    return (Symbol.MethodSymbol) sym;
  }

  /** Gets the symbol for a member reference. */
  public static Symbol.MethodSymbol getSymbol(MemberReferenceTree tree) {
    Symbol sym = ((JCTree.JCMemberReference) tree).sym;
    if (!(sym instanceof Symbol.MethodSymbol)) {
      // Defensive. Would only occur if there are errors in the AST.
      throw new IllegalArgumentException(tree.toString());
    }
    return (Symbol.MethodSymbol) sym;
  }

  public static Symbol.MethodSymbol findSuperMethodInType(
          Symbol.MethodSymbol methodSymbol, Type superType, Types types) {
    if (methodSymbol.isStatic() || superType.equals(methodSymbol.owner.type)) {
      return null;
    }

    Scope scope = superType.tsym.members();
    for (Symbol sym : scope.getSymbolsByName(methodSymbol.name)) {
      if (sym != null
              && !isStatic(sym)
              && ((sym.flags() & Flags.SYNTHETIC) == 0)
              && methodSymbol.overrides(
              sym, (Symbol.TypeSymbol) methodSymbol.owner, types, /* checkResult= */ true)) {
        return (Symbol.MethodSymbol) sym;
      }
    }
    return null;
  }

  /**
   * Finds supermethods of {@code methodSymbol}, not including {@code methodSymbol} itself, and
   * including interfaces.
   */
  public static Set<Symbol.MethodSymbol> findSuperMethods(Symbol.MethodSymbol methodSymbol, Types types) {
    return findSuperMethods(methodSymbol, types, /* skipInterfaces= */ false)
            .collect(toCollection(LinkedHashSet::new));
  }

  /** See {@link #findSuperMethods(Symbol.MethodSymbol, Types)}. */
  public static Stream<Symbol.MethodSymbol> streamSuperMethods(Symbol.MethodSymbol methodSymbol, Types types) {
    return findSuperMethods(methodSymbol, types, /* skipInterfaces= */ false);
  }

  private static Stream<Symbol.MethodSymbol> findSuperMethods(
          Symbol.MethodSymbol methodSymbol, Types types, boolean skipInterfaces) {
    Symbol.TypeSymbol owner = (Symbol.TypeSymbol) methodSymbol.owner;
    Stream<Type> typeStream = types.closure(owner.type).stream();
    if (skipInterfaces) {
      typeStream = typeStream.filter(type -> !type.isInterface());
    }
    return typeStream
            .map(type -> findSuperMethodInType(methodSymbol, type, types))
            .filter(Objects::nonNull);
  }

  /**
   * Finds (if it exists) first (in the class hierarchy) non-interface super method of given {@code
   * method}.
   */
  public static Optional<Symbol.MethodSymbol> findSuperMethod(Symbol.MethodSymbol methodSymbol, Types types) {
    return findSuperMethods(methodSymbol, types, /* skipInterfaces= */ true).findFirst();
  }

  /** Returns true if the symbol is static. Returns {@code false} for module symbols. */
  public static boolean isStatic(Symbol symbol) {
    switch (symbol.getKind()) {
      case MODULE:
        return false;
      default:
        return symbol.isStatic();
    }
  }

  private ASTHelpers() {}
}
