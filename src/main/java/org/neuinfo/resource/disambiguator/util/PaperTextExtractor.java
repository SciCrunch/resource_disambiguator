package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.common.CharSetEncoding;
import bnlpkit.util.FileUtils;
import org.apache.commons.cli.*;
import org.neuinfo.resource.disambiguator.services.Paper2TextHandler;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedWriter;

/**
 * Created by bozyurt on 1/21/14.
 */
public class PaperTextExtractor {

    public static void extractText(String inXmlFile, String outTextFile) throws Exception {
         SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        factory.setFeature("http://xml.org/sax/features/validation", false);

        SAXParser parser = factory.newSAXParser();

        XMLReader xmlReader = parser.getXMLReader();
        Paper2TextHandler handler = new Paper2TextHandler(
                inXmlFile, Paper2TextHandler.OpType.NER);
        xmlReader.setContentHandler(handler);

        xmlReader.parse(Utils.convertToFileURL(inXmlFile));

        String text = handler.getText();

        BufferedWriter out = null;
        try {
            out = FileUtils.getBufferedWriter(outTextFile, CharSetEncoding.UTF8);
            out.write(text);
            out.newLine();
            System.out.printf("wrote text file:%s%n", outTextFile);
        } finally {
             FileUtils.close(out);
        }
    }
    public static void usage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("PaperTextExtractor", options);
        System.exit(1);
    }


    public static void main(String[] args) throws Exception {
        Option help = new Option("h", "print this message");
        Option inOption = Option.builder("i").required().hasArg()
                .argName("paper").desc("PMC paper xml").build();
        Option outOption = Option.builder("o").required().hasArg()
                .argName("text").desc("text content file").build();

        Options options = new Options();
        options.addOption(help);
        options.addOption(inOption);
        options.addOption(outOption);
        CommandLineParser cli = new DefaultParser();
        CommandLine line = null;
        try {
            line = cli.parse(options, args);
        } catch (Exception x) {
            System.err.println(x.getMessage());
            usage(options);
        }

        assert line != null;
        if (line.hasOption("h")) {
            usage(options);
        }
        String inXmlFile = line.getOptionValue("i");
        String outTextFile = line.getOptionValue("o");

        PaperTextExtractor.extractText(inXmlFile, outTextFile);
    }

}
