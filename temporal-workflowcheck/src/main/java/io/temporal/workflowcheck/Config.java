package io.temporal.workflowcheck;

import java.io.IOException;
import java.util.Properties;

public class Config {
  public static Properties defaultProperties() throws IOException {
    var props = new Properties();
    try (var is = Config.class.getResourceAsStream("config.properties")) {
      props.load(is);
    }
    return props;
  }

  // TODO(cretz): Document later overrides earlier, but more exact overrides less exact
  public static Config fromProperties(Properties... props) {
    return new Config(new DescriptorMatcher("invalid", props), new DescriptorMatcher("excluded", props));
  }

  final DescriptorMatcher invalidMethods;
  final DescriptorMatcher excludedMethods;

  private Config(DescriptorMatcher invalidMethods, DescriptorMatcher excludedMethods) {
    this.invalidMethods = invalidMethods;
    this.excludedMethods = excludedMethods;
  }
}
