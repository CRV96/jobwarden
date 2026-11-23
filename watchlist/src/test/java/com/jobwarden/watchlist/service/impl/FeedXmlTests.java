package com.jobwarden.watchlist.service.impl;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class FeedXmlTests {

	@Test
	void readsEachJobsElementAsTheJsonTheOtherFeedsGive() throws XMLStreamException {
		List<JsonNode> jobs = jobs("""
				<?xml version="1.0" encoding="UTF-8"?>
				<workzag-jobs>
				<position>
				    <id>1</id>
				    <name>Java &amp; Kotlin Developer</name>
				    <!-- a comment says nothing -->
				    <offices><office>Berlin</office><office>Remote</office></offices>
				    <parts><part><value><![CDATA[<p>Build</p>]]></value></part></parts>
				    <empty/>
				</position>
				<position><id>2</id></position>
				</workzag-jobs>""");

		assertThat(jobs).hasSize(2);
		JsonNode first = jobs.getFirst();
		assertThat(first.path("name").asString()).isEqualTo("Java & Kotlin Developer");
		assertThat(FeedXml.each(first.path("offices").path("office"))).extracting(JsonNode::asString)
			.containsExactly("Berlin", "Remote");
		// Named once, a child is the object itself
		assertThat(FeedXml.each(first.path("parts").path("part"))).singleElement()
			.satisfies(part -> assertThat(part.path("value").asString()).isEqualTo("<p>Build</p>"));
		assertThat(first.path("empty").asString()).isEmpty();
		assertThat(FeedXml.each(first.path("missing"))).isEmpty();
		assertThat(FeedXml.each(JsonNodeFactory.instance.nullNode())).isEmpty();
		assertThat(jobs.get(1).path("id").asString()).isEqualTo("2");
	}

	@Test
	void readsNoEntityADtdDeclares() {
		// Were the DTD read, the job's name would be the file's lines
		assertThatExceptionOfType(XMLStreamException.class).isThrownBy(() -> jobs("""
				<?xml version="1.0"?>
				<!DOCTYPE workzag-jobs [<!ENTITY secret SYSTEM "file:///etc/hosts">]>
				<workzag-jobs><position><id>1</id><name>&secret;</name></position></workzag-jobs>"""));
	}

	@Test
	void refusesJobsDeeperThanAnyFeedGoes() {
		String deep = "<a>".repeat(FeedXml.MAX_DEPTH) + "x" + "</a>".repeat(FeedXml.MAX_DEPTH);

		assertThatExceptionOfType(XMLStreamException.class)
			.isThrownBy(() -> jobs("<workzag-jobs><position>" + deep + "</position></workzag-jobs>"))
			.withMessageContaining("deeper than " + FeedXml.MAX_DEPTH);
	}

	@Test
	void refusesAnAnswerThatIsntXml() {
		assertThatExceptionOfType(XMLStreamException.class).isThrownBy(() -> jobs("{\"jobs\": []}"));
		assertThatExceptionOfType(XMLStreamException.class)
			.isThrownBy(() -> jobs("<workzag-jobs><position><id>1</id></workzag-jobs>"));
	}

	private static List<JsonNode> jobs(String xml) throws XMLStreamException {
		List<JsonNode> jobs = new ArrayList<>();
		FeedXml.readJobs(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "position", jobs::add);
		return jobs;
	}

}
