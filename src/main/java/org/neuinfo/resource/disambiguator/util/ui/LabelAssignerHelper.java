package org.neuinfo.resource.disambiguator.util.ui;


import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;
import org.neuinfo.resource.disambiguator.classification.DataRec;
import org.neuinfo.resource.disambiguator.util.Utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bozyurt on 3/26/14.
 */
public class LabelAssignerHelper {
    List<DataRec> drList;

    public List<DataRec> loadXml(File datasetXmlFile) throws Exception {

        SAXBuilder saxBuilder = new SAXBuilder();
        Document doc = saxBuilder.build(datasetXmlFile);
        Element rootNode = doc.getRootElement();
        List<Element> children = rootNode.getChildren("data");
        drList = new ArrayList<DataRec>(children.size());
        int locIdx = 0;
        for (Element el : children) {
            String nifId = el.getAttributeValue("registry_nif_id");
            String type = el.getAttributeValue("type");
            String label = el.getAttributeValue("label");
            String url = el.getAttributeValue("url");
            String content = el.getText();
            DataRec dr = new DataRec(nifId, content, url, label, type);
            dr.setLocIdx(locIdx);
            drList.add(dr);
            locIdx++;
        }
        return drList;
    }

    public void saveXml(File datasetXmlFile) throws Exception {
        Element root = new Element("data-set");
        Document doc = new Document(root);
        for (DataRec dr : drList) {
            root.addContent(dr.toXml());
        }
        BufferedOutputStream bout = null;
        try {
            bout = new BufferedOutputStream(new FileOutputStream(datasetXmlFile));
            XMLOutputter serializer = new XMLOutputter(Format.getPrettyFormat());
            serializer.output(doc, bout);
            bout.flush();
        } finally {
            Utils.close(bout);
        }
    }

}
