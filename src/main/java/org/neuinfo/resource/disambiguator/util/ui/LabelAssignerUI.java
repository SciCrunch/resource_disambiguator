package org.neuinfo.resource.disambiguator.util.ui;

import bnlpkit.util.SuffixFileFilter;
import org.neuinfo.resource.disambiguator.classification.DataRec;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Created by bozyurt on 3/26/14.
 */
public class LabelAssignerUI extends AppFrame implements ListSelectionListener {
    protected String xmlFile;
    protected DataSetListView dslView;
    protected DataLabelView labelView;
    protected JFileChooser chooser = new JFileChooser();
    protected LabelAssignerHelper helper;

    public LabelAssignerUI(int width, int height) throws Exception {
        super(width, height, new FontUIResource("Dialog", Font.PLAIN, 12));
        setTitle("Label Assigner UI");
        init();
    }

    protected void init() throws Exception {
        helper = new LabelAssignerHelper();
        chooser.setCurrentDirectory(new File("/tmp"));
        chooser.setFileFilter(new SuffixFileFilter("XML Files (*.xml)", ".xml"));

        dslView = new DataSetListView();
        labelView = new DataLabelView();
        dslView.addListSelectionListener(labelView);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, dslView,
                labelView);
        splitPane.setDividerLocation(300);
        splitPane.setOneTouchExpandable(true);
        getContentPane().add(splitPane);
    }

    @Override
    public void onOpen() {
        int rv = chooser.showOpenDialog(this);
        if (rv == JFileChooser.APPROVE_OPTION) {
            this.xmlFile = chooser.getSelectedFile().getAbsolutePath();
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    try {
                        List<DataRec> dataRecs = helper.loadXml(new File(xmlFile));
                        dslView.updateModel(dataRecs);
                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                }
            });
        }
    }

    @Override
    public void onSave() {
        if (this.xmlFile == null) {
            return;
        }
        File backupFile = new File(this.xmlFile + ".bak");
        new File(this.xmlFile).renameTo(backupFile);
        try {
            helper.saveXml(new File(this.xmlFile));
        } catch (Exception x) {
            x.printStackTrace();
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        JList list = (JList) e.getSource();
        DataRec dr = (DataRec) list.getSelectedValue();
        if (dr != null) {

        }
    }

    public static void main(String[] args) throws Exception {
        LabelAssignerUI ui = new LabelAssignerUI(800, 600);

        ui.startup();
    }
}
