package org.neuinfo.resource.disambiguator.util;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author bozyurt
 */
public class HtmlSaxHandler extends DefaultHandler {
    private boolean inP = false;
    private boolean inTitle = false;
    private boolean inScript = false;
    private boolean inAnchor = false;
    private boolean inStyle = false;
    private StringBuilder sb = new StringBuilder(4096);
    private StringBuilder descriptionBuf = new StringBuilder(4096);
    private Pattern pattern = Pattern.compile("is (a|an)");
    private StringBuilder titleBuf = new StringBuilder(256);
    private StringBuilder totBuf = new StringBuilder(4096);
    private String curHref;
    private StringBuilder anchorBuf = new StringBuilder(200);
    private OpMode opMode = OpMode.DESCR;
    private String aboutLink;
    private String pageURL;
    private String redirectUrl;
    private final Pattern redirectRegex = Pattern.compile("URL=(.+)", Pattern.CASE_INSENSITIVE);

    public HtmlSaxHandler(OpMode opMode, String pageURL) {
        this.opMode = opMode;
        this.pageURL = pageURL;
    }

    public HtmlSaxHandler(OpMode opMode) {
        this.opMode = opMode;
    }

    @Override
    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {
        // System.out.println(localName);
        //FIXME handle manual redirect
        if (localName.equalsIgnoreCase("p")) {
            inP = true;
        } else if (localName.equalsIgnoreCase("title")) {
            inTitle = true;
        } else if (localName.equalsIgnoreCase("script")) {
            inScript = true;
        } else if (localName.equalsIgnoreCase("a")) {
            inAnchor = true;
            curHref = attributes.getValue("href");
        } else if (localName.equalsIgnoreCase("style")) {
            inStyle = true;
        } else if (localName.equalsIgnoreCase("meta")) {
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
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (localName.equalsIgnoreCase("p")) {
            inP = false;
            String content = sb.toString().trim();
            if (content.length() > 0) {
                if (opMode == OpMode.DESCR_FROM_ABOUT_PAGE) {
                    descriptionBuf.append(' ').append(content);
                } else if (opMode == OpMode.DESCR) {
                    String lcContent = content.toLowerCase();
                    if (lcContent.indexOf("introduction") != -1
                            || lcContent.indexOf("background") != -1
                            || pattern.matcher(lcContent).find()) {
                        descriptionBuf.append(' ').append(content);
                    }
                }
            }
            sb.setLength(0);
        } else if (localName.equalsIgnoreCase("title")) {
            inTitle = false;
        } else if (localName.equalsIgnoreCase("script")) {
            inScript = false;
        } else if (localName.equalsIgnoreCase("a")) {
            if (aboutLink == null && curHref != null && pageURL != null
                    && !curHref.equalsIgnoreCase(pageURL)) {
                String anchorText = anchorBuf.toString();
                if (anchorText.toLowerCase().indexOf("about") != -1
                        || curHref.toLowerCase().indexOf("about") != -1) {
                    if (!isBadURL(curHref)) {
                        if (!curHref.startsWith("http")) {
                            String s;
                            if (curHref.startsWith("/")) {
                                s = curHref.replaceFirst("^/+", "");
                            } else {
                                s = curHref;
                            }
                            this.aboutLink = pageURL + "/" + s;
                        } else {
                            this.aboutLink = curHref;
                        }
                    }
                }
            }
            inAnchor = false;
            anchorBuf.setLength(0);
        } else if (localName.equalsIgnoreCase("style")) {
            inStyle = false;
        }
    }

    public static boolean isBadURL(String url) {
        url = url.toLowerCase();
        return ((url.indexOf(".pdf") != -1) || (url.indexOf(".jpg") != -1)
                || (url.indexOf(".gif") != -1) || (url.indexOf(".bmp") != -1)
                || (url.indexOf(".ppt") != -1) || (url.indexOf(".doc") != -1));
    }

    @Override
    public void characters(char[] ch, int start, int length)
            throws SAXException {
        // System.out.println(new String(ch));
        if (opMode == OpMode.FULL_CONTENT) {
            if (!inScript && !inStyle) {
                totBuf.append(ch, start, length).append(' ');
            }
        }
        if (inP) {
            sb.append(ch, start, length).append(' ');
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
}
