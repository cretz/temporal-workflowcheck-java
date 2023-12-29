package io.temporal.workflowcheck;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Pattern is <code>[[qualified/class/]Name.]methodName[(Lthe/Desc;)V]</code>.
 */
class DescriptorMatcher {
  private final Map<String, Boolean> descriptors;

  DescriptorMatcher() {
    this(new HashMap<>());
  }

  DescriptorMatcher(Map<String, Boolean> descriptors) {
    this.descriptors = descriptors;
  }

  DescriptorMatcher(String category, Properties[] propSets) {
    this(new HashMap<>());
    for (var props : propSets) {
      addFromProperties(category, props);
    }
  }

  DescriptorMatcher(String[] positiveMatches) {
    this(new HashMap<>(positiveMatches.length));
    for (var positiveMatch : positiveMatches) {
      descriptors.put(positiveMatch, true);
    }
  }

  void addFromProperties(String category, Properties props) {
    var prefix = "temporal.workflowcheck." + category + ".";
    for (var entry : props.entrySet()) {
      // Key is temporal.workflowcheck.<category>.<desc-sans-return>=<true|false>
      var key = (String) entry.getKey();
      if (!key.startsWith(prefix)) {
        continue;
      }
      // Sanity check to confirm methods with descriptors need to _not_ have
      // return values
      var closeParenIndex = key.lastIndexOf(')');
      if (closeParenIndex > 0 && closeParenIndex != key.length() - 1) {
        throw new IllegalArgumentException("Config key '" + key + "' should not have anything after ')'");
      }
      var desc = key.substring(31);
      var value = (String) entry.getValue();
      if ("true".equals(value)) {
        descriptors.put(desc, true);
      } else if ("false".equals(value)) {
        descriptors.put(desc, false);
      } else {
        throw new IllegalArgumentException("Config key " + key + " supposed to be true or false, was " + value);
      }
    }
  }

  @Nullable
  Boolean check(String className, @Nullable String methodName, @Nullable String methodDescriptor) {
    // Check full descriptor sans return, then full sans params, then just
    // method, then just method sans params, then FQCN, and then each parent
    // package. We remove return values from the method descriptor since the
    // map only allows arguments.
    if (methodDescriptor != null) {
      methodDescriptor = methodDescriptor.substring(0, methodDescriptor.indexOf(')') + 1);
    }

    // Method name + descriptor doesn't have to be present to check class
    if (methodName != null) {
      // Try qualified class with method
      var classAndMethod = className + "." + methodName;
      if (methodDescriptor != null) {
        var invalid = descriptors.get(classAndMethod + methodDescriptor);
        if (invalid != null) {
          return invalid;
        }
      }
      var invalid = descriptors.get(classAndMethod);
      if (invalid != null) {
        return invalid;
      }
      // Try unqualified class with method
      var slashIndex = className.lastIndexOf('/');
      if (slashIndex > 0) {
        classAndMethod = classAndMethod.substring(slashIndex + 1);
        if (methodDescriptor != null) {
          invalid = descriptors.get(classAndMethod + methodDescriptor);
          if (invalid != null) {
            return invalid;
          }
        }
        invalid = descriptors.get(classAndMethod);
        if (invalid != null) {
          return invalid;
        }
      }
      // Just method
      if (methodDescriptor != null) {
        invalid = descriptors.get(methodName + methodDescriptor);
        if (invalid != null) {
          return invalid;
        }
      }
      invalid = descriptors.get(methodName);
      if (invalid != null) {
        return invalid;
      }
    }
    // Unqualified class name
    var slashIndex = className.lastIndexOf('/');
    if (slashIndex > 0) {
      var invalid = descriptors.get(className.substring(slashIndex + 1));
      if (invalid != null) {
        return invalid;
      }
    }
    // All packages above class
    while (true) {
      var invalid = descriptors.get(className);
      if (invalid != null) {
        return invalid;
      }
      var slash = className.lastIndexOf('/');
      if (slash == -1) {
        return null;
      }
      className = className.substring(0, slash);
    }
  }
}
