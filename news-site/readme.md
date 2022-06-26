# Main news-site code and entry point

## Directories

### [`/src/main`](src/main)

Contains all resources and code for the main application.

### Subdirectories of `/src/main`

#### [`java`](src/main/java)

Main application code. The server entry point is `net/ritzow/news/RunSite.java`.

#### [`css/global.scss`](src/main/css/global.css)

Contains all CSS. Mostly plain CSS currently but processed as SCSS.

#### [`image/icon.svg`](src/main/image/icon.svg)

The website logo and favicon.

#### [`lang/welcome.properties`](src/main/lang/welcome.properties)

UI text translations. Most things aren't currently translated.

#### [`xml/opensearch.xml`](src/main/xml/opensearch.xml)

OpenSearch integration (allows use by browsers as a search engine). No actual search functionality implemented.

#### [`font/OpenSans-Regular.ttf`](src/main)

This file doesn't exist but should be placed here. It can be downloaded from Google Fonts. Would probably be better to automate this.