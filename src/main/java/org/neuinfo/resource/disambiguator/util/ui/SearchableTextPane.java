package org.neuinfo.resource.disambiguator.util.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by bozyurt on 3/28/14.
 */
public class SearchableTextPane extends JPanel implements ActionListener {
    protected JTextPane textPane;
    protected JTextField searchField;
    protected JButton searchButton;
    protected JButton nextButton;
    protected JButton prevButton;
    protected JCheckBox caseCB;
    /**
     * last found position
     */
    protected int foundPos = -1;
    /**
     * the last text that is search for
     */
    protected String foundText;
    protected boolean caseSensitive = true;
    protected String fullText;

    public SearchableTextPane() {
        setLayout(new BorderLayout(5, 5));

        textPane = new JTextPane();
        textPane.setEditable(false);
        JScrollPane sp = new JScrollPane(textPane);
        JPanel searchPanel = new JPanel(null);
        BoxLayout bl = new BoxLayout(searchPanel, BoxLayout.X_AXIS);
        searchPanel.setLayout(bl);
        searchField = new JTextField(10);
        searchButton = new JButton("Search");
        nextButton = new JButton("Next");
        prevButton = new JButton("Previous");
        searchButton.addActionListener(this);
        nextButton.addActionListener(this);
        prevButton.addActionListener(this);
        prevButton.setEnabled(false);
        nextButton.setEnabled(false);
        caseCB = new JCheckBox("Case sensitive");
        caseCB.setSelected(true);
        caseCB.addActionListener(this);

        searchPanel.add(Box.createHorizontalStrut(5));
        searchPanel.add(searchField);
        searchPanel.add(Box.createHorizontalStrut(5));
        searchPanel.add(searchButton);
        searchPanel.add(Box.createHorizontalStrut(5));
        searchPanel.add(nextButton);
        searchPanel.add(Box.createHorizontalStrut(5));
        searchPanel.add(caseCB);
        searchPanel.add(Box.createHorizontalGlue());
        textPane.setSelectionColor(Color.red);

        add(sp, BorderLayout.CENTER);
        add(searchPanel, BorderLayout.NORTH);
    }

    public void setText(String text) {
        textPane.setText(text);
        textPane.setCaretPosition(0);
        this.foundPos = -1;
        this.foundText = null;

    }

    protected void findText(String text2Find) {
        int nextPos;
        if (!text2Find.equals(foundText)) {
            this.foundPos = 0;
            if (caseSensitive) {
                fullText = textPane.getText();
            } else {
                fullText = textPane.getText().toLowerCase();
            }
        }
        nextPos = nextFindIndex(fullText, text2Find, this.foundPos);
        if (nextPos >= 0) {
            // make sure the textPane has input focus
            textPane.requestFocusInWindow();
            textPane.select(nextPos, nextPos + text2Find.length());
            this.foundPos = nextPos + text2Find.length() + 1;
            this.foundText = text2Find;
        } else {
            foundPos = nextPos;
            JOptionPane.showMessageDialog(this, text2Find + " not found!");
        }
    }

    protected int nextFindIndex(String fullText, String text2Find,
                                int startOffset) {
        int pos = -1;
        if (fullText != null && text2Find != null
                && startOffset < fullText.length()) {
            if (this.caseSensitive) {
                pos = fullText.indexOf(text2Find, startOffset);
            } else {
                pos = fullText.indexOf(text2Find.toLowerCase(), startOffset);
            }
        }
        return pos;
    }


    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == searchButton) {
            String text2Search = searchField.getText().trim();
            if (text2Search.length() > 0) {
                findText(text2Search);
                if (foundPos >= 0) {
                    nextButton.setEnabled(true);
                } else {
                    nextButton.setEnabled(false);
                }
            }
        } else if (e.getSource() == nextButton) {
            String text2Search = searchField.getText().trim();
            if (text2Search.length() > 0) {
                findText(text2Search);
                if (foundPos >= 0) {
                    nextButton.setEnabled(true);
                } else {
                    nextButton.setEnabled(false);
                }
            }
        } else if (e.getSource() == caseCB) {
            this.caseSensitive = caseCB.isSelected();
        }
    }
}
