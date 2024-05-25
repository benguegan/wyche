
# Setting up the project

```sh
mvn archetype:generate \
  -DgroupId=com.portfolio.wyche \
  -DartifactId=miscellaneous \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DarchetypeVersion=1.4 -DinteractiveMode=false
```

# Executing

```sh
mvn clean compile exec:java
```

# Maven Common Commands
- List available updates: `mvn versions:display-dependency-updates`
- Updating to the latest release: `mvn versions:use-latest-releases`
- Convert snapshots into releases: `mvn versions:use-releases`
- Package: `mvn package`
- Compile: `mvn compile`

# Enable HTTPS support

## Generate a self-signed certificate

1. Install mkcert
```sh
mkcert -install
```

2. Generate a CA certificate and a private key (by default the password for this file is `changeit`)
```sh
mkcert -pkcs12 localhost
```

3. Remove the CA from your system trust stores
```sh
mkcert -uninstall
```

## Test the connection

- Use --cacert option to curl to tell it to truct the mkcert certificate

```sh
curl --cacert "$(mkcert -CAROOT)/rootCA.pem" \
  -d ‘{"username":"demo","password":"password"}’ \
  -H ‘Content-Type: application/json’ https://localhost:4567/users
```