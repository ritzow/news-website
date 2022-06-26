package net.ritzow.news.runner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

@SuppressWarnings("unused")
@Execute(phase = LifecyclePhase.PACKAGE)
@Mojo(
	name = "run", 
	defaultPhase = LifecyclePhase.NONE, 
	requiresDependencyResolution = ResolutionScope.RUNTIME,
	threadSafe = true
)
public class RunnerMojo extends AbstractMojo {
	
	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;
	
	@Parameter(name = "jvmProps")
	private File jvmProps;
	
	@Parameter(name = "reportNonZeroExit", defaultValue = "false")
	private boolean reportNonZeroExit;
	
	@Override
	public void execute() throws MojoFailureException {
		var project = session.getCurrentProject();
		if(project.getPackaging().equals("jar")) {
			executeProject(project);
		} else {
			getLog().info("Skipping project " 
				+ project.getId() + " with packaging \"" 
				+ project.getPackaging() + "\" (must be \"jar\")");
		}
	}
	
	private void executeProject(MavenProject project) throws MojoFailureException {
		try {
			var mainJar = RunnerSetup.mainJar(project);
			var args = RunnerSetup.args(
				ProcessHandle.current().info().command().orElseThrow(),
				RunnerSetup.mainModuleName(mainJar.toPath()),
				RunnerSetup.exactModulePathString(mainJar, RunnerSetup.libraries(project)),
				jvmProps
			).toList();

			long startTime = System.nanoTime();
			var process = new ProcessBuilder(args).start();

			getLog().info("Running project " + project.getId() + " as process " + process.pid());
			getLog().info(String.join(" ", args));

			getLog().info("Maven will terminate the program "
				+ (process.supportsNormalTermination() ? "forcibly" : "normally")
				+ " when stopped."
			);
			
			var stdout = new Thread(() -> {
				try(var scan = process.inputReader(StandardCharsets.UTF_8)) {
					scan.lines().forEachOrdered(content -> getLog().info("[STDOUT] " + content));
				} catch(IOException e) {
					throw new RuntimeException(e);
				}
			});
			stdout.setDaemon(true);
			stdout.start();

			var stderr = new Thread(() -> {
				try(var scan = process.errorReader(StandardCharsets.UTF_8)) {
					scan.lines().forEachOrdered(content -> getLog().info("[STDERR] " + content));
				} catch(IOException e) {
					throw new RuntimeException(e);
				}
			});
			stderr.setDaemon(true);
			stderr.start();

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				if(process.isAlive()) {
					getLog().info("Shutting down " + process.info()
						.commandLine().orElseGet(() -> "PID " + process.pid()));

					try {
						process.destroy();
						
						try {
							getLog().info("[STDIN] " + new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
							getLog().info("[STDERR] " + new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
						} catch(IOException e) {
							throw new RuntimeException(e);
						}

						int status = exitApplication(process);
						if(status != 0 && reportNonZeroExit) {
							getLog().error("Application exited with exit code " + status);
						}
					} catch(InterruptedException e) {
						getLog().error("Interrupted while waiting for application termination");
					}
				}
			}));

			try {
				int status = exitApplication(process);
				if(status != 0 && reportNonZeroExit) {
					throw new MojoFailureException("Application exited with exit code " + status);
				}
			} catch(InterruptedException e) {
				getLog().error("Interrupted while waiting for application termination");
			}
		} catch(IOException e) {
			getLog().error(e);
		}
	}
	
	private int exitApplication(Process app) throws InterruptedException {
		int status = app.waitFor();
		app.descendants().forEach(ProcessHandle::destroy);
		app.descendants().forEach(process -> {
			process.onExit().orTimeout(5, TimeUnit.SECONDS).whenComplete((proc, ex) -> {
				if(ex != null) {
					if(ex instanceof InterruptedException || ex instanceof ExecutionException) {
						getLog().error("Failed to wait for application descendant PID " + process.pid() + " to exit");			
					} else if(ex instanceof TimeoutException e) {
						if(process.destroyForcibly()) {
							getLog().warn("Forcibly exited descendant PID " + process.pid());	
						} else {
							getLog().error("Failed to forcibly exit descendant PID " + process.pid());
						}
					}
				}
			});
		});
		app.descendants().forEach(process -> process.onExit().join());
		return status;
	}
}
