# Article Hosting Website

A website that stores and displays "articles" to the user.

## Project Structure

[`/news-site`](news-site) contains all website source code and assets.

[`/runner`](runner) contains a Maven plugin for running modular Java applications, designed to run the news-site.

## Software Used

### [Maven](https://maven.apache.org/)

For building, testing, and running. Follows standard build system structure. The build script requires a Java .properties file `/news-site/project.properties` with the following properties:

```properties
# Optional path to a PKCS #12 file
keystore = <path>
# Keystore password (used as password and keygen seed (NOT SECURE) for self-signed cert if keystore is absent)
password = <password>
# Human-readable organization name for self-signed cert
organization = <name>
#DNS name for the server used by self-signed cert, TLS, and other functionality
hostname = <domain>
```

To automatically build and run the server using Maven, in the root directory of the project run

```sh
mvn net.ritzow:runner:1.4-SNAPSHOT:run
```

### [Jetty Embedded HTTP Server](https://www.eclipse.org/jetty/documentation/jetty-11/programming-guide/index.html)

For HTTP 1.1/2/3 web server functionality and utilities.

### [j2html](https://j2html.com/)

For dynamically generating all HTML served.

### [HyperSQL (HSQLDB)](http://hsqldb.org/)

For article storage (currently in-memory).

### [Hikari Connection Pool](https://github.com/brettwooldridge/HikariCP)

For proper database functionality in application threads started by Jetty.

### [commonmark-java](https://github.com/commonmark/commonmark-java)

For parsing article Markdown and converting it to HTML.

### [SLF4J](http://www.slf4j.org/)

Used by libraries for logging.