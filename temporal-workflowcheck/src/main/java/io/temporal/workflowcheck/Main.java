package io.temporal.workflowcheck;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/** Entrypoint for CLI. */
public class Main {
  public static void main(String[] args) throws IOException {
    if (args.length == 0 || "--help".equals(args[0])) {
      System.err.println("""
            Analyze Temporal workflows for common mistakes.
            
            Usage:
              workflowcheck [command]
            
            Commands:
              check - Check all workflow code on the classpath for invalid calls
              prebuild-config - Pre-build a config for certain packages to keep from scanning each time (TODO)
            """);
      return;
    }
    switch (args[0]) {
      case "check":
        System.exit(check(Arrays.copyOfRange(args, 1, args.length)));
      case "prebuild-config":
        System.exit(prebuildConfig(Arrays.copyOfRange(args, 1, args.length)));
      default:
        System.err.println("Unrecognized command '" + args[0] + "'");
        System.exit(1);
    }
  }

  private static int check(String[] args) throws IOException {
    if (args.length == 1 && "--help".equals(args[0])) {
      System.err.println("""
            Analyze Temporal workflows for common mistakes.
            
            Usage:
              workflowcheck check <classpath...> [--config <config-file>] [--no-default-config] [--show-valid]
            """);
      return 0;
    }
    // Args list that removes options as encountered
    var argsList = new ArrayList<>(List.of(args));

    // Load config
    var configProps = new ArrayList<Properties>();
    if (!argsList.remove("--no-default-config")) {
      configProps.add(Config.defaultProperties());
    }
    while (true) {
      var configIndex = argsList.indexOf("--config");
      if (configIndex == -1) {
        break;
      } else if (configIndex == argsList.size() - 1) {
        System.err.println("Missing --config value");
        return 1;
      }
      argsList.remove(configIndex);
      var props = new Properties();
      try (var is = new FileInputStream(argsList.remove(configIndex))) {
        props.load(is);
      }
      configProps.add(props);
    }

    // Whether we should also show valid
    var showValid = argsList.remove("--show-valid");

    // Ensure that we have at least one classpath arg
    if (argsList.isEmpty()) {
      System.err.println("At least one classpath argument required");
      return 1;
    }
    // While it can rarely be possible for the first file in a class path string
    // to start with a dash, we're going to assume it's an invalid argument and
    // users can qualify if needed.
    var invalidArg = argsList.stream().filter(s -> s.startsWith("-")).findFirst();
    if (invalidArg.isPresent()) {
      System.err.println("Unrecognized argument: " + invalidArg);
    }

    System.err.println("Analyzing classpath for classes with workflow methods...");
    var config = Config.fromProperties(configProps.toArray(new Properties[0]));
    var infos = new WorkflowCheck(config).findWorkflowClasses(argsList.toArray(new String[0]));
    System.out.println("Found " + infos.size() + " class(es) with workflow methods");
    if (infos.isEmpty()) {
      return 0;
    }

    // Print workflow methods impls
    var anyInvalidImpls = false;
    for (var info : infos) {
      for (var methods : info.methods.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
        for (var method : methods.getValue()) {
          // Only impls
          if (method.workflowImpl == null) {
            continue;
          }
          if (showValid || method.isInvalid()) {
            System.out.println(Printer.methodText(info, methods.getKey(), method));
          }
          if (method.isInvalid()) {
            anyInvalidImpls = true;
          }
        }
      }
    }
    return anyInvalidImpls ? 1 : 0;
  }

  private static int prebuildConfig(String[] args) {
    System.err.println("TODO");
    return 1;
  }

  private Main() { }
}
