package org.neuinfo.resource.disambiguator.util.ui;

import org.neuinfo.resource.disambiguator.classification.DataRec;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;

/**
 * Created by bozyurt on 3/26/14.
 */
public class DataSetListView extends JPanel {
    protected JList dataSetList;

    public DataSetListView() {
        setLayout(new BorderLayout(5, 5));
        dataSetList = new JList(new DefaultListModel());
        dataSetList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(dataSetList);
        sp.setPreferredSize(new Dimension(500, 200));
        sp.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Data Sets"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        add(sp, BorderLayout.CENTER);
    }

    public void updateModel(java.util.List<DataRec> list) {
        DefaultListModel lm = (DefaultListModel) dataSetList.getModel();
        lm.clear();
        for(DataRec dr : list) {
            lm.addElement(dr);
        }
    }

    public void addListSelectionListener(ListSelectionListener lsl) {
        dataSetList.addListSelectionListener(lsl);
    }
}
