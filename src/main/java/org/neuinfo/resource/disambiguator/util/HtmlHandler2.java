package org.neuinfo.resource.disambiguator.util;

import org.neuinfo.resource.disambiguator.util.HtmlContentExtractor.FrameInfo;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bozyurt on 8/7/14.
 */
public class HtmlHandler2 extends DefaultHandler {
    private boolean inFrameSet = false;
    private boolean inP = false;
    private boolean inTitle = false;
    private boolean inScript = false;
    private boolean inAnchor = false;
    private boolean inStyle = false;
    private boolean inTD = false;
    private boolean inLI = false;
    private StringBuilder paraBuf = new StringBuilder(4096);
    private StringBuilder otherBuf = new StringBuilder(4096);
    private StringBuilder sb = new StringBuilder(4096);
    private StringBuilder descriptionBuf = new StringBuilder(4096);
    private StringBuilder titleBuf = new StringBuilder(256);
    private StringBuilder totBuf = new StringBuilder(4096);
    private String curHref;
    private StringBuilder anchorBuf = new StringBuilder(200);
    private StringBuilder tagBuf = new StringBuilder(200);
    private List<FrameInfo> frames = new ArrayList<FrameInfo>(4);

    private String aboutLink;
    private String pageURL;
    private String redirectUrl;
    private final Pattern redirectRegex = Pattern.compile("URL=(.+)", Pattern.CASE_INSENSITIVE);
    private List<ContentSection> sectionList = new LinkedList<ContentSection>();

    public HtmlHandler2(String pageURL) {
        this.pageURL = pageURL;
    }


