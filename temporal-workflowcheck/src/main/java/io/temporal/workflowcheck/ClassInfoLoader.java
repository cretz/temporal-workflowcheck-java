package io.temporal.workflowcheck;

import org.objectweb.asm.ClassReader;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

class ClassInfoLoader {
  private static final System.Logger logger = System.getLogger(ClassInfoLoader.class.getName());

  final Config config;
  final ClassLoader classLoader;

  // This can also include class infos still loading
  final Map<String, ClassInfo> infos = new HashMap<>();
  final Map<String, ClassInfoLoading> loading = new HashMap<>();

  ClassInfoLoader(Config config, ClassLoader classLoader) {
    this.config = config;
    this.classLoader = classLoader;
  }

  // TODO(cretz): Document that this may be null if excluded
  @Nullable
  ClassInfo load(String name) {
    // Check cache
    var info = infos.get(name);
    if (info != null) {
      return info;
    }
    // Always exclude module info
    if ("module-info".equals(name) || name.endsWith("/module-info")) {
      return null;
    }
    // Check other exclusions
    var excluded = config.excludedMethods.check(name, null, null);
    if (excluded != null && excluded) {
      logger.log(System.Logger.Level.TRACE, "Excluding class {0}", name);
      return null;
    }
    // Load
    var visitor = new ClassInfoVisitor(this);
    infos.put(name, visitor.info);
    loading.put(name, visitor.loading);
    try {
      logger.log(System.Logger.Level.TRACE, "Reading class {0}", name);
      try (var is = classLoader.getResourceAsStream(name + ".class")) {
        if (is == null) {
          return null;
        }
        new ClassReader(is).accept(visitor, ClassReader.SKIP_FRAMES);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      loading.remove(name);
    }
    return visitor.info;
  }
}
