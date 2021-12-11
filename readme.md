# Article Hosting Website

Embedded Jetty server using HTML generation and a (currently in-memory) database for article storage as Markdown.

## Project Structure

[`/news-site`](news-site) contains most of the code.

[`/jetstart`](jetstart) contains some base code, this was originally intended to be the "website agnostic" part of the code, but I put most code in `/news-site`.

## Software Used

### [Gradle](https://docs.gradle.org/7.3.1/userguide/userguide.html)

For building, testing, and r**u**nning. Follows standard build system structure. The build script requires a Java .properties file `/news-site/project.properties` with the following properties:

```properties
# Absolute path to a Java-supported keystore, i.e. a .p12 file
keystore.file = <path>
# Keystore password
keystore.password = <password>
# Absolute path to SASS executable
sass.path = <path>
# Absolute path to Inkscape executable
inkscape.path = <path>
```

### [Jetty Embedded HTTP Server](https://www.eclipse.org/jetty/documentation/jetty-11/programming-guide/index.html)

For HTTP 1.1/2 web server functionality

### [j2html](https://j2html.com/)

For dynamically generating all HTML served.

### [HyperSQL (HSQLDB)](http://hsqldb.org/)

For article storage.

### [Hikari Connection Pool](https://github.com/brettwooldridge/HikariCP)

For proper database functionality in application threads started by Jetty.

### [commonmark-java](https://github.com/commonmark/commonmark-java)

For parsing article Markdown and converting it to HTML.

### [JUnit](https://junit.org/junit5/docs/current/user-guide/)

For testing (not implemented).

### [Jetty Load Generator](https://github.com/jetty-project/jetty-load-generator)

For testing (not implemented, need to work around certificate issues for testing).

### [SLF4J](http://www.slf4j.org/)

Used by libraries for logging.