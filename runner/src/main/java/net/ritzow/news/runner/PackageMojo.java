package net.ritzow.news.runner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@SuppressWarnings("unused")
@Mojo(
	name = "bundle",
	defaultPhase = LifecyclePhase.NONE,
	requiresDependencyResolution = ResolutionScope.RUNTIME
)
public class PackageMojo extends AbstractMojo {

	@SuppressWarnings("unused")
	@Parameter( defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;

	@SuppressWarnings("unused")
	@Parameter(name = "jvmProps")
	private File jvmProps;
	
	@Override
	public void execute() {
		try {
			Path bundleDir = Path.of(session.getCurrentProject()
				.getBuild().getDirectory(), "bundle");
			
			var mainJar = RunnerSetup.mainJar(session).toPath();
			var libs = RunnerSetup.libraries(session);
			
			Files.createDirectories(bundleDir);

			mainJar = Files.copy(
				mainJar, 
				bundleDir.resolve(mainJar.getFileName()), 
				StandardCopyOption.REPLACE_EXISTING
			);

			var it = libs.map(File::toPath).iterator();
			List<Path> jars = new ArrayList<>();
			while(it.hasNext()) {
				Path jar = it.next();
				jars.add(Files.copy(jar, bundleDir.resolve(jar.getFileName()),
					StandardCopyOption.REPLACE_EXISTING));
			}
			
			/* Remove no longer used jars */
			Files.list(bundleDir).filter(file -> !jars.contains(file)).forEach(file -> {
				try {
					Files.delete(file);
				} catch(IOException e) {
					throw new RuntimeException("Can't delete old file from bundle", e);
				}
			});

			getLog().info("Windows: " + args(mainJar, bundleDir, jars, ';'));
			getLog().info("Linux: " + args(mainJar, bundleDir, jars, ':'));
			getLog().info("Current: " + args(mainJar, bundleDir, jars, File.pathSeparatorChar));
			getLog().info("Libraries and jar successfully copied to " +
				session.getTopLevelProject().getBasedir().toPath().relativize(bundleDir));
			
		} catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	private String args(Path mainJar, Path bundleDir, List<Path> jars, 
		char separator) throws IOException {
		return RunnerSetup.args(
			"java",
			RunnerSetup.mainModuleName(mainJar),
			RunnerSetup.modulePathString(File.pathSeparatorChar,
				bundleDir.relativize(mainJar).toString(),
				jars.stream().map(bundleDir::relativize).map(Path::toString)),
			jvmProps
		).collect(Collectors.joining(" "));
	}
}
