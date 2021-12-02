package net.ritzow.news;

import java.sql.SQLException;
import java.util.Locale;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public class ContentUtil {
	private static final String VALID_CHARS = "abcdefghijklmnopqrstuvwxyz";
	
	private static String generateGibberish(RandomGenerator random, int words, int maxWordSize) {
		StringBuilder builder = new StringBuilder(words * maxWordSize/2);
		for(int i = 0; i < words; i++) {
			char[] word = new char[random.nextInt(maxWordSize + 1) + 1];
			for(int j = 0; j < word.length; j++) {
				word[j] = VALID_CHARS.charAt(random.nextInt(VALID_CHARS.length()));
			}
			if(random.nextBoolean()) {
				word[0] = Character.toUpperCase(word[0]);
			}
			builder.append(word).append(' ');
		}
		return builder.toString();
	}
	
	static void genArticles(ContentManager cm) throws SQLException {
		RandomGenerator random = RandomGeneratorFactory.getDefault().create(0);
		
		for(int i = 0; i < 25; i++) {
			int length = random.nextInt(200, 1000);
			String title = ContentUtil.generateGibberish(random, 3, 5);
			for(Locale locale : cm.getSupportedLocales()) {
				if(random.nextFloat() < 0.7) {
					cm.newArticle(Integer.toHexString(i),
						locale, title + " " + locale.getDisplayLanguage(locale), ContentUtil.generateGibberish(random, length, 6));
				}
			}
		}
	}
}
