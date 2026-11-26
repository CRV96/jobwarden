package com.jobwarden.mcp.controller;

import java.util.List;
import java.util.Map;

import com.jobwarden.jobs.dto.CompanyResearch;
import com.jobwarden.jobs.dto.PngImage;
import com.jobwarden.jobs.service.ApplicationService;
import com.jobwarden.jobs.service.CompanyResearchService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;
import org.springframework.stereotype.Component;

import static com.jobwarden.mcp.controller.JobTools.properties;
import static com.jobwarden.mcp.controller.Schemas.integer;
import static com.jobwarden.mcp.controller.Schemas.object;
import static com.jobwarden.mcp.controller.Schemas.string;
import static com.jobwarden.mcp.controller.Schemas.strings;

/**
 * Finding out about a job's company: what the assistant read on the web of who they are, what their people say, what
 * they pay and what's new, which the job's page shows; and its logo, for a job saved without one or with the wrong one.
 */
@Component
@RequiredArgsConstructor
class ResearchTools implements McpToolSet {

	/** The most a text field keeps, and the most items a list does: read at a glance, before applying or a call. */
	private static final int TEXT_MAX_LENGTH = 4000;

	private static final int ITEMS_MAX = 15;

	private final Profiles profiles;

	private final CompanyResearchService research;

	private final ApplicationService applications;

	private final CompanyLogos logos;

	@Override
	public List<McpTool> tools() {
		return List.of(saveCompanyResearch(), saveCompanyLogo());
	}

	/**
	 * JobWarden downloads the image itself, from the public internet, and keeps it as the job's logo: the assistant
	 * passes an address, never the image's bytes, which would cost far more of what it reads.
	 */
	private McpTool saveCompanyLogo() {
		return new McpTool("save_company_logo", "Save a company's logo",
				"Downloads the image at url as the job's logo.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(), "job_id", integer(), "url", string()),
						"job_id", "url"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					long jobId = call.requiredNumber("job_id");
					PngImage logo = this.logos.download(call.requiredString("url"));
					this.applications.changeCompanyLogo(userId, jobId, logo);
					return Map.of("job_id", jobId, "saved", true, "page", "/applications/" + jobId);
				});
	}

	private McpTool saveCompanyResearch() {
		// Its fields go undescribed: every session of every assistant reads the tools, and only a RESEARCH_COMPANY
		// request calls this one, whose steps say what goes in each
		return new McpTool("save_company_research", "Save research on a company",
				"Saves a job's company research, replacing the last.",
				object(properties(Profiles.ARGUMENT, Profiles.schema(),
						"job_id", integer(),
						"company", string(),
						"ratings", strings(),
						"pros", strings(),
						"cons", strings(),
						"salaries", strings(),
						"benefits", strings(),
						"news", strings(),
						"interviews", string(),
						"watch_out", string(),
						"sources", strings()),
						"job_id", "company"),
				false, false, call -> {
					long userId = this.profiles.of(call);
					long jobId = call.requiredNumber("job_id");
					CompanyResearch saved = this.research.save(userId, jobId, CompanyResearch.builder()
						.company(shortened(call.requiredString("company")))
						.ratings(items(call.strings("ratings")))
						.pros(items(call.strings("pros")))
						.cons(items(call.strings("cons")))
						.salaries(items(call.strings("salaries")))
						.benefits(items(call.strings("benefits")))
						.news(items(call.strings("news")))
						.interviews(shortened(call.string("interviews")))
						.watchOut(shortened(call.string("watch_out")))
						.sources(items(call.strings("sources")))
						.build());
					return Map.of("job_id", jobId, "ratings", saved.ratings().size(), "salaries", saved.salaries().size(),
							"sources", saved.sources().size(), "page", "/applications/" + jobId);
				});
	}

	private static List<String> items(List<String> items) {
		return items.stream().limit(ITEMS_MAX).map(ResearchTools::shortened).toList();
	}

	@Contract("null -> null; !null -> !null")
	private static @Nullable String shortened(@Nullable String text) {
		return Texts.shortened(text, TEXT_MAX_LENGTH);
	}

}
