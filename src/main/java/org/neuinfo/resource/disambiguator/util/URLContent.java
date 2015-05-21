package org.neuinfo.resource.disambiguator.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Encapsulates data extracted from the content of the URL and redirection info
 * if any.
 * 
 * @author bozyurt
 * 
 */
public class URLContent {
	private final String url;
	private final URI finalRedirectURI;
	private final String title;
	private final String description;
	private final String content;

	public URLContent(String url, URI finalRedirectURI, String title,
			String description, String content) {
		super();
		this.url = url;
		this.finalRedirectURI = finalRedirectURI;
		this.title = title;
		this.description = description;
		this.content = content;
	}

	public String getUrl() {
		return url;
	}

	public URI getFinalRedirectURI() {
		return finalRedirectURI;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getContent() {
		return content;
	}

	public void toXml(File xmlFile) throws Exception {
		
		OutputStream os = null;
		try {
			os = new FileOutputStream(xmlFile);
			XMLStreamWriter out = XMLOutputFactory.newInstance()
					.createXMLStreamWriter(new OutputStreamWriter(os, "utf-8"));
			out.writeStartDocument();
			out.writeStartElement("doc");
			out.writeAttribute("url", url);
			if (finalRedirectURI != null) {
				out.writeAttribute("redirect-url", finalRedirectURI.toString());
			}
			out.writeStartElement("title");
			if (title != null) {
				out.writeCharacters(title);
			}
			out.writeEndElement();

			out.writeStartElement("description");
			if (description != null) {
				out.writeCharacters(description);
			}
			out.writeEndElement();

			out.writeStartElement("content");
			if (content != null) {
				out.writeCharacters(content);
			}
			out.writeEndElement();

			out.writeEndElement();
			out.writeEndDocument();
			out.flush();
		} finally {
			Utils.close(os);
		}
	}

	public static URLContent fromXml(File xmlFile) throws Exception {
		SaxHandler handler = new SaxHandler();
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser saxParser = factory.newSAXParser();

		saxParser.parse(xmlFile, handler);

		return handler.getURLContent();
	}

	static class SaxHandler extends DefaultHandler {
		private StringBuilder descriptionBuf = new StringBuilder(4096);
		private boolean inTitle = false;
		private boolean inDesc = false;
		private boolean inContent = false;
		private StringBuilder titleBuf = new StringBuilder(256);
		private StringBuilder totBuf = new StringBuilder(4096);
		private String url;
		private URI finalRedirectURI;

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {
			super.startElement(uri, localName, qName, attributes);
			if (localName.equals("title")) {
				inTitle = true;
			} else if (localName.equals("doc")) {
				url = attributes.getValue("url");
				if (attributes.getIndex("redirect-url") != -1) {
					try {
						finalRedirectURI = new URI(
								attributes.getValue("redirect-url"));
					} catch (URISyntaxException e) {
						e.printStackTrace();
						finalRedirectURI = null;
					}
				}
			} else if (localName.equals("description")) {
				inDesc = true;
			} else if (localName.equals("content")) {
				inContent = true;
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			if (localName.equals("title")) {
				inTitle = false;
			} else if (localName.equals("description")) {
				inDesc = false;
			} else if (localName.equals("content")) {
				inContent = false;
			}
		}

		@Override
		public void characters(char[] ch, int start, int length)
				throws SAXException {
			if (inContent) {
				totBuf.append(ch, start, length);
			} else if (inDesc) {
				descriptionBuf.append(ch, start, length);
			} else if (inTitle) {
				titleBuf.append(ch, start, length);
			}
		}

		public URLContent getURLContent() {
			URLContent uc = new URLContent(url, finalRedirectURI,
					titleBuf.toString(), descriptionBuf.toString(),
					totBuf.toString());

			return uc;
		}
	}// ;

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("URLContent [");
		if (url != null) {
			builder.append("url=");
			builder.append(url);
			builder.append(", ");
		}
		if (finalRedirectURI != null) {
			builder.append("finalRedirectURI=");
			builder.append(finalRedirectURI);
			builder.append(", ");
		}
		if (title != null) {
			builder.append("title=");
			builder.append(title);
			builder.append(", ");
		}
		if (description != null) {
			builder.append("description=");
			builder.append(description);
			builder.append(", ");
		}
		if (content != null) {
			builder.append("content=");
			builder.append(content);
		}
		builder.append("]");
		return builder.toString();
	}

}