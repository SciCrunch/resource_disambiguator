package org.neuinfo.resource.disambiguator.classification;

import org.jdom2.Element;

/**
 * Created by bozyurt on 3/29/14.
 */
public class DataRec {
    int locIdx;
    String registryNifID;
    String content;
    String url;
    String type;
    String label;

    public DataRec(String registryNifID, String content, String url, String label, String type) {
        this.registryNifID = registryNifID;
        this.content = content;
        this.url = url;
        this.label = label;
        this.type = type;
    }

    public DataRec(DataRec other) {
        this.registryNifID = other.registryNifID;
        this.content = other.content;
        this.url = other.url;
        this.label = other.label;
        this.type = other.type;
    }

    public String getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getLocIdx() {
        return locIdx;
    }

    public String getRegistryNifID() {
        return registryNifID;
    }

    public String getContent() {
        return content;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(registryNifID).append(" - ");
        if (url != null) {
            sb.append(url).append(" - ");
        }
        sb.append(content.substring(0, Math.min(50, content.length())));
        return sb.toString();
    }

    public void setLocIdx(int locIdx) {
        this.locIdx = locIdx;
    }

    public Element toXml() {
        Element el = new Element("data");
        el.setAttribute("type", type);
        el.setAttribute("label", label);
        el.setAttribute("registry_nif_id", registryNifID);
        el.setAttribute("url", url != null ? url : "");
        el.setText(content);
        return el;
    }

    public static DataRec fromXml(Element el) {
        String type = el.getAttributeValue("type");
        String label = el.getAttributeValue("label");
        String registryNifID = el.getAttributeValue("registry_nif_id");
        String url = el.getAttributeValue("url");
        String content = el.getTextTrim();

        DataRec dr = new DataRec(registryNifID, content, url, label, type);
        return dr;
    }

}
