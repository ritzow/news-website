package net.ritzow.news;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import net.ritzow.news.ResponseUtil.ContextRequestConsumer;
import net.ritzow.news.response.CachingImmutableRequestConsumer;
import net.ritzow.news.response.ContentSource;
import net.ritzow.news.response.NamedResourceConsumer;

public class JarContent<T> implements NamedResourceConsumer<T> {
	private final String path;
	private final CachingImmutableRequestConsumer<T> handler;
	
	//TODO pre-gzip content
	
	private JarContent(String jarPath, String type) {
		try {
			this.handler = new CachingImmutableRequestConsumer<T>(ContentSource.ofModuleResource(jarPath, type));
			MessageDigest hash = MessageDigest.getInstance("SHA-256");
			hash.update(this.handler.load());
			hash.update(jarPath.getBytes(StandardCharsets.UTF_8));
			hash.update(type.getBytes(StandardCharsets.UTF_8));
			this.path = ResourceUtil.bytesToString(hash.digest());
		} catch(NoSuchAlgorithmException | IOException e) {
			throw new RuntimeException(e);
		}
		
		/*var compressor = new Deflater(Deflater.BEST_COMPRESSION);
		//compressor.setStrategy(Deflater.HUFFMAN_ONLY);
		compressor.setInput(hash.digest());
		compressor.finish();
		byte[] output = new byte[256];
		int length = compressor.deflate(output);
		if(!compressor.finished())
			throw new RuntimeException();*/
			
		//this.path = ResourceUtil.bytesToString(Arrays.copyOf(output, length));
		//this.path = ResourceUtil.bytesToString(hash.digest());
	}

	public static <T> JarContent<T> create(String jarPath, String type) {
		return new JarContent<T>(jarPath, type);
	}
	
	

	public String fileName() {
		return path;
	}

	@Override
	public String getKey() {
		//TODO unify the path handling stuff in newssite to use normal paths
		return path; //remove leading slash
	}

	@Override
	public ContextRequestConsumer<T> getValue() {
		return handler;
	}

	@Override
	public ContextRequestConsumer<T> setValue(ContextRequestConsumer<T> value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof JarContent c && path.equals(c.path);
	}

	@Override
	public int hashCode() {
		return Objects.hash(path);
	}
}
