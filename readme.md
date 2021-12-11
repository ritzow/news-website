# Article Hosting Website

Embedded Jetty server using HTML generation and a (currently in-memory) database for article storage as Markdown.

## Project Structure

`/news-site` contains most of the code.

`/jetstart` contains some base code, this was originally intended to be the "website agnostic" part of the code, but I put most code in `/news-site`.

## Software Used

### Gradle

For building, testing, and running. Follows standard build system structure. The build script requires a Java .properties file `/news-site/project.properties` with the following properties:

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

### Jetty Embedded HTTP Server

For HTTP 1.1/2 web server functionality

### j2html

For dynamically generating all HTML served.

### HyperSQL (HSQLDB)

For article storage.

### Hikari Connection Pool

For proper database functionality in application threads started by Jetty.

### commonmark-java

For parsing article Markdown and converting it to HTML.

### JUnit

For testing (not implemented).

### Jetty Load Generator

For testing (not implemented, need to work around certificate issues for testing).

### SLF4J

Used by libraries for logging.