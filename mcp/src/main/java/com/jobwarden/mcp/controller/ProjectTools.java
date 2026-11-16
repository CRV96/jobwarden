package com.jobwarden.mcp.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvProject;
import com.jobwarden.cv.dto.GitHubProject;
import com.jobwarden.cv.service.CvService;
import com.jobwarden.cv.service.GitHubService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.list;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.string;
import static com.jobwarden.mcp.controller.Texts.shortened;

/**
 * The CV's personal projects, from the profile's GitHub: the repositories it chose on the CV page, which JobWarden
 * reads from GitHub with the profile's token when they're private, and the projects Claude writes from them.
 */
@Component
@RequiredArgsConstructor
class ProjectTools implements McpToolSet {

	private static final int TEXT_MAX_LENGTH = 200;

	private static final int DESCRIPTION_MAX_LENGTH = 1000;

	/** How much of a README the assistant reads: what the project is, and how it's built, come first. */
	static final int README_MAX_LENGTH = 4000;

	/** A fenced block of code: commands to install and run it, rarely what the project is. */
	private static final Pattern CODE_BLOCK = Pattern.compile("(?s)```.*?(```|$)|~~~.*?(~~~|$)");

	/** An image, a badge among them, linked or not: [![alt](image)](link) or ![alt](image). */
	private static final Pattern IMAGE = Pattern.compile("\\[?!\\[[^\\]]*]\\([^)]*\\)(]\\([^)]*\\))?");

	private static final Pattern HTML_TAG = Pattern.compile("<!--(?s).*?-->|</?[a-zA-Z][^>]*>");

	private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

	private final Profiles profiles;

	private final GitHubService github;

	private final CvService cvs;

	@Override
	public List<McpTool> tools() {
		return List.of(listGitHubProjects(), saveCvProjects());
	}

	private McpTool listGitHubProjects() {
		return new McpTool("list_github_projects", "List the GitHub projects",
				"The GitHub repositories the profile chose on the CV page, only these: link, description, site, "
						+ "languages (most used first), topics, stars, dates, and the start of the README.",
				object(properties(Profiles.ARGUMENT, Profiles.schema())),
				true, false, call -> Map.of("projects", this.github.findProjects(this.profiles.of(call))
					.stream()
					.map(ProjectTools::view)
					.toList()));
	}

	private McpTool saveCvProjects() {
		ObjectNode project = object(properties(
				"name", string("As a person would say it"),
				"link", string("Repository or site"),
				"technologies", string("A few words, e.g. Java, Spring Boot, PostgreSQL"),
				"description", string("What it does and what the person built, 1-3 sentences")),
				"name");
		return new McpTool("save_cv_projects", "Save the CV's projects",
				"Replaces the CV's projects, for the profile to review on the CV page. Max " + RequestTools.PROJECTS_MAX
						+ ".",
				object(properties(Profiles.ARGUMENT, Profiles.schema(), "projects", list(project, "Best first")),
						"projects"),
				false, false, call -> {
					List<CvProject> projects = call.items("projects").stream().map(ProjectTools::project).toList();
					if (projects.size() > RequestTools.PROJECTS_MAX) {
						throw new ToolException("At most " + RequestTools.PROJECTS_MAX + " projects");
					}
					CvDetails saved = this.cvs.saveProjects(this.profiles.of(call), projects);
					return Map.of("projects", saved.projects().size(), "page", "/cv");
				});
	}

	/** A project as the assistant wrote it, each field kept to a CV's length. */
	static CvProject project(ToolCall item) {
		return new CvProject(Objects.requireNonNull(shortened(item.requiredString("name"), TEXT_MAX_LENGTH)),
				shortened(item.string("link"), TEXT_MAX_LENGTH), shortened(item.string("technologies"), TEXT_MAX_LENGTH),
				shortened(item.string("description"), DESCRIPTION_MAX_LENGTH));
	}

	/**
	 * A repository as the assistant reads it: its README without what says nothing about the project to a reader of
	 * text, namely images and badges, HTML tags and code blocks, and no longer than its start.
	 */
	static ProjectView view(GitHubProject project) {
		return new ProjectView(project.name(), project.url(), project.homepage(), project.description(),
				project.languages(), project.topics(), project.stars(), project.fork(), project.createdAt(),
				project.pushedAt(), readme(project.readme()));
	}

	static @Nullable String readme(@Nullable String readme) {
		if (readme == null) {
			return null;
		}
		String text = CODE_BLOCK.matcher(readme).replaceAll("");
		text = IMAGE.matcher(text).replaceAll("");
		text = HTML_TAG.matcher(text).replaceAll("");
		// Each line's trailing spaces, in one pass: a regex retries a long run of them at each of its spaces
		text = BLANK_LINES.matcher(text.lines().map(String::stripTrailing).collect(Collectors.joining("\n"))).replaceAll("\n\n")
			.strip();
		return text.isEmpty() ? null : shortened(text, README_MAX_LENGTH);
	}

	/**
	 * @param readme its start, without images, HTML and code blocks
	 */
	record ProjectView(String name, String url, @Nullable String homepage, @Nullable String description,
			List<String> languages, List<String> topics, int stars, boolean fork, @Nullable Instant createdAt,
			@Nullable Instant pushedAt, @Nullable String readme) {
	}

}
