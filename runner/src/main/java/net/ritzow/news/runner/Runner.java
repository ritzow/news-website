package net.ritzow.news.runner;

import java.io.File;
import java.io.IOException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

@SuppressWarnings("unused")
@Execute(lifecycle = "default", phase = LifecyclePhase.PACKAGE)
@Mojo(
	name = "run", 
	defaultPhase = LifecyclePhase.NONE, 
	requiresDependencyResolution = ResolutionScope.RUNTIME,
	threadSafe = true
)
public class Runner extends AbstractMojo {
	
	@SuppressWarnings("unused")
	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;
	
	@SuppressWarnings("unused")
	@Parameter(name = "jvmProps")
	private File jvmProps;
	
	@Override
	public void execute() {
		var project = session.getCurrentProject();
		if(project.getPackaging().equals("jar")) {
			executeProject(project);
		} else {
			getLog().info("Skipping project " 
				+ project.getId() + " with packaging \"" 
				+ project.getPackaging() + "\" (must be \"jar\")");
		}
	}
	
	private void executeProject(MavenProject project) {
		try {
			var mainJar = RunnerSetup.mainJar(project);
			var args = RunnerSetup.args(
				ProcessHandle.current().info().command().orElseThrow(),
				RunnerSetup.mainModuleName(mainJar.toPath()),
				RunnerSetup.exactModulePathString(mainJar, RunnerSetup.libraries(project)),
				jvmProps
			).toList();

			getLog().info("Running project " + project.getId());
			getLog().info(String.join(" ", args));

			var process = new ProcessBuilder(args)
				.inheritIO()
				.start();

			getLog().info("Maven will terminate the program " 
				+ (process.supportsNormalTermination() ? "forcibly" : "normally") 
				+ " when stopped."
			);

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
			getLog().error(e);
		}
	}
}
