
# Setting up the project

```sh
mvn archetype:generate \
  -DgroupId=com.portfolio.wyche \
  -DartifactId=miscellaneous \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DarchetypeVersion=1.4 -DinteractiveMode=false
```

# Maven Common Commands
- List available updates: `mvn versions:display-dependency-updates`
- Updating to the latest release: `mvn versions:use-latest-releases`
- Convert snapshots into releases: `mvn versions:use-releases`
- Package: `mvn package`
- Compile: `mvn compile`