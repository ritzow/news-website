package net.ritzow.news.runner;

import java.io.File;
import java.io.IOException;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@SuppressWarnings("unused")
@Mojo(
	name = "run", 
	defaultPhase = LifecyclePhase.NONE, 
	requiresDependencyResolution = ResolutionScope.RUNTIME
)
public class Runner extends AbstractMojo {
	
	@SuppressWarnings("unused")
	@Parameter( defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;
	
	@SuppressWarnings("unused")
	@Parameter(name = "jvmProps")
	private File jvmProps;
	
	@Override
	public void execute() throws MojoFailureException {
		try {
			var proj = session.getCurrentProject();

			var file = proj.getArtifact().getFile();

			if(file == null) {
				file = new File(
					proj.getBuild().getDirectory(),
					proj.getBuild().getFinalName() + "." +
						proj.getArtifact().getArtifactHandler().getExtension()
				);
			}

			var libJars = proj.getArtifacts()
				.stream()
				.filter(artifact -> artifact.getType().equals("jar"))
				.map(Artifact::getFile)
				.map(File::getAbsolutePath);

			var jarString = Stream.concat(Stream.of(file.getAbsolutePath()), libJars)
				.collect(Collectors.joining(Character.toString(File.pathSeparatorChar)));

			var args = new ArrayList<String>();

			args.add(ProcessHandle.current().info().command().orElseThrow());
			args.add("--module-path");
			args.add(jarString);

			if(jvmProps != null) {
				Properties argsFile = new Properties();
				try(var in = Files.newBufferedReader(jvmProps.toPath(), StandardCharsets.UTF_8)) {
					argsFile.load(in);
				}
				argsFile.stringPropertyNames().stream().map(entry ->
					"-D" + entry + "=" + argsFile.getProperty(entry)).forEachOrdered(args::add);
			}

			var moduleName = ModuleFinder.of(file.toPath())
				.findAll()
				.stream()
				.findFirst()
				.orElseThrow()
				.descriptor()
				.name();

			args.add("--module");
			args.add(moduleName);

			getLog().info(String.join(" ", args));

			var process = new ProcessBuilder(args)
				.inheritIO()
				.start();
			
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				getLog().info("Shutting down " + process.info()
					.commandLine()
					.orElseGet(() -> "PID " + process.pid()));
				process.destroy();
				try {
					process.waitFor();
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
			}));
			
			process.waitFor();
			
		} catch(IOException | InterruptedException e) {
			throw new MojoFailureException(e);
		}
	}
}
