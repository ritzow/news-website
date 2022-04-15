package net.ritzow.news.runner;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

//@Mojo(name = "resources")
public class Resources extends AbstractMojo {

	@SuppressWarnings("unused")
	@Parameter( defaultValue = "${session}", readonly = true, required = true)
	private MavenSession session;
	
	@Override
	public void execute() {
		
		var outDir = session.getCurrentProject()
			.getBasedir()
			.toPath().resolve(
				session.getCurrentProject()
					.getBuild()
					.getDirectory()
			);
		
		//getLog().info(outDir.toString());
	}
}
