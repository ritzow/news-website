package net.ritzow.news.internal;

import net.ritzow.news.NewsSite;
import net.ritzow.news.OpenSearch;
import net.ritzow.news.response.ContentSource;
import net.ritzow.news.response.NamedResourceConsumer;

import static net.ritzow.news.ResponseUtil.contentPath;

public class SiteResources {

	public static final NamedResourceConsumer<NewsSite> 
		RES_GLOBAL_CSS = NamedResourceConsumer.ofHashed(ContentSource.ofModuleResource("/css/global.css", "text/css"));
	public static final NamedResourceConsumer<NewsSite> RES_ICON = NamedResourceConsumer.ofHashed(ContentSource.ofModuleResource("/image/icon.svg", "image/svg+xml"));
	public static final NamedResourceConsumer<NewsSite> RES_FONT = NamedResourceConsumer.ofHashed(ContentSource.ofModuleResource("/font/OpenSans-Regular.ttf", "font/ttf"));
	public static final NamedResourceConsumer<NewsSite>
		RES_FONT_FACE = NamedResourceConsumer.ofHashed(
			ContentSource.ofString("""
				@font-face {
					font-family: "Open Sans";
					src: url("URLHERE") format("truetype");
					font-display: swap;
				}
				""".replace("URLHERE", contentPath(RES_FONT)),
				"text/css"
			)
		);
	public static final ContentSource 
			RES_OPENSEARCH = ContentSource.ofString(OpenSearch.generateOpensearchXml(), "application/opensearchdescription+xml");
}
