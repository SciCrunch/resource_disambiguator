package org.neuinfo.resource.disambiguator.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class PMCSectionExtractionUtils {

	public static void extractAllSectionTitles(String batchNum)
			throws Exception {
		Properties props = Utils
				.loadProperties("resource_disambiguator.properties");

		String indexRootDir = props.getProperty("index.rootdir");
		File baseDir = new File(indexRootDir, "PMC_OAI_" + batchNum);
		assert baseDir.isDirectory();
		File[] journals = baseDir.listFiles();
		int count = 0;
		boolean exitFlag = false;
		for (File journalDir : journals) {
			List<File> papers = new ArrayList<File>();
			getPapers(journalDir, papers);
			for (File paperPath : papers) {
				if (paperPath.getAbsolutePath().indexOf("449-456") != -1) {
					ArticleInfo ai = extractSections(paperPath);
					System.out.println(ai);

					System.out.println("======================");
				}
				count++;
				if (count > 10) {
					exitFlag = true;
					break;
				}
			}
			if (exitFlag) {
				break;
			}
		}
	}

	public static ArticleInfo extractSections(File paperPath) throws Exception {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setValidating(false);
		factory.setFeature(
				"http://apache.org/xml/features/nonvalidating/load-external-dtd",
				false);
		factory.setFeature("http://xml.org/sax/features/validation", false);

		SAXParser parser = factory.newSAXParser();

		XMLReader xmlReader = parser.getXMLReader();
		PMCSectionHandler handler = new PMCSectionHandler(
				paperPath.getAbsolutePath());
		xmlReader.setContentHandler(handler);

		xmlReader.parse(Utils.convertToFileURL(paperPath.getAbsolutePath()));

		return handler.ai;
	}

	public static void getPapers(File journalDir, List<File> paperList) {
		File[] files = journalDir.listFiles();
		for (File f : files) {
			if (f.isDirectory()) {
				getPapers(f, paperList);
			} else {
				paperList.add(f);
			}
		}
	}

	public static class PMCSectionHandler extends DefaultHandler {
		private StringBuilder buf = new StringBuilder(256);
		SectionInfo curSi = null;
		ArticleInfo ai;
		boolean inSec = false, inTitle = false, inJournalTitle = false;
		boolean inArticleTitle = false;

		public PMCSectionHandler(String filePath) {
			ai = new ArticleInfo(filePath);
		}

		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {
			if (localName.equals("sec")) {
				inSec = true;
				curSi = new SectionInfo();
				String type = attributes.getValue("sec-type");
				if (type != null) {
					curSi.type = type;
				}
			} else if (localName.equals("title")) {
				inTitle = true;
			} else if (localName.equals("journal-title")) {
				inJournalTitle = true;
			} else if (localName.equals("article-title")) {
				inArticleTitle = true;
			}
		}

		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			if (localName.equals("sec")) {
				inSec = false;
				if (curSi != null) {
					ai.siList.add(curSi);
				}
				curSi = null;
			} else if (localName.equals("title")) {
				if (inSec) {
					curSi.title = buf.toString().trim();
					if (curSi != null) {
						ai.siList.add(curSi);
					}
					curSi = null;
				}
				// buf.setLength(0);
				inTitle = false;
			} else if (localName.equals("journal-title")) {
				ai.journalTitle = buf.toString().trim();
				// buf.setLength(0);
				inJournalTitle = false;
			} else if (localName.equals("article-title")) {
				ai.title = buf.toString().trim();
				inArticleTitle = false;
			}
			buf.setLength(0);
		}

		@Override
		public void characters(char[] ch, int start, int length)
				throws SAXException {
			if (inTitle || inJournalTitle || inArticleTitle) {
				buf.append(ch, start, length);
			}

		}
	}

	public static class ArticleInfo {
		String filePath;
		String journalTitle;
		String title;
		List<SectionInfo> siList = new ArrayList<SectionInfo>(10);

		public ArticleInfo(String filePath) {
			this.filePath = filePath;
		}

        public String getJournalTitle() {
            return journalTitle;
        }

        public String getTitle() {
            return title;
        }

        @Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ArticleInfo [");
			if (filePath != null) {
				builder.append("filePath=");
				builder.append(filePath);
				builder.append(", ");
			}
			if (journalTitle != null) {
				builder.append("journalTitle=");
				builder.append(journalTitle);
				builder.append(", ");
			}
			if (title != null) {
				builder.append("title=");
				builder.append(title);
				builder.append(", ");
			}
			if (siList != null) {
				for (SectionInfo si : siList) {
					builder.append("\n\t").append(si);
				}
			}
			builder.append("]");
			return builder.toString();
		}

	}// ;

	public static class SectionInfo {
		String type;
		String title;

		public SectionInfo() {
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("SectionInfo [");
			if (title != null) {
				builder.append("title=");
				builder.append(title);
				builder.append(", ");
			}
			if (type != null) {
				builder.append("type=");
				builder.append(type);
			}
			builder.append("]");
			return builder.toString();
		}
	}// ;

	public static void main(String[] args) throws Exception {
		PMCSectionExtractionUtils.extractAllSectionTitles("201310");

	}

}
