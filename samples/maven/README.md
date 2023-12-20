
(under development)

To test, first publish the workflowcheck JAR to a local repository, so run this from the
[temporal-workflowcheck](../../temporal-workflowcheck) dir:

    gradlew publish

Now with the local repository present, can run the following from this dir:

    mvn -U verify

Note, this is a sample using the local repository so that's why we have `-U`. For normal use, `mvn verify` without the
`-U` can be used (and the `<pluginRepositories>` section of the `pom.xml` can be removed).