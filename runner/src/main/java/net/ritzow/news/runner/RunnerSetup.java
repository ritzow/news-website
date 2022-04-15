package net.ritzow.news.runner;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;

public class RunnerSetup {
	
	public static File mainJar(MavenSession session) {
		var proj = session.getCurrentProject();

		var file = proj.getArtifact().getFile();

		if(file == null) {
			file = new File(
				proj.getBuild().getDirectory(),
				proj.getBuild().getFinalName() + "." +
					proj.getArtifact().getArtifactHandler().getExtension()
			);
		}
		
		return file;
	}
	
	public static Stream<File> libraries(MavenSession session) {
		return session.getCurrentProject().getArtifacts()
			.stream()
			.filter(artifact -> artifact.getType().equals("jar"))
			.map(Artifact::getFile);
	}
	
	public static String exactModulePathString(File mainJar, Stream<File> libJars) {
		return Stream.concat(Stream.of(mainJar.getAbsolutePath()), libJars.map(File::getAbsolutePath))
			.collect(Collectors.joining(Character.toString(File.pathSeparatorChar)));
	}

	public static String modulePathString(char separator, String mainJar, Stream<String> libJars) {
		return Stream.concat(Stream.of(mainJar), libJars)
			.collect(Collectors.joining(Character.toString(separator)));
	}
	
	public static String mainModuleName(Path mainJar) {
		return ModuleFinder.of(mainJar)
			.findAll()
			.stream()
			.findFirst()
			.orElseThrow()
			.descriptor()
			.name();
	}
	
	public static Stream<String> args(String executable, String mainModule,
		String modulePath, File jvmProps) throws IOException {
		Stream.Builder<String> args = Stream.builder();
		args.add(executable);
		args.add("--module-path");
		args.add(modulePath);

		if(jvmProps != null) {
			Properties argsFile = new Properties();
			try(var in = Files.newBufferedReader(jvmProps.toPath(), StandardCharsets.UTF_8)) {
				argsFile.load(in);
			}
			argsFile.stringPropertyNames().stream().map(entry ->
				"-D" + entry + "=" + argsFile.getProperty(entry)).forEachOrdered(args::add);
		}

		args.add("--module");
		args.add(mainModule);
		
		return args.build();
	}
}
