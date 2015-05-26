package org.neuinfo.resource.disambiguator.util;

import bnlpkit.nlp.common.CharSetEncoding;
import bnlpkit.nlp.common.index.*;
import bnlpkit.util.FileUtils;
import bnlpkit.util.GenUtils;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import org.jdom.Comment;
import org.neuinfo.resource.disambiguator.persist.JPAInitializer;
import org.neuinfo.resource.disambiguator.persist.RDPersistModule;
import org.neuinfo.resource.disambiguator.persist.bindings.IndicatesPrimaryJpa;
import org.neuinfo.resource.disambiguator.util.DocPreprocessor;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bozyurt on 7/29/14.
 */
public class NERTrainingDataPreparer {
    @Inject
    @IndicatesPrimaryJpa
    protected Provider<EntityManager> emFactory;
    POSTaggerME tagger;

    public void handle(String outIdxXmlFile) throws Exception {
        InputStream pin = null;
        POSModel model;
        try {
            pin = getClass().getClassLoader().getResourceAsStream(
                    "opennlp/models/en-pos-maxent.bin");
            model = new POSModel(pin);
            this.tagger = new POSTaggerME(model);
        } finally {
            FileUtils.close(pin);
        }
        EntityManager em = null;
        try {
            em = Utils.getEntityManager(emFactory);
            Query query = em.createQuery("select p.pubmedId, r.context, r.startIdx, r.endIdx, n.label " +
                    "from NERAnnotationInfo n inner join n.resourceRec r inner join  r.paper p " +
                    "where n.label = 'good' or n.label = 'bad'");
            final List<?> resultList = query.getResultList();
            FileInfo fi = new FileInfo("", "", "");
            Map<String, DocumentInfo> diMap = new HashMap<String, DocumentInfo>();
            int docIdx = 1;
            int sentIdx = 1;
            for (Object o : resultList) {
                Object[] row = (Object[]) o;
                if (row[0] == null) {
                    System.out.println("No PMID! skipping...");
                    continue;
                }
                String pubmedId = row[0].toString();
                String sentence = row[1].toString();
                String start = row[2].toString();
                String end = row[3].toString();
                String label = row[4].toString();
                DocumentInfo di = diMap.get(pubmedId);
                if (di == null) {
                    di = new DocumentInfo(docIdx, pubmedId);
                    fi.appendDocument(di);
                    docIdx++;
                    diMap.put(pubmedId, di);
                }
                String sentenceWS = DocPreprocessor.toWSDelimTokenSentence(sentence);
                String[] toks = sentenceWS.split("\\s+");
                String[] tags = tagger.tag(toks);
                String ptStr = DocPreprocessor.toLispNotation(toks, tags);
                TextInfo sti = new TextInfo(sentence, sentIdx);
                TextInfo pt = new TextInfo(ptStr);
                SentenceInfo si = new SentenceInfo(di.getDocIdx(), sentIdx, sti, pt);
                di.addSentenceInfo(si, di.getDocIdx());
                if (label.equals("good")) {
                    NEInfo nei = new NEInfo("resource", start, end, "human");
                    si.addNEInfo(nei);
                }
                sentIdx++;
                for (Object col : row) {
                    System.out.print(col + " ");
                }
                System.out.println();
                if (diMap.size() >= 20) {
                   // break;
                }
            }

            Comment comment = new Comment(GenUtils.prepCreatorComment(NERTrainingDataPreparer.class.getName()));
            fi.saveAsXML(outIdxXmlFile, comment, CharSetEncoding.UTF8);

        } finally {
            Utils.closeEntityManager(em);
        }
    }

    public static void main(String[] args) throws Exception {
        Injector injector;
        try {
            injector = Guice.createInjector(new RDPersistModule());
            NERTrainingDataPreparer dp = new NERTrainingDataPreparer();
            injector.injectMembers(dp);
            dp.handle("/tmp/rdw_resource_ner_idx.xml");

        } finally {
            JPAInitializer.stopService();
        }
    }
}
