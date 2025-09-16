import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Einstiegspunkt & Hauptfenster des Gefahren-Readers.
 * Liest index.json + Bereichsdateien + gefahrenzahl.json und bietet
 * Navigation nach Klasse und UN-Bereichen, Suche, Detail-Popups.
 */
public class GefahrenReaderApp extends JFrame {

    private final DataRepository repo;
    private final JTree navTree;
    private final JTable table;
    private final SubstanceTableModel tableModel;
    private final JTextField searchField;
    private final JButton searchBtn;
    private final JButton resetBtn;
    private final JButton detailsBtn;
    private final JLabel statusLabel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            applyDarkNimbus("#00D2FF");
            File dataRoot = DataRepository.findDefaultDataRoot();
            DataRepository repo;
            try {
                repo = new DataRepository(dataRoot);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null,
                        "Daten konnten nicht geladen werden:\n" + e.getMessage(),
                        "Ladefehler", JOptionPane.ERROR_MESSAGE);
                return;
            }
            new GefahrenReaderApp(repo).setVisible(true);
        });
    }

    public GefahrenReaderApp(DataRepository repo) {
        super("GefahrenReader – UN-Nummern & Gefahrenzahlen");
        this.repo = repo;

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setIconImage(new BufferedImageIcon16().get());

        // Left: Navigation JTree
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Navigation");
        DefaultMutableTreeNode byClass = new DefaultMutableTreeNode("Nach Klasse");
        DefaultMutableTreeNode byRange = new DefaultMutableTreeNode("Nach UN-Bereich");
        root.add(byClass);
        root.add(byRange);

        // Build class nodes
        for (String cls : repo.getAllClassesSorted()) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new NodePayload(NodeType.CLASS, cls));
            byClass.add(node);
        }
        // Build range nodes (use label from index.json)
        for (IndexRange r : repo.getRanges()) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(new NodePayload(NodeType.RANGE, r.rangeLabel));
            byRange.add(node);
        }

        navTree = new JTree(new DefaultTreeModel(root));
        navTree.setRootVisible(true);
        navTree.setShowsRootHandles(true);
        navTree.setRowHeight(22);
        navTree.setBorder(new EmptyBorder(6,6,6,6));
        navTree.addTreeSelectionListener(new NavListener());

        JScrollPane navScroll = new JScrollPane(navTree);
        navScroll.setPreferredSize(new Dimension(280, 400));

        // Right: Table + search panel + toolbar
        tableModel = new SubstanceTableModel();
        table = new JTable(tableModel);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new ZebraCellRenderer());

        // Double-click -> details
        table.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    showDetailsForSelected();
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(table);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JLabel searchLbl = new JLabel("UN-Nummer:");
        searchField = new JTextField(10);
        searchBtn = new JButton("Suchen");
        resetBtn = new JButton("Reset");
        detailsBtn = new JButton("Details…");
        detailsBtn.setEnabled(false);

        table.getSelectionModel().addListSelectionListener(e -> {
            detailsBtn.setEnabled(table.getSelectedRow() >= 0);
        });

        Action searchAction = new AbstractAction("Suchen") {
            @Override public void actionPerformed(ActionEvent e) {
                String q = searchField.getText().trim();
                filterByUn(q);
            }
        };
        searchBtn.addActionListener(searchAction);
        searchField.addActionListener(searchAction);

        resetBtn.addActionListener(e -> {
            searchField.setText("");
            tableModel.setRows(currentContextList);
            updateStatus();
        });

        detailsBtn.addActionListener(e -> showDetailsForSelected());

        searchPanel.add(searchLbl);
        searchPanel.add(searchField);
        searchPanel.add(searchBtn);
        searchPanel.add(resetBtn);
        searchPanel.add(new JLabel("   "));
        searchPanel.add(detailsBtn);

        statusLabel = new JLabel("Bereit.");
        statusLabel.setBorder(new EmptyBorder(4, 8, 4, 8));

        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.add(searchPanel, BorderLayout.NORTH);
        right.add(tableScroll, BorderLayout.CENTER);
        right.add(statusLabel, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, navScroll, right);
        split.setDividerLocation(300);
        split.setResizeWeight(0.0);

        setLayout(new BorderLayout());
        add(buildToolbar(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        // Preselect first category
        navTree.expandRow(0);
        navTree.expandRow(1);
        // If classes exist, select first class
        if (!repo.getAllClassesSorted().isEmpty()) {
            TreePath path = new TreePath(((DefaultTreeModel) navTree.getModel())
                    .getPathToRoot(byClass.getFirstChild()));
            navTree.setSelectionPath(path);
        }
    }

    private JToolBar buildToolbar() {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        JButton openBtn = new JButton("Datenordner öffnen…");
        openBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(repo.getDataRoot());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Ordner mit index.json auswählen");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File dir = fc.getSelectedFile();
                try {
                    DataRepository rep2 = new DataRepository(dir);
                    // Replace data + rebuild tree
                    this.replaceRepository(rep2);
                    JOptionPane.showMessageDialog(this, "Daten neu geladen aus:\n" + dir.getAbsolutePath(),
                            "Neu geladen", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Fehler beim Laden:\n" + ex.getMessage(),
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        tb.add(openBtn);
        tb.addSeparator();
        JButton about = new JButton("Info");
        about.addActionListener(e -> {
            JOptionPane.showMessageDialog(this,
                    "GefahrenReader\nUN-Nummern & Gefahrenzahlen\n\n" +
                    "Lädt index.json + Bereichsdateien + gefahrenzahl.json\n" +
                    "Design: Dark-Nimbus, Akzent #00D2FF\n" +
                    "© 2025 https://mdwebdev.de\n Marcus Dziersan\n",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
        });
        tb.add(about);
        return tb;
    }

    // Current context (rows currently shown on the right)
    private List<Substance> currentContextList = new ArrayList<>();

    private void filterByUn(String query) {
        if (query == null || query.isEmpty()) {
            tableModel.setRows(currentContextList);
            updateStatus();
            return;
        }
        String q = query.trim();
        List<Substance> filtered = currentContextList.stream()
                .filter(s -> s.unNumber.startsWith(q))
                .collect(Collectors.toList());
        tableModel.setRows(filtered);
        statusLabel.setText("Gefiltert nach UN " + q + " – " + filtered.size() + " Treffer");
    }

    private class NavListener implements TreeSelectionListener {
        @Override public void valueChanged(TreeSelectionEvent e) {
            Object nodeObj = navTree.getLastSelectedPathComponent();
            if (!(nodeObj instanceof DefaultMutableTreeNode)) return;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodeObj;
            Object user = node.getUserObject();
            if (user instanceof NodePayload) {
                NodePayload np = (NodePayload) user;
                switch (np.type) {
                    case CLASS:
                        List<Substance> byCls = repo.getByClass(np.value);
                        currentContextList = byCls;
                        tableModel.setRows(byCls);
                        statusLabel.setText("Klasse " + np.value + " – " + byCls.size() + " Stoffe");
                        break;
                    case RANGE:
                        List<Substance> byRange = repo.getByRange(np.value);
                        currentContextList = byRange;
                        tableModel.setRows(byRange);
                        statusLabel.setText("Bereich " + np.value + " – " + byRange.size() + " Stoffe");
                        break;
                }
            } else {
                // Click on category headers – do nothing
            }
        }
    }

    private void showDetailsForSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = table.convertRowIndexToModel(viewRow);
        Substance s = tableModel.getRow(modelRow);

        String kemler = (s.hazardNumber == null || s.hazardNumber.isEmpty()) ? "–" : s.hazardNumber;
        String kemlerDesc = repo.getHazardDescription(kemler);
        if (kemlerDesc == null && kemler.startsWith("X")) {
            kemlerDesc = repo.getHazardDescription(kemler.substring(1));
        }

        String hint = s.hint != null ? s.hint : repo.deriveHint(s);

        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='width:420px'>");
        sb.append("<h2>").append(escape(s.name)).append("</h2>");
        sb.append("<b>UN-Nummer:</b> ").append(escape(s.unNumber)).append("<br>");
        sb.append("<b>Gefahrenzahl (Kemler):</b> ").append(escape(kemler));
        if (kemlerDesc != null) sb.append(" – ").append(escape(kemlerDesc));
        sb.append("<br>");
        sb.append("<b>Gefahrgutklasse:</b> ").append(escape(s.klass)).append("<br>");
        if (hint != null) {
            sb.append("<b>Hinweis:</b> ").append(escape(hint)).append("<br>");
        }
        sb.append("<hr>");
        sb.append("<small>Datenquelle: index.json + Bereiche, gefahrenzahl.json</small>");
        sb.append("</body></html>");

        JTextPane pane = new JTextPane();
        pane.setContentType("text/html");
        pane.setText(sb.toString());
        pane.setEditable(false);
        pane.setBorder(new EmptyBorder(10,10,10,10));
        JScrollPane sp = new JScrollPane(pane);
        sp.setPreferredSize(new Dimension(500, 320));

        JOptionPane.showMessageDialog(this, sp, "Details", JOptionPane.PLAIN_MESSAGE);
    }

    /** Aktualisiert die Statusleiste je nach Filter/ Kontext. */
    private void updateStatus() {
        int shown = tableModel.getRowCount();
        int base  = (currentContextList == null) ? shown : currentContextList.size();

        if (currentContextList == null || shown == base) {
            statusLabel.setText(shown + " Stoffe angezeigt");
        } else {
            statusLabel.setText("Gefiltert: " + shown + " / " + base + " Stoffe");
        }
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    private void replaceRepository(DataRepository newRepo) {
        // rebuild tree
        this.getContentPane().removeAll();
        GefahrenReaderApp app = new GefahrenReaderApp(newRepo);
        this.dispose();
        app.setVisible(true);
    }

    /* ---------- UI helpers ---------- */

    private static void applyDarkNimbus(String accentHex) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}
        // Dark palette
        Color bg = new Color(0x1E1F25);
        Color fg = new Color(0xE6E6E6);
        Color panel = new Color(0x23252E);
        Color table = new Color(0x272A34);
        Color sel = hex(accentHex);
        UIManager.put("control", bg);
        UIManager.put("info", panel);
        UIManager.put("nimbusBase", panel.darker());
        UIManager.put("nimbusBlueGrey", panel);
        UIManager.put("nimbusLightBackground", table);
        UIManager.put("text", fg);
        UIManager.put("textForeground", fg);
        UIManager.put("Table.foreground", fg);
        UIManager.put("Table.background", table);
        UIManager.put("Table.alternateRowColor", table.darker());
        UIManager.put("Table.selectionBackground", sel);
        UIManager.put("Table.selectionForeground", Color.BLACK);
        UIManager.put("ScrollBar.thumb", sel);
        UIManager.put("defaultFont", new Font("SansSerif", Font.PLAIN, 13));
        // refresh
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
    }
    private static Color hex(String h) {
        try { return Color.decode(h); } catch (Exception e) { return new Color(0x00D2FF); }
    }
}

/* ======== Support classes for GefahrenReaderApp ======== */

enum NodeType { CLASS, RANGE; }

class NodePayload {
    final NodeType type;
    final String value;
    NodePayload(NodeType t, String v) { this.type = t; this.value = v; }
    public String toString() { return value; }
}

class ZebraCellRenderer extends DefaultTableCellRenderer {
    @Override public Component getTableCellRendererComponent(JTable table, Object value,
                                                            boolean isSelected, boolean hasFocus, int row, int col) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
        if (!isSelected) {
            Color base = UIManager.getColor("Table.background");
            Color alt = UIManager.getColor("Table.alternateRowColor");
            c.setBackground((row % 2 == 0) ? base : alt);
        }
        if (isSelected) {
            c.setFont(c.getFont().deriveFont(Font.BOLD));
        } else {
            c.setFont(c.getFont().deriveFont(Font.PLAIN));
        }
        return c;
    }
}

class BufferedImageIcon16 {
    java.awt.Image get() {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(16,16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x00D2FF));
        g.fillRoundRect(1,1,14,14,4,4);
        g.setColor(new Color(0x0A0B0F));
        g.drawString("UN", 3, 12);
        g.dispose();
        return img;
    }
}
