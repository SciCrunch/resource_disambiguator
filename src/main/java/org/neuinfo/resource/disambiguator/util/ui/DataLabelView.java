package org.neuinfo.resource.disambiguator.util.ui;

import org.neuinfo.resource.disambiguator.classification.DataRec;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by bozyurt on 3/26/14.
 */
public class DataLabelView extends JPanel implements ListSelectionListener, ActionListener {
    SearchableTextPane contentPane;
    JComboBox labelCB;
    DefaultComboBoxModel labelCBModel;
    DataRec curDataRec;


    public DataLabelView() {
        setLayout(new BorderLayout(5, 5));
        contentPane = new SearchableTextPane();


        labelCBModel = new DefaultComboBoxModel();
        labelCBModel.addElement("<no-label>");
        labelCBModel.addElement("good");
        labelCBModel.addElement("bad");
        labelCB = new JComboBox(labelCBModel);
        labelCB.addActionListener(this);
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        controlPanel.add(labelCB);
        controlPanel.setPreferredSize(new Dimension(0,30));
        controlPanel.setMaximumSize(new Dimension(0,30));



        contentPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Data"),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        add(contentPane, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.NORTH);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == labelCB) {
            int selIdx = labelCB.getSelectedIndex();
            if (selIdx != -1 && curDataRec != null) {
                if (selIdx == 0) {
                    curDataRec.setLabel("");
                } else {
                    curDataRec.setLabel((String) labelCB.getSelectedItem());
                }
            }
        }

    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting() == true) {
            return;
        }
        JList list = (JList) e.getSource();
        if (list.getSelectedIndex() == -1) {
            return;
        }
        this.curDataRec = (DataRec) list.getSelectedValue();
        contentPane.setText(curDataRec.getContent());
        if (curDataRec.getLabel().length() == 0) {
            this.labelCBModel.setSelectedItem("<no-label>");
        } else {
            this.labelCBModel.setSelectedItem(curDataRec.getLabel());
        }

    }
}
