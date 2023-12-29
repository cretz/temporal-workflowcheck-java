
(under active development)

### FAQ

* Why not CheckStyle or ErrorProne or something else off the shelf?
  * Need to make homemade checks for transitive anyways, so no benefit
  * Tried using ErrorProne but it's a pain across compilation units and transitive (same pain as annotation processor)
* Why not a custom annotation processor?
  * It's a bit tough to use the JDK compiler API and walk source and bytecode
  * No good caching across compilation units
* Why not Soot or SootUp?
  * Soot is now becoming SootUp
  * SootUp is in a development stage and has lots of little errors (can't even handle annotations not on classpath)
* Why not ClassGraph?
  * That library doesn't give which methods call other methods (so not a call graph)
* Why not SemGrep?
  * Does not seem to support recursive call graph analysis to find invalid calls at arbitrary call depths
* Why .properties config instead of something more modern?
  * We don't want runtime dependencies
* Why does so much of the code do traditional looping instead of streaming, direct field access instead of
  encapsulation, etc?
  * This code is optimized for performance, but the user-facing API does follow proper practices

### Configuration Properties Format

* Configuration is via properties files, but many properties files can be referenced/merged via CLI/env (TODO: which?)
* For the rules below, `[fully-qualified-thing]` is `path/to/Class.method(LDesc;)V` style, but parameters can be left
  off to match all overloads of the method, or the method can be left off to match the entire class, or the class can be
  left off to match entire packages
* To mark something as invalid or valid, set `temporal.workflowcheck.invalid.[fully-qualified-thing]` to `true` or
  `false` respectively
* To mark something as excluded or included, set `temporal.workflowcheck.exclude.[fully-qualified-thing]` to `true` or
  `false` respectively
* More specific properties override less specific, after that, latter property files override earlier ones

### TODO

* Normalize method descriptor to not include anything after `()` (i.e. remove return type) for purposes of descriptor
  equality checks inside of code
* Provide config prebuilding where you can give the classpath, a set of packages, and an output file and the classpath
  will be scanned for all conforming classes, and a new properties file will be created that represents all found
  invalid calls, and scan exclusions at the top (Note, may need some way in config to provide the invalid calls that
  it is calling). Then use this prebuilding to generate for Java 21's standard library to improve performance.
* Document that method checks are not hierarchical but fixed for performance reasons
* Document how collection iteration is by default considered unsafe but that it can be set as safe
* Support configuration via env vars and CLI params too
* SARIF output