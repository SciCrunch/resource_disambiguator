package org.neuinfo.resource.disambiguator.nlp;

import bnlpkit.nlp.common.Node;
import bnlpkit.nlp.common.ParseTreeManager;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.trees.Tree;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by bozyurt on 9/20/14.
 */
public class StanfordLexicalParser implements ISyntacticParser {
    private int maxLength;
    private LexicalizedParser lp;
    private boolean verbose = true;
    private Pattern nodePattern = Pattern.compile("(\\([^ ()]+ [()]\\))");


    public StanfordLexicalParser(int maxLength) {
        this.maxLength = maxLength;
        String grammar = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";
        String[] options = {"-maxLength", String.valueOf(maxLength), "-retainTmpSubcategories"};
        this.lp = LexicalizedParser.loadModel(grammar, options);
    }

    @Override
    public Node parseSentence(String sentence) throws Exception {
        String[] toks = sentence.split("\\s+");
        List<HasWord> sentenceToks = new ArrayList<HasWord>(toks.length);
        for (String tok : toks) {
            sentenceToks.add(new Word(tok));
        }
        Tree tree = lp.parseTree(sentenceToks);
        String pt = tree.pennString().replaceAll("[\\n ]+", " ");
        pt = pt.replaceFirst("^\\(ROOT", "(S1");

        Matcher matcher = nodePattern.matcher(pt);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String group = matcher.group(1);
            group = escapeParens(group);
            matcher.appendReplacement(sb, group);
        }
        matcher.appendTail(sb);
        pt = sb.toString();
        if (verbose) {
            System.out.println("pt:" + pt);
        }
        Node rootNode = ParseTreeManager.asParseTree(pt);
        if (verbose) {
            System.out.println(rootNode);
        }
        return rootNode;
    }

    public static String escapeParens(String capturedGroup) {
        String[] toks = capturedGroup.split("\\s+");
        StringBuilder sb = new StringBuilder(toks[0]).append(' ');
        if (toks[1].charAt(0) == '(') {
            sb.append("-LRB-").append(toks[1].substring(1));
        } else if (toks[1].charAt(0) == ')') {
            sb.append("-RRB-").append(toks[1].substring(1));
        }
        return sb.toString();
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
