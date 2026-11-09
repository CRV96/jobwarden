package com.jobwarden.mcp.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.jobwarden.cv.dto.CvDetails;
import com.jobwarden.cv.dto.CvProject;
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

/**
 * The CV's personal projects, from the profile's GitHub: the repositories it chose on the CV page, which JobWarden
 * reads from GitHub with the profile's token when they're private, and the projects Claude writes from them.
 */
@Component
@RequiredArgsConstructor
class ProjectTools implements McpToolSet {

	private static final int TEXT_MAX_LENGTH = 200;

	private static final int DESCRIPTION_MAX_LENGTH = 1000;

	private final Profiles profiles;

	private final GitHubService github;

	private final CvService cvs;

	@Override
	public List<McpTool> tools() {
		return List.of(listGitHubProjects(), saveCvProjects());
	}

	private McpTool listGitHubProjects() {
		return new McpTool("list_github_projects", "List the GitHub projects",
				"The GitHub repositories the profile chose on the CV page for its CV's projects, each with its link, "
						+ "description, site, languages (the most used first), topics, stars, dates, and the start of its "
						+ "README. Only these: the profile chose what you may read.",
				object(properties(Profiles.ARGUMENT, Profiles.schema())),
				true, false, call -> Map.of("projects", this.github.findProjects(this.profiles.of(call))));
	}

	private McpTool saveCvProjects() {
		ObjectNode project = object(properties(
				"name", string("The project's name, as a person would say it"),
				"link", string("Its repository, or its site"),
				"technologies", string("What it's made with, in a few words, e.g. Java, Spring Boot, PostgreSQL"),
				"description", string("What it does and what the person built in it, in one to three sentences")),
				"name");
		return new McpTool("save_cv_projects", "Save the CV's projects",
				"Puts the projects on the profile's CV in place of those it had, the ones most worth showing first, at "
						+ "most " + RequestTools.PROJECTS_MAX + ". The CV page shows them, to review, and the CV's PDF has them.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"projects", list(project, "The projects, the ones most worth showing first")),
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

	/** A project as Claude wrote it, each field kept to a CV's length. */
	static CvProject project(ToolCall item) {
		return new CvProject(Objects.requireNonNull(shortened(item.requiredString("name"), TEXT_MAX_LENGTH)),
				shortened(item.string("link"), TEXT_MAX_LENGTH), shortened(item.string("technologies"), TEXT_MAX_LENGTH),
				shortened(item.string("description"), DESCRIPTION_MAX_LENGTH));
	}

	private static @Nullable String shortened(@Nullable String text, int maxLength) {
		return (text == null || text.length() <= maxLength) ? text : text.substring(0, maxLength - 1) + "…";
	}

}
