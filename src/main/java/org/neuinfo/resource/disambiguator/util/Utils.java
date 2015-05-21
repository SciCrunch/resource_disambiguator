package org.neuinfo.resource.disambiguator.util;

import bnlpkit.util.FileUtils;
import bnlpkit.util.GenUtils;
import com.google.inject.Provider;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author bozyurt
 */
public class Utils {
    private static final Pattern badUrlPattern = Pattern.compile(
            "zip|rar|gz|jpg|jpeg|gif|bz2|pdf", Pattern.CASE_INSENSITIVE);
    private static SimpleDateFormat sdf = new SimpleDateFormat("MMddyyyy");

    public static Properties loadProperties(String propsFilename)
            throws IOException {
        InputStream is = Utils.class.getClassLoader().getResourceAsStream(
                propsFilename);
        if (is == null) {
            throw new IOException(
                    "Cannot find properties file in the classpath:"
                            + propsFilename);
        }
        Properties props = new Properties();
        props.load(is);

        return props;
    }

    public static void close(Reader in) {
        try {
            in.close();
        } catch (Exception x) {
            // ignore
        }
    }

    public static void close(Writer out) {
        try {
            out.close();
        } catch (Exception x) {
            // ignore
        }
    }

    public static void close(InputStream in) {
        try {
            in.close();
        } catch (Exception x) {
            // ignore
        }
    }

    public static void close(OutputStream os) {
        try {
            os.close();
        } catch (Exception x) {
            // no op
        }
    }

    public static String prepBatchId() {
        return sdf.format(new Date());
    }

