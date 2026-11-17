package ro.threet.run.web;

import java.io.IOException;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Hosts the Angular build next to the API, so the SPA and {@code /api} share one origin
 * (ADR 0002). The container image copies the build into {@code classpath:/static/}; in
 * local dev that folder is empty and the Angular dev server serves the SPA instead.
 *
 * A file that exists is served as itself. Any other path that looks like an Angular route
 * (not under {@code /api} or {@code /actuator}, no file extension in its last segment)
 * gets {@code index.html}, so a page reload on {@code /dashboard} works. A missing file
 * (for example a chunk name from an older deploy) stays a 404.
 *
 * Everything is sent with {@code Cache-Control: no-cache}: the browser keeps its copy but
 * checks it on each load (a cheap 304). That way a cached {@code index.html} never points
 * at chunks a new deploy has removed.
 */
@Configuration
class SpaHostingConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
			.addResourceLocations("classpath:/static/")
			.setCacheControl(CacheControl.noCache())
			.resourceChain(true)
			.addResolver(new SpaFallbackResolver());
	}

	private static final class SpaFallbackResolver extends PathResourceResolver {

		@Override
		protected Resource getResource(String resourcePath, Resource location) throws IOException {
			Resource requested = super.getResource(resourcePath, location);
			if (requested != null || !isAngularRoute(resourcePath)) {
				return requested;
			}
			return super.getResource("index.html", location);
		}

		private static boolean isAngularRoute(String path) {
			if (path.equals("api") || path.startsWith("api/") || path.equals("actuator")
					|| path.startsWith("actuator/")) {
				return false;
			}
			String lastSegment = path.substring(path.lastIndexOf('/') + 1);
			return !lastSegment.contains(".");
		}

	}

}
