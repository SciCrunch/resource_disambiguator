package org.neuinfo.resource.disambiguator.util.ui;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Enumeration;

/**
 * Created by bozyurt on 3/26/14.
 */
public class AppFrame extends JFrame {
    protected int width;
    protected int height;
    protected JComponent view;
    protected JLabel statusBar;
    protected JMenuBar menuBar;
    protected JMenu fileMenu, helpMenu;
    protected java.util.List<String> statusLabels = new ArrayList<String>(3);
    protected JPanel statusInfoPanel;

    public AppFrame(int width, int height, FontUIResource fur) {
        this.width = width;
        this.height = height;

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onClose();
                System.exit(0);
            }
        });
        if (fur != null) {
            setUIFont(fur);
        }
        initFrame();
    }

    public void startup() {
        menuBar.add(Box.createHorizontalGlue());
        menuBar.add(createHelpMenu());

        this.pack();
        this.setSize(width, height);
        this.setVisible(true);
    }

    protected void initFrame() {
        JPanel statusPanel = new JPanel(null);
        BoxLayout bl = new BoxLayout(statusPanel, BoxLayout.X_AXIS);
        statusPanel.setLayout(bl);
        statusPanel.setPreferredSize(new Dimension(0, 18));
        JPanel leftPanel = new JPanel(new BorderLayout());
        JPanel rightPanel = new JPanel(new BorderLayout());

        statusInfoPanel = new JPanel(null);
        BoxLayout rbl = new BoxLayout(statusInfoPanel, BoxLayout.X_AXIS);
        statusInfoPanel.setLayout(rbl);
        Dimension dim = new Dimension(60, 18);
        rightPanel.setPreferredSize(dim);
        rightPanel.setMinimumSize(dim);
        rightPanel.setMaximumSize(dim);

        leftPanel.setBorder(BorderFactory.createLoweredBevelBorder());
        rightPanel.setBorder(BorderFactory.createLoweredBevelBorder());
        rightPanel.add(statusInfoPanel);

        statusBar = new JLabel("");
        statusBar.setMinimumSize(new Dimension(0, 15));
        statusBar.setMaximumSize(new Dimension(0, 15));
        statusBar.setPreferredSize(new Dimension(0, 15));
        leftPanel.add(statusBar);
        statusPanel.add(Box.createHorizontalGlue());
        statusPanel.add(leftPanel);
        statusPanel.add(Box.createHorizontalStrut(3));
        statusPanel.add(rightPanel);

        this.getContentPane().add(statusPanel, BorderLayout.SOUTH);
        menuBar = new JMenuBar();

        setJMenuBar(menuBar);
        menuBar.add(createFileMenu());
    }
    public void addStatusLabel(int idx, JLabel label) {
        statusInfoPanel.add(label, idx);
        statusInfoPanel.validate();
        statusInfoPanel.getParent().repaint();
    }

    public void removeStatusLabel(int idx) {
        if (statusInfoPanel.getComponentCount() > idx) {
            statusInfoPanel.remove(idx);
            statusInfoPanel.validate();
            statusInfoPanel.getParent().repaint();
        }
    }

    protected JMenu createFileMenu() {
        fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem item = new JMenuItem("Load", KeyEvent.VK_L);
        fileMenu.add(item);
        item.addActionListener(new OpenAdapter());

        item = new JMenuItem("Save", KeyEvent.VK_S);
        fileMenu.add(item);
        item.addActionListener(new SaveAdapter());

        item = new JMenuItem("Save As", KeyEvent.VK_A);
        fileMenu.add(item);
        item.addActionListener(new SaveAsAdapter());

        fileMenu.addSeparator();
        item = new JMenuItem("Quit", KeyEvent.VK_Q);
        fileMenu.add(item);
        item.addActionListener(new CloseAdapter());

        return fileMenu;
    }

    protected JMenu createHelpMenu() {
        helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);

        JMenuItem item = new JMenuItem("About", KeyEvent.VK_A);
        helpMenu.add(item);
        item.addActionListener(new AboutAdapter());

        return helpMenu;
    }

    public void addView(JComponent view) {
        this.view = view;
        this.getContentPane().add(this.view);
    }

    public void onClose() {
        dispose();
        System.exit(0);
    }

    /**
     * hook for File->Open menu
     */
    public void onOpen() {}


    /**
     * hook for File->Save menu
     */
    public void onSave() {}

    /**
     * hook for File->Saves menu
     */
    public void onSaveAs() {}

    /**
     * hook for Help->About menu
     */
    public void onAbout() {
        String message = getTitle() != null ? getTitle()
                : "Your application name here";
        JOptionPane.showMessageDialog(this, message, "About",
                JOptionPane.PLAIN_MESSAGE);
    }

    class OpenAdapter implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            AppFrame.this.onOpen();
        }
    }

    class CloseAdapter implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            AppFrame.this.onClose();
        }
    }
    class SaveAdapter implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            AppFrame.this.onSave();
        }
    }

    class SaveAsAdapter implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            AppFrame.this.onSaveAs();
        }
    }

    class AboutAdapter implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            AppFrame.this.onAbout();
        }
    }

    public static void setUIFont(FontUIResource fur) {
        Enumeration<?> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                UIManager.put(key, fur);
            }
        }
    }

}
