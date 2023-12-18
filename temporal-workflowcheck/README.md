
(under active development)

### Configuration Properties Format

* Configuration is via properties files, but many properties files can be referenced/merged via CLI/env (TODO: which?)
* To mark something as invalid, `temporal.workflowcheck.invalid.[fully-qualified-thing]=true` 
* To mark something as valid (overrides invalid by default), `temporal.workflowcheck.valid.[fully-qualified-thing]=true`
* To exclude from checking, `temporal.workflowcheck.exclude.[fully-qualified-thing]=true`
* To include (overrides exclude by default), `temporal.workflowcheck.include.[fully-qualified-thing]=true`
* By default, valid overrides invalid and include overrides exclude
  * Can be given priority to affect order,
    `temporal.workflowcheck.[invalid|valid|exclude|include].[fully-qualified-thing].priority=123`. Must be signed
    integer. Default is 0, highest number is highest priority.
  * For the same priority, more specific qualified things are applied over least specific
  * For the same priority and same qualified thing, later-provided cli/env files' versions replace earlier
* "Fully qualified thing" is `path/to/Class` to apply to all methods, or it can have `.method` to apply to all overloads
  of that method name or `.method(` with the rest of the exact signature to apply to exact overloads.