package org.neuinfo.resource.disambiguator.nlp;

import bnlpkit.nlp.common.Node;
import bnlpkit.nlp.common.ParseTreeManager;
import opennlp.tools.cmdline.parser.ParserTool;
import opennlp.tools.parser.Parse;
import opennlp.tools.parser.Parser;
import opennlp.tools.parser.ParserFactory;
import opennlp.tools.parser.ParserModel;
import org.neuinfo.resource.disambiguator.util.Utils;

import java.io.InputStream;

/**
 * Created by bozyurt on 9/20/14.
 */
public class OpenNLPParser implements ISyntacticParser{
    Parser parser;
    int maxLength;
    private boolean verbose = true;

    public OpenNLPParser(int maxLength) throws Exception {
        this.maxLength = maxLength;
        InputStream in = null;
        try {
            in = getClass().getClassLoader().getResourceAsStream("opennlp/models/en-parser-chunking.bin");
            ParserModel model = new ParserModel(in);
            this.parser = ParserFactory.create(model);
        } finally {
            Utils.close(in);
        }
    }

    @Override
    public Node parseSentence(String sentence) throws Exception {
        if (sentence.length() >= maxLength) {
            throw new UnsupportedOperationException("The sentence is too long to parse");
        }
        Parse topParses[] = ParserTool.parseLine(sentence, parser, 1);
        StringBuffer sb = new StringBuffer(sentence.length() * 2);
        topParses[0].show(sb);
        String pt = sb.toString();
        pt = pt.replaceFirst("^\\(TOP", "(S1");
        if (verbose) {
            System.out.println(pt);
        }
        Node rootNode = ParseTreeManager.asParseTree(pt);
        if (verbose) {
            System.out.println(rootNode);
        }
        return rootNode;
    }


    public static void testDrive() throws Exception {
        ISyntacticParser parser = new OpenNLPParser(180);
        String sentence = "The quick brown fox jumps over the lazy dog .";
        sentence = "Fold increase from D10 to D14 ( D14/D10 ) of the proportion of HLA-DR + cells among CD3 − CD56 + , CD3 − CD56 dim and CD3 − CD56 bright natural killer ( NK ) cells ( D ) .";
        parser.parseSentence(sentence);
    }

    public static void main(String[] args) throws Exception {
        testDrive();
    }
}
