package net.ritzow.news.runner;

import java.io.File;
import java.io.IOException;
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
			
			var mainJar = RunnerSetup.mainJar(session);
			
			var args = RunnerSetup.args(
				ProcessHandle.current().info().command().orElseThrow(),
				RunnerSetup.mainModuleName(mainJar.toPath()),
				RunnerSetup.exactModulePathString(mainJar, RunnerSetup.libraries(session)),
				jvmProps
			).toList();

			getLog().info(String.join(" ", args));

			var process = new ProcessBuilder(args)
				.inheritIO()
				.start();

			getLog().info("Maven will "
				+ (process.supportsNormalTermination() ? "forcibly" : "normally")
				+ " terminate the program when stopped."
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
			throw new MojoFailureException(e);
		}
	}
}
