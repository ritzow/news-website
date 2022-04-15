package net.ritzow.news;

import java.sql.SQLException;
import java.util.Locale;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import net.ritzow.news.database.ContentManager;

public class ContentUtil {
	private static final String VALID_CHARS = "abcdefghijklmnopqrstuvwxyz";
	
	public static String generateGibberish(RandomGenerator random, boolean md, int words, int maxWordSize) {
		StringBuilder builder = new StringBuilder(words * maxWordSize/2);
		for(int i = 0; i < words; i++) {
			char[] word = new char[random.nextInt(maxWordSize + 1) + 1];
			for(int j = 0; j < word.length; j++) {
				word[j] = VALID_CHARS.charAt(random.nextInt(VALID_CHARS.length()));
			}
			if(random.nextBoolean()) {
				word[0] = Character.toUpperCase(word[0]);
			}
			
			if(md && random.nextInt(100) < 25) {
				builder.append("**").append(word).append("** ");
			} else {
				builder.append(word).append(' ');
			}
		}
		return builder.toString();
	}
	
	static void genArticles(ContentManager cm) throws SQLException {
		RandomGenerator random = RandomGeneratorFactory.getDefault().create(0);
		
		int count = random.nextInt(15, 35);
		for(int i = 0; i < count; i++) {
			int length = random.nextInt(200, 2000);
			String title = ContentUtil.generateGibberish(random, false, 3, 5);
			String url = Integer.toHexString(i);
			for(Locale locale : cm.getSupportedLocales()) {
				if(random.nextDouble() < 0.75) {
					cm.newArticle(url,
						locale, title + " " + locale.getDisplayLanguage(locale), ContentUtil.generateGibberish(random, true, length, 6));
				}
			}
			var article = cm.getLatestArticle(url, cm.getArticleLocales(url).stream().findFirst().orElseThrow(), data -> new Object());
		}
	}
}
