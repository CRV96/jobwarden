package com.jobwarden.search;

import java.util.List;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * What the search page suggests as you type, and the descriptions it offers as examples.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SearchSuggestions {

	public static final List<String> TITLES = List.of("Software Engineer", "Software Developer", "Backend Developer",
			"Frontend Developer", "Full Stack Developer", "Web Developer", "Mobile Developer", "iOS Developer",
			"Android Developer", "Java Developer", "Python Developer", ".NET Developer", "JavaScript Developer",
			"React Developer", "Node.js Developer", "PHP Developer", "Go Developer", "DevOps Engineer",
			"Site Reliability Engineer", "Cloud Engineer", "Platform Engineer", "Data Engineer", "Data Scientist",
			"Data Analyst", "Machine Learning Engineer", "QA Engineer", "Test Automation Engineer", "Security Engineer",
			"Embedded Software Engineer", "Solutions Architect", "Software Architect", "Tech Lead", "Engineering Manager",
			"Product Manager", "Project Manager", "Scrum Master", "Business Analyst", "Product Designer", "UX Designer",
			"Database Administrator");

	public static final List<String> SKILLS = List.of("Java", "Spring", "Kotlin", "Scala", "Python", "Django",
			"JavaScript", "TypeScript", "React", "Angular", "Vue", "Node.js", "Go", "Rust", "C#", ".NET", "C++", "PHP",
			"Ruby", "Swift", "SQL", "PostgreSQL", "MySQL", "MongoDB", "Redis", "Kafka", "AWS", "Azure", "GCP", "Docker",
			"Kubernetes", "Terraform", "Linux", "GraphQL", "Microservices", "Machine Learning", "Spark");

	public static final List<Region> REGIONS = List.of(new Region("Remote", null), new Region("EMEA", null),
			new Region("Europe", "eu"), new Region("remote EU", "eu"), new Region("Worldwide", null),
			new Region("DACH", "de"), new Region("Nordics", null), new Region("Benelux", "be"), new Region("UK", "gb"),
			new Region("Germany", "de"), new Region("France", "fr"), new Region("Spain", "es"),
			new Region("Netherlands", "nl"), new Region("Poland", "pl"), new Region("Romania", "ro"),
			new Region("Portugal", "pt"), new Region("Ireland", "ie"), new Region("MENA", null), new Region("US", "us"),
			new Region("APAC", null), new Region("LATAM", null));

	public static final List<JobBoard> JOB_BOARDS = List.of(new JobBoard("Lever", "jobs.lever.co"),
			new JobBoard("Greenhouse", "boards.greenhouse.io"), new JobBoard("Ashby", "jobs.ashbyhq.com"),
			new JobBoard("Workable", "apply.workable.com"), new JobBoard("SmartRecruiters", "jobs.smartrecruiters.com"),
			new JobBoard("Recruitee", "recruitee.com"), new JobBoard("Personio", "personio.de"),
			new JobBoard("Teamtailor", "teamtailor.com"));

	/** The Google query's sites until you change them: the three boards most tech companies use. */
	public static final List<String> DEFAULT_SITES = JOB_BOARDS.subList(0, 3).stream().map(JobBoard::domain).toList();

	/** Descriptions of a job, for {@link DescriptionParser}. */
	public static final List<String> EXAMPLES = List.of("Senior backend engineer, remote Europe, contract",
			"Frontend developer with React and TypeScript, hybrid in Germany",
			"Data engineer, remote EMEA, full-time, not junior", "DevOps engineer, Kubernetes and AWS, remote worldwide",
			"Junior QA engineer in Romania, internship or full-time");

}