    public List<ContentSection> getSectionList() {
        return sectionList;
    }

    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {
        localName = localName.toLowerCase();
        if (localName.equals("p")) {
            inP = true;
            addOtherContentSection();
            if (paraBuf.length() > 0) {
                ContentSection cs = new ContentSection(paraBuf.toString().trim(), ContentSection.PARA);
                sectionList.add(cs);
            }
            paraBuf.setLength(0);
        } else if (localName.equals("title")) {
            inTitle = true;
            addOtherContentSection();
        } else if (localName.equals("script")) {
            inScript = true;
        } else if (localName.equals("a")) {
            inAnchor = true;
            curHref = attributes.getValue("href");
        } else if (localName.equals("style")) {
            inStyle = true;
        } else if (localName.equals("frameset")) {
            inFrameSet = true;
        } else if (localName.equals("frame")) {
            String frameSrc = attributes.getValue("src");
            String frameName = attributes.getValue("name");
            if (frameSrc != null) {
                FrameInfo fi = new FrameInfo(frameSrc, frameName);
                this.frames.add(fi);
            }
        } else if (localName.equals("meta")) {
            String value = attributes.getValue("http-equiv");
            if (value != null && value.equalsIgnoreCase("refresh")) {
                String contentValue = attributes.getValue("content");
                if (contentValue != null) {
                    Matcher matcher = redirectRegex.matcher(contentValue);
                    if (matcher.find()) {
                        this.redirectUrl = matcher.group(1);
                    }
                }
            }
        } else if (localName.equals("li")) {
            inLI = true;
            addOtherContentSection();
        } else if (localName.equals("td")) {
            inTD = true;
            addOtherContentSection();
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        localName = localName.toLowerCase();
        if (localName.equals("title")) {
            inTitle = false;
            String titleContent = titleBuf.toString().trim();
            if (!isEmptyString(titleContent)) {
                ContentSection cs = new ContentSection(titleContent, ContentSection.TITLE);
                sectionList.add(cs);
            }
        } else if (localName.equals("script")) {
            inScript = false;
        } else if (localName.equals("a")) {
            if (aboutLink == null && curHref != null && pageURL != null
                    && !curHref.equalsIgnoreCase(pageURL)) {
                String anchorText = anchorBuf.toString();
                if (anchorText.toLowerCase().indexOf("about") != -1
                        || curHref.toLowerCase().indexOf("about") != -1) {
                    if (!isBadURL(curHref)) {
                        prepareAboutLink(curHref);
                    }
                }
            }
            inAnchor = false;
            anchorBuf.setLength(0);
        } else if (localName.equals("style")) {
            inStyle = false;
        } else if (localName.equals("li")) {
            inLI = false;
            addAsSentence2ContentBuffer(tagBuf);
            tagBuf.setLength(0);
        } else if (localName.equals("td")) {
            inTD = false;
            addAsSentence2ContentBuffer(tagBuf);
            tagBuf.setLength(0);
        } else if (localName.equals("p")) {
            addSentenceEnd2ContentBuffer();
            inP = false;
        }
    }

    void prepareAboutLink(String curHref) {
        if (!curHref.startsWith("http")) {
            String s;
            if (curHref.startsWith("/")) {
                s = curHref.replaceFirst("^/+", "");
            } else {
                s = curHref;
            }
            String prefix = pageURL;
            if (pageURL.endsWith("/")) {
                prefix = prefix.replaceFirst("/+$", "");
            } else if (pageURL.endsWith(".html") || pageURL.endsWith(".htm")) {
                int idx = prefix.lastIndexOf('/');
                if (idx != -1) {
                    prefix = prefix.substring(0, idx);
                }
            }
            this.aboutLink = prefix + "/" + s;
        } else {
            try {
                URL pu = new URL(pageURL);
                URL u = new URL(curHref);
                if (pu.getHost().equals(u.getHost())) {
                    this.aboutLink = curHref;
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }

    void addOtherContentSection() {
        String otherContent = otherBuf.toString().trim();
        if (!isEmptyString(otherContent)) {
            ContentSection cs = new ContentSection(otherContent, ContentSection.UNKNOWN);
            sectionList.add(cs);
        }
        otherBuf.setLength(0);
    }


    void addSentenceEnd2ContentBuffer() {
        int lastNonWSCharIndex = getLastNonWSCharIndex(totBuf);
        if (lastNonWSCharIndex >= 0) {
            if (!isPossibleEOS(totBuf.charAt(lastNonWSCharIndex))) {
                totBuf.append(" . ");
            }
        }
        String paraContent = paraBuf.toString().trim();
        if (!isEmptyString(paraContent)) {
            ContentSection cs = new ContentSection(paraContent, ContentSection.PARA);
            sectionList.add(cs);
        }
        paraBuf.setLength(0);
    }

    void addAsSentence2ContentBuffer(StringBuilder buf) {
        String sentence = buf.toString().trim();
        if (sentence.length() > 0) {
            int lastNonWSCharIndex = getLastNonWSCharIndex(totBuf);
            if (lastNonWSCharIndex >= 0) {
                if (!isPossibleEOS(totBuf.charAt(lastNonWSCharIndex))) {
                    totBuf.append(" . ");
                }
            }
            char lastChar = sentence.charAt(sentence.length() - 1);
            totBuf.append(sentence).append(' ');
            if (!isPossibleEOS(lastChar)) {
                totBuf.append(". ");
            }
        }
        String content = buf.toString().trim();
        if (!isEmptyString(content)) {
            ContentSection cs = new ContentSection(content, ContentSection.INFERRED);
            sectionList.add(cs);
        }
    }

    public static boolean isEmptyString(String content) {
        if (!content.isEmpty()) {
            if (content.indexOf(160) != -1) {
                content = content.replaceAll("\\xA0","");
                return content.isEmpty();
            }
            return false;
        }
        return true;
    }
    public static boolean isBadURL(String url) {
        url = url.toLowerCase();
        return ((url.indexOf(".pdf") != -1) || (url.indexOf(".jpg") != -1)
                || (url.indexOf(".gif") != -1) || (url.indexOf(".bmp") != -1)
                || (url.indexOf(".ppt") != -1) || (url.indexOf(".doc") != -1));
    }

    public static boolean isPossibleEOS(char c) {
        return c == '.' || c == '?' || c == '!' || c == ':' || c == ';';
    }

    public static int getLastNonWSCharIndex(StringBuilder sb) {
        int idx = sb.length() - 1;
        while (idx >= 0) {
            if (!Character.isWhitespace(sb.charAt(idx))) {
                break;
            }
            idx--;
        }
        return idx;
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        // System.out.println(new String(ch));
        if (!inScript && !inStyle) {
            if (inTD || inLI) {
                tagBuf.append(ch, start, length).append(' ');
            } else {
                totBuf.append(ch, start, length).append(' ');
                if (!inTitle) {
                    otherBuf.append(ch, start, length).append(' ');
                }
            }
        }
        if (inP) {
            sb.append(ch, start, length).append(' ');
            paraBuf.append(ch, start, length).append(' ');
        } else if (inTitle) {
            titleBuf.append(ch, start, length).append(' ');
        } else if (inAnchor) {
            anchorBuf.append(ch, start, length);
        }
    }

    public String getDescription() {
        String d = descriptionBuf.toString().trim();
        if (d.length() > 0) {
            d = d.replaceAll("\\s+", " ");
        }
        return d;
    }

    public String getTitle() {
        return titleBuf.toString().trim();
    }

    public String getContent() {
        String s = totBuf.toString().trim();
        s = Utils.normalizeWS(s);
        s = s.replaceAll("(?:\\s*\\n)+", "\n");
        return s;
    }

    public String getAboutLink() {
        return aboutLink;
    }


    public String getRedirectUrl() {
        return redirectUrl;
    }

    public List<FrameInfo> getFrames() {
        return frames;
    }


    public static class ContentSection {
        final String content;
        final int type;
        public final static int UNKNOWN = 0;
        public final static int INFERRED = 1; /* inferred sentence/section boundaries */
        public final static int PARA = 2;
        public final static int TITLE = 3;

        public ContentSection(String content, int type) {
            content = Utils.normalizeWS(content);
            content = content.replaceAll("(?:[\\s\\xA0]*\\r?\\n)+", "\n");
            content = content.replaceAll("\\r?\\n[ \\xA0]+", "\n");
            this.content = content;
            this.type = type;
        }

        public String getContent() {
            return content;
        }

        public int getType() {
            return type;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(content.length() + 20);
            String prefix;
            switch (type) {
                case UNKNOWN:
                    prefix = "<UNK>";
                    break;
                case INFERRED:
                    prefix = "<INF>";
                    break;
                case PARA:
                    prefix = "<PAR>";
                    break;
                default:
                    prefix = "<TTL>";
            }
            sb.append(prefix).append('\t').append(content);
            return sb.toString();
        }
    }
}

