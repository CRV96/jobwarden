package com.jobwarden.watchlist.service.impl;

import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reading an XML feed (Personio's) as the JSON the other feeds give, a job at a time, as it streams in: each job's
 * element is an object, its children its properties; a child with only text is a string, a child named twice or more
 * an array. Attributes are left out. No DTD is read, so no entity reaches outside the answer.
 */
final class FeedXml {

	/** Deeper than any job's elements go: Personio's go three deep. */
	static final int MAX_DEPTH = 16;

	private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

	private FeedXml() {
	}

	/**
	 * @param element the name of each job's element, e.g. position: any but the root's
	 * @throws XMLStreamException when the answer isn't XML, or it couldn't be read: its cause says which
	 */
	static void readJobs(InputStream body, String element, Consumer<JsonNode> job) throws XMLStreamException {
		XMLStreamReader xml = factory().createXMLStreamReader(body);
		try {
			boolean inRoot = false;
			while (xml.hasNext()) {
				if (xml.next() != XMLStreamConstants.START_ELEMENT) {
					continue;
				}
				if (inRoot && element.equals(xml.getLocalName())) {
					job.accept(element(xml, 1));
				}
				inRoot = true;
			}
		} finally {
			xml.close();
		}
	}

	/**
	 * A child named once is an object or a string, named twice or more an array: either way, each of them.
	 *
	 * @return none for a child that isn't there
	 */
	static List<JsonNode> each(JsonNode node) {
		if (node.isMissingNode() || node.isNull()) {
			return List.of();
		}
		return node.isArray() ? List.copyOf(node.values()) : List.of(node);
	}

	/**
	 * Its children, or its text, up to its end.
	 *
	 * @param depth how deep in the job it is, the job's own element 1
	 */
	private static JsonNode element(XMLStreamReader xml, int depth) throws XMLStreamException {
		if (depth > MAX_DEPTH) {
			throw new XMLStreamException("Its elements go deeper than " + MAX_DEPTH);
		}
		ObjectNode children = null;
		StringBuilder text = new StringBuilder();
		while (true) {
			switch (xml.next()) {
				case XMLStreamConstants.START_ELEMENT -> {
					String name = xml.getLocalName();
					JsonNode child = element(xml, depth + 1);
					if (children == null) {
						children = NODES.objectNode();
					}
					add(children, name, child);
				}
				case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE ->
					text.append(xml.getText());
				case XMLStreamConstants.END_ELEMENT -> {
					return (children != null) ? children : NODES.stringNode(text.toString().strip());
				}
				case XMLStreamConstants.END_DOCUMENT -> throw new XMLStreamException("The answer ends inside an element");
				default -> {
					// Comments and processing instructions say nothing of the job
				}
			}
		}
	}

	private static void add(ObjectNode parent, String name, JsonNode child) {
		JsonNode there = parent.path(name);
		if (there.isMissingNode()) {
			parent.set(name, child);
		} else if (there instanceof ArrayNode many) {
			many.add(child);
		} else {
			parent.putArray(name).add(there).add(child);
		}
	}

	/**
	 * The JDK's own parser, whatever else the classpath has, with no DTD: an entity can't read a file or an address.
	 */
	private static XMLInputFactory factory() {
		XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		return factory;
	}

}