    public static BufferedWriter newUTF8CharSetWriter(String filename)
            throws IOException {
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(
                filename), Charset.forName("UTF-8")));
    }

    public static BufferedReader newUTF8CharSetReader(String filename)
            throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(
                filename), Charset.forName("UTF-8")));
    }

    /**
     * replacement for Java_download.java (IBO)
     *
     * @param file
     * @param urlStr
     * @throws Exception
     */
    public static void loadURLContent(String file, String urlStr)
            throws Exception {
        BufferedReader in = null;
        BufferedWriter out = null;
        try {
            URL url = new URL(urlStr);
            in = new BufferedReader(new InputStreamReader(url.openStream()));
            out = newUTF8CharSetWriter(file);
            String line = null;
            while ((line = in.readLine()) != null) {
                out.write(line);
                out.newLine();
            }

        } finally {
            close(in);
            close(out);
        }
    }

    public static String getMD5Checksum(String filePath) throws Exception {
        byte[] barr = createMD5Checksum(filePath);
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < barr.length; i++) {
            sb.append(Integer.toString((barr[i] & 0xff) + 0x100, 16).substring(
                    1));
        }
        return sb.toString();
    }

    public static String getMD5ChecksumOfString(String text) throws Exception {
        byte[] barr = createMD5ChecksumOfString(text);
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < barr.length; i++) {
            sb.append(Integer.toString((barr[i] & 0xff) + 0x100, 16).substring(
                    1));
        }
        return sb.toString();
    }

    public static byte[] createMD5ChecksumOfString(String text)
            throws Exception {
        byte[] buffer = text.getBytes("UTF-8");
        MessageDigest md = MessageDigest.getInstance("MD5");
        return md.digest(buffer);
    }

    public static byte[] createMD5Checksum(String filePath) throws Exception {
        BufferedInputStream in = null;
        byte[] buffer = new byte[4096];
        MessageDigest md = MessageDigest.getInstance("MD5");
        try {
            int nr = 0;
            in = new BufferedInputStream(new FileInputStream(filePath));
            while ((nr = in.read(buffer)) > 0) {
                md.update(buffer, 0, nr);
            }
            return md.digest();
        } finally {
            close(in);
        }
    }

    public static boolean isHtmlPage(String urlStr) {
        return !badUrlPattern.matcher(urlStr).find()
                && !urlStr.startsWith("mail");
    }

    public static void closeEntityManager(EntityManager em) {
        try {
            if (em != null && em.isOpen()) {
                em.close();
            }
        } catch (Exception e) {
        }
    }

    public static void beginTransaction(EntityManager em) throws Exception {
        EntityTransaction txn = em.getTransaction();
        if (!txn.isActive())
            txn.begin();

    }

    public static void commitTransaction(EntityManager em) throws Exception {
        EntityTransaction txn = null;
        try {
            txn = em.getTransaction();
            if (txn.isActive())
                txn.commit();
        } catch (Exception e) {
            e.printStackTrace();
            if (txn != null && txn.isActive())
                txn.rollback();
            throw e;
        }
    }

    public static void rollbackTransaction(EntityManager em) {
        EntityTransaction txn = null;
        try {
            txn = em.getTransaction();
            if (txn.isActive())
                txn.rollback();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static EntityManager getEntityManager(
            Provider<EntityManager> emFactory) {
        EntityManager em = emFactory.get();

        if (!em.isOpen()) {
            EntityManagerFactory entityManagerFactory = em
                    .getEntityManagerFactory();
            closeEntityManager(em);
            em = entityManagerFactory.createEntityManager();
        }
        return em;
    }

    public static boolean isValidURLFormat(String urlCandidate) {
        try {
            URL url = new URL(urlCandidate);
            url.getHost();
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public static String convertToFileURL(String filename) {
        String path = new File(filename).getAbsolutePath();
        if (File.separatorChar != '/') {
            path = path.replace(File.separatorChar, '/');
        }
        path = path.replaceAll("%20","\\%20");

        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return "file:" + path;
    }

    public static String normalizeUrl(String url) {
        url = url.trim().toLowerCase();
        if (url.endsWith("/")) {
            url = url.replaceFirst("/$", "");
        }
        if (!url.startsWith("http")) {
            url = "http://" + url;
        }
        return url;
    }

    public static String[] normalizeUrls(String commaSeparatedURLStr) {
        String[] urls = commaSeparatedURLStr.split("\\s*,\\s*");
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i];
            url = url.trim().toLowerCase();
            if (url.endsWith("/")) {
                url = url.replaceFirst("/$", "");
            }
            if (!url.startsWith("http")) {
                url = "http://" + url;
            }
            urls[i] = url;
        }
        return urls;
    }

    public static int numOfMatches(String url, char c) {
        int count = 0;
        char[] carr = url.toCharArray();
        for (int i = 0; i < carr.length; i++) {
            if (carr[i] == c) {
                count++;
            }
        }
        return count;
    }

    public static void saveText(String text, File outFile) throws IOException {
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(outFile), Charset.forName("UTF-8")));
            out.write(text);
            out.newLine();
        } finally {
            close(out);
        }
    }

    public static String toFileName(String url) {
        return url.replaceAll("[\\.!\\?,;:\\-&='\"()\\[\\]\\s/]+", "_");
    }

    public static Calendar getStartOfMonth() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c;
    }

    static Pattern urlPattern = Pattern.compile(
            "((([A-Za-z]{3,9}:(?://)?)(?:[-;:&=\\+\\$,\\w]+@)?[A-Za-z0-9.-]+|(?:www.|[-;:&=\\+\\$,\\w]+@)[A-Za-z0-9.-]+)((?:/[\\+~%/.\\w_-]*)?\\??(?:[-\\+=&;%@.\\w_]*)#?(?:[\\w]*))?)");

    public static String extractUrl(String str) {
        Matcher m = urlPattern.matcher(str);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    public static String normalizeWS(String s) {
        s = s.replaceAll("[ \\xA0\\t]{2,}", " ");
        s = s.replaceAll("(?: \\xA0\\n)+", "\n");
        s = s.replaceAll("\\n{2,}", "\n");
        return s;
    }

    /**
     * @param s a string where all the multiple occurrences of white space
     *          including newlines are normalized to single space
     * @return
     */
    public static String normalizeAllWS(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        char[] carr = s.toCharArray();
        boolean inWS = false;
        for (int i = 0; i < carr.length; i++) {
            if (Character.isWhitespace(carr[i]) ||
                    Character.isISOControl(carr[i]) || ((int) carr[i] == 160)) {
                if (!inWS) {
                    sb.append(' ');
                }
                inWS = true;
            } else {
                inWS = false;
                sb.append(carr[i]);
            }
        }
        return sb.toString();
    }

    public static String stripHTML(String content) {
        return content.replaceAll("<[^>]+>", "");
    }

    public static void save2File(InputStream in, String outFile) throws IOException {
        BufferedWriter out = null;
        BufferedReader bin = null;
        try {
            out = newUTF8CharSetWriter(outFile);
            bin = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = bin.readLine()) != null) {
                out.write(line);
                out.newLine();
            }

        } finally {
            close(bin);
            close(out);
        }
    }

    static Pattern fileSuffixPattern = Pattern.compile("\\.\\w+$");

    public static String prepFrameAbsUrl(String frameSrc, URL url) {
        if (frameSrc.startsWith("http")) {
            return frameSrc;
        }
        String urlStr = url.toString();
        String path = url.getPath();
        Matcher matcher = fileSuffixPattern.matcher(path);
        if (matcher.find()) {
            int idx = urlStr.lastIndexOf('/');
            String prefix = urlStr.substring(0, idx);
            if (frameSrc.endsWith("/")) {
                return prefix + frameSrc;
            } else {
                return prefix + "/" + frameSrc;
            }
        }
        frameSrc = frameSrc.replaceFirst("/$", "");
        if (urlStr.endsWith("/")) {
            return urlStr + frameSrc;
        } else {
            return urlStr + '/' + frameSrc;
        }
    }

    public static boolean isEmpty(String s) {
        return s == null || s.trim().length() == 0;

    }

    public static String filterNonUTF8(String content) {
        //CharsetDecoder utf8Decoder = Charset.forName("UTF-8").newDecoder();
        //utf8Decoder.onMalformedInput(CodingErrorAction.IGNORE);
        //utf8Decoder.onUnmappableCharacter(CodingErrorAction.IGNORE);
        // utf8Decoder.decode():
        // TODO
        content = content.replaceAll("([\\ud800-\\udbff\\udc00-\\udfff])", "");
        return content;
    }

    public static int levenshteinDistance(char[] string1, char[] string2) {
        int d[][] = new int[string1.length + 1][string2.length + 1];
        for (int i = 0; i <= string1.length; i++)
            d[i][0] = i;
        for (int i = 0; i <= string2.length; i++)
            d[0][i] = i;
        int m = string1.length;
        int n = string2.length;
        int deletion;
        int insertion;
        int substitution;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = string1[i - 1] == string2[j - 1] ? 0 : 1;
                deletion = d[i - 1][j] + 1;
                insertion = d[i][j - 1] + 1;
                substitution = d[i - 1][j - 1] + cost;
                d[i][j] = Math.min(deletion, Math.min(insertion, substitution));
            }
        }
        return d[m][n];
    }


    public static float levenshteinDistance(char[] string1, char[] string2, ICostFunction costFunction) {
        float d[][] = new float[string1.length + 1][string2.length + 1];
        for (int i = 0; i <= string1.length; i++) {
            d[i][0] = i;
        }
        for (int i = 0; i <= string2.length; i++) {
            d[0][i] = i;
        }
        int m = string1.length;
        int n = string2.length;
        float deletion;
        float insertion;
        float substitution;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                float cost = costFunction.calcCost(string1[i-1], string2[j-1]);
                //int cost = string1[i - 1] == string2[j - 1] ? 0 : 1;
                deletion = d[i - 1][j] + 1;
                insertion = d[i][j - 1] + 1;
                substitution = d[i - 1][j - 1] + cost;
                d[i][j] = Math.min(deletion, Math.min(insertion, substitution));
            }
        }
        return d[m][n];
    }

    public static boolean isSame(String refValue, String otherValue) {
        if (isEmpty(refValue) && isEmpty(otherValue)) {
            return true;
        }
        if (!isEmpty(refValue) && !isEmpty(otherValue)) {
            return refValue.equalsIgnoreCase(otherValue);
        }
        return false;
    }

    public static interface  ICostFunction {
        public float calcCost(char c1, char c2);
    }

    public static class TransliterationCostFunction implements ICostFunction {
        public float calcCost(char c1, char c2) {
            float cost = c1 == c2 ? 0f : 1f;
            if (cost > 0) {
                if (c1 == '-' || c2 == '-' || c1 == '\'' || c2 == '\'') {
                    cost = 0.5f;
                } else if (c1 == ' ' || c2 == ' ') {
                    cost = 0.25f;
                }
            }
            return cost;
        }
    }

    public static int levenshteinDistance2(String[] string1, String[] string2) {
        int d[][] = new int[string1.length + 1][string2.length + 1];
        for (int i = 0; i <= string1.length; i++)
            d[i][0] = i;
        for (int i = 0; i <= string2.length; i++)
            d[0][i] = i;
        int m = string1.length;
        int n = string2.length;
        int deletion;
        int insertion;
        int substitution;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int cost = string1[i - 1].equals(string2[j - 1]) ? 0 : 1;
                deletion = d[i - 1][j] + 1;
                insertion = d[i][j - 1] + 1;
                substitution = d[i - 1][j - 1] + cost;
                d[i][j] = Math.min(deletion, Math.min(insertion, substitution));
            }
        }
        return d[m][n];
    }

    public static void saveXML(Element rootElem, String filename,
                               boolean useLatinCharset) throws IOException, JDOMException {
        BufferedWriter out = null;
        try {
            if (useLatinCharset) {
                out = FileUtils.newLatin1CharSetWriter(filename);
            } else {
                out = FileUtils.newUTF8CharSetWriter(filename);
            }
            XMLOutputter xout = new XMLOutputter(Format.getPrettyFormat());
            xout.output(rootElem, out);
        } finally {
            FileUtils.close(out);
        }
    }

    public static Element loadXML(String xmlFile) throws Exception {
        SAXBuilder builder = new SAXBuilder(false);
        BufferedReader in = null;
        Element root = null;
        try {
            in = FileUtils.newUTF8CharSetReader(xmlFile);
            Document doc = builder.build(in);
            root = doc.getRootElement();
        } finally {
            FileUtils.close(in);
        }
        return root;
    }

    public static double mean(double[] data) {
        double sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += data[i];
        }
        return sum / data.length;
    }

    public static double variance(double[] data, double mean) {
        double sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += (data[i] - mean) * (data[i] - mean);
        }
        return sum / (data.length - 1);
    }

    public static void main(String[] args) {
        String s = "http://phm.utoronto.ca/~jeffh/neuromouse.htm X NeuroMouse homepage NeuroMouse Homepage Introduction: This page provides information on distribution of the current version of NeuroMouse , an interactive ToolBook-based, object-oriented database of   murine neurologic information. This system provides an integrated resource for the characterization and description of mammalian neurologic data. Major divisions include: Neural Atlas, Molecular Atlas, Genetics/Surgical Lesion Atlas. Neuromouse has been integrated into our strain-specific three dimensional MRI and surgical atlases of the murine CNS. The development of the public AERD - Antibody / Epitope Registry Database , is also an outgrowth of the NeuroMouse project. The Murine Imaging and Histology Core facility maintains components of the NeuroMouse database for use by University investigators. Version 6.0e is the current version of NeuroMouse used within the core facility. An interactive example of key proteins / interactions involved in the process of programmed cell death (apoptosis) can be seen here (Jmol) . What is NeuroMouse? / Database Contents System requirements NM ordering and updates / Tech and legal issues NM Demo (outline of old NM version 1.0) For those wishing to contribute Frequently asked questions (FAQs) and trouble shooting                                                       Migrating granule cells                                                                         Beating myocytes                                 (Neurophilosopher's weblog)                                                                       Cells Alive)         MIH Homepage         Henderson Laboratory Home Page          Send mail to Dr. Henderson: jeff.henderson@utoronto.ca";
        s = "trouble shooting                                                       Migrating granule cells                                                                         Beating myocytes                                 (Neurophilosopher's weblog)  ";

        System.out.println(GenUtils.formatText(s, 200));
        String s1 = Utils.normalizeAllWS(s);
        System.out.println(GenUtils.formatText(s1, 200));


    }
}
