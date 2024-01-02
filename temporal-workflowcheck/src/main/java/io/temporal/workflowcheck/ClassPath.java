package io.temporal.workflowcheck;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Classpath helpers for a class loader to get all classes.
 */
class ClassPath implements AutoCloseable {
  static boolean isStandardLibraryClass(String name) {
    return name.startsWith("java/") ||
            name.startsWith("javax/") ||
            name.startsWith("jdk/") ||
            name.startsWith("com/sun/");
  }

  final URLClassLoader classLoader;
  // Non-standard-library classes only here
  final List<String> classes = new ArrayList<>();

  ClassPath(String... classPaths) throws IOException {
    var urls = new ArrayList<URL>();
    for (var classPath : classPaths) {
      // If there is an `@` sign starting the classPath, instead read from a file
      if (classPath.startsWith("@")) {
        classPath = Files.readString(Paths.get(classPath.substring(1))).trim();
      }
      // Split and handle each entry
      for (var entry : classPath.split(File.pathSeparator)) {
        var file = new File(entry);
        // Like javac and others, we just ignore non-existing entries
        if (file.exists()) {
          if (file.isDirectory()) {
            urls.add(file.toURI().toURL());
            findClassesInDir("", file, classes);
          } else if (entry.endsWith(".jar")) {
            urls.add(new URL("jar", "", "file:/" + file.getAbsoluteFile() + "!/"));
            findClassesInJar(file, classes);
          }
        }
      }
    }
    classLoader = new URLClassLoader(urls.toArray(new URL[0]));
    // Sort the classes to loaded in a deterministic order
    classes.sort(String::compareTo);
  }

  private static void findClassesInDir(String path, File dir, List<String> classes) {
    var files = dir.listFiles();
    if (files == null) {
      return;
    }
    for (var file : files) {
      if (file.isDirectory()) {
        findClassesInDir(path + file.getName() + "/", file, classes);
      } else if (file.getName().endsWith(".class")) {
        addClass(path + file.getName(), classes);
      }
    }
  }

  private static void findClassesInJar(File jar, List<String> classes) throws IOException {
    try (var jarFile = new JarFile(jar)) {
      var entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        if (entry.getName().endsWith(".class")) {
          addClass(entry.getName(), classes);
        }
      }
    }
  }

  private static void addClass(String fullPath, List<String> classes) {
    // Trim off trailing .class
    var className = fullPath.substring(0, fullPath.length() - 6);
    // Only if not built in
    if (!isStandardLibraryClass(className)) {
      classes.add(className);
    }
  }

  @Override
  public void close() throws IOException {
    classLoader.close();
  }
}
