# Main news-site code and entry point

### [`build.gradle`](build.gradle)

This file is the main Gradle build script for the whole program. It defines specific dependencies and provides a way to run the program using Gradle (see: `run {}` and `test {}` sections. Quite messy right now.

## Directories

### [`/src/main`](src/main)

Contains all resources and code for the main application, excluding what's in `jetstart` elsewhere. Standard Gradle layout without `resources` directory.

### Subdirectories of `/src/main`

#### [`/java`](src/main/java)

Main application code. The server entry point is `net/ritzow/news/RunSite.java`.

#### [`/css/global.scss`](src/main/css/global.scss)

Contains all CSS. Mostly plain CSS currently but processed as SCSS.

#### [`/image/icon.svg`](src/main/image/icon.svg)

The website logo and favicon.

#### [`/lang/welcome.properties`](src/main/lang/welcome.properties)

UI text translations. Most things aren't currently translated.

#### [`/xml/opensearch.xml`](src/main/xml/opensearch.xml)

OpenSearch integration (allows use by browsers as a search engine via `ritzow.net`). No actual search functionality implemented.

#### [`/font/OpenSans-Regular.ttf`](src/main)

This file doesn't exist but should be placed here. It can be downloaded from Google Fonts. Would probably be better to automate this.

### [`/src/test`](src/test/java/net/ritzow/web/test/TestLoad.java)

Contains (basically nonexistent) test code. Default directory used for this by Gradle.