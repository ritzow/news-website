package net.ritzow.news.test;

import io.permazen.JObject;
import io.permazen.PermazenFactory;
import io.permazen.annotation.JField;
import io.permazen.annotation.JListField;
import io.permazen.annotation.PermazenType;
import io.permazen.core.Database;
import io.permazen.kv.mvstore.MVStoreAtomicKVStore;
import io.permazen.kv.mvstore.MVStoreKVDatabase;
import io.permazen.util.Bounds;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.random.RandomGenerator;
import jetbrains.exodus.entitystore.PersistentEntityStores;
import jetbrains.exodus.env.Environments;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVStore.Builder;
import org.junit.jupiter.api.Test;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.EnvFlags;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

public class StorageTest {
	
	@Test
	void xodus() {
		var dir = new File("target/database");
		dir.mkdirs();
		//new Log(new LogConfig())
		//Environments.newInstance()
		var env = Environments.newInstance(dir);
		var store = PersistentEntityStores.newInstance(env);
		store.executeInTransaction(txn -> {
			var languages = txn.newEntity("Language");
		});
	}
	
	@Test
	void mvstore() {
		try(var mvstore = new MVStore.Builder()
			.fileName("target/database.mvstore")
			.compressHigh()
			.autoCommitDisabled()
			.open()) {
			MVMap<Integer, byte[]> map = mvstore.openMap("test");
			for(int i = 0; i < 50_000; i++) {
				map.put(i, ("hello world " + i).getBytes(StandardCharsets.ISO_8859_1));
				System.out.println(new String(map.get(i), StandardCharsets.ISO_8859_1));
			}
			mvstore.commit();
		}
	}
	
	@Test
	void lmdbjava() {
		try(var env = Env.create()
			.open(new File("target/database.lmdb"), EnvFlags.MDB_NOSUBDIR, EnvFlags.MDB_NOLOCK)) {
			var db = env.openDbi("test", DbiFlags.MDB_CREATE);
			var write = env.txnWrite();
			db.put(write, ByteBuffer.allocate(4).putInt(10).flip(), StandardCharsets.ISO_8859_1.encode("hello world"));
			System.out.println(StandardCharsets.ISO_8859_1.decode(db.get(write, ByteBuffer.allocate(4).putInt(10).flip())));
		}
	}
	
	@Test
	void mapdb() {
		try(DB db = DBMaker/*.volumeDB(new FileChannelVol(new File("target/database.mapdb")), false)*/
			.fileDB(new File("target/database.mapdb"))
			.fileChannelEnable()
			.fileMmapEnableIfSupported()
			.executorEnable()
			.make()) {
			var map = db.hashMap("test", Serializer.INTEGER, Serializer.STRING).createOrOpen();
			map.put(10, "hello world");
			System.out.println(map.get(10));
		}
	}
	
//	@Test
//	void relationalStorage() {
//		try(RelationalStorage store = RelationalStorage.open(new MVStore.Builder()
//			.fileName("target/database.mvstore")
//			.compressHigh()
//			.autoCommitDisabled()
//			.open())) {
//			store.openTable("blah");
//		}
//	}

	@PermazenType
	public abstract static class Person implements JObject {
		public abstract int getAge();
		public abstract void setAge(int age);

		@JListField(element = @JField(indexed = true))
		public abstract List<String> getNames();
	}
	
	@Test
	void permazen() throws IOException {
		
		MVStoreAtomicKVStore implimpl = new MVStoreAtomicKVStore();
		implimpl.setBuilder(new Builder()
			.fileName("target/database.permazen"));
		var impl = new MVStoreKVDatabase();
		impl.setKVStore(implimpl);
		
		impl.start();
		
		var pz = new PermazenFactory()
			.setDatabase(new Database(impl))
			.setModelClasses(Person.class)
			.newPermazen();
		
		var tx = pz.createTransaction();

		tx.create(Person.class).getNames().addAll(RandomGenerator.getDefault().ints(500).mapToObj(String::valueOf).toList());

		tx.queryListElementIndex(Person.class, "names.element", String.class).withValue1Bounds(Bounds.ge("a"))
			.asMap()
			.forEach((s, people) -> System.out.println(s + " " + people));

		tx.getAll(Person.class).forEach(System.out::println);
		
		/*tx.getAll(Person.class)
			.forEach(System.out::println);*/

		tx.commit();

		implimpl.getMVStore().compactFile(2000);

		System.out.println(Files.size(Path.of(implimpl.getMVStore().getFileStore().getFileName())));
		
		impl.stop();
	}
}
