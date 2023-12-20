package io.temporal.workflowcheck;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Main {
  public static void main(String[] args) throws IOException {
    if (args.length == 0 || "--help".equals(args[0])) {
      System.err.println("""
            Analyze Temporal workflows for common mistakes.
            
            Usage:
              workflowcheck [command]
            
            Commands:
              check - Check all workflow code on the classpath for invalid calls
              prebuild-config - Pre-build a config for certain packages to keep from scanning each time
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
              workflowcheck check <classpath...> [--config <config-file>] [--no-default-config]
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

    // Ensure that only a single arg remains for the classpath
    if (argsList.isEmpty()) {
      System.err.println("At least one classpath argument");
      return 1;
    }

    System.err.println("Analyzing classpath for invalid workflow methods...");
    var config = Config.fromProperties(configProps.toArray(new Properties[0]));
    var infos = new WorkflowCheck(config).findInvalidWorkflowImpls(argsList.toArray(new String[0]));
    System.out.println("Found " + infos.size() + " workflow class(es) with invalid methods");
    if (infos.isEmpty()) {
      return 0;
    }

    // Print the offenders
    // TODO(cretz): Should we also print valid workflows/methods?
    for (var info : infos) {
      info.workflowMethodImpls.
              keySet().
              stream().
              filter(info.invalidMethods::containsKey).
              sorted().
              forEach(method -> System.out.println(Printer.invalidWorkflowMethodText(info, method)));
    }
    return 1;
  }

  private static int prebuildConfig(String[] args) {
    System.err.println("TODO");
    return 1;
  }

  private Main() { }
}
