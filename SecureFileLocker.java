
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.dnd.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.*;
import java.util.Arrays;

public class SecureFileLocker {

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ignored) {
      }
      JFrame.setDefaultLookAndFeelDecorated(true);
      new PolishedMainFrame().setVisible(true);
    });
  }

  // ---------------- UI: polished frame ----------------
  static class PolishedMainFrame extends JFrame {
    PolishedMainFrame() {
      setTitle("Secure File Locker");
      setDefaultCloseOperation(EXIT_ON_CLOSE);
      setSize(820, 420);
      setLocationRelativeTo(null);
      setLayout(new BorderLayout());

      HeaderPanel header = new HeaderPanel();
      StatusBar status = new StatusBar();
      MainContent content = new MainContent(status); // pass shared status bar

      add(header, BorderLayout.NORTH);
      add(content, BorderLayout.CENTER);
      add(status, BorderLayout.SOUTH);
    }
  }

  static class HeaderPanel extends JPanel {
    HeaderPanel() {
      setLayout(new BorderLayout());
      setBackground(new Color(33, 150, 243));
      setBorder(new EmptyBorder(12, 16, 12, 16));
      JLabel title = new JLabel("Secure File Locker");
      title.setForeground(Color.WHITE);
      title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
      add(title, BorderLayout.WEST);

      JLabel subtitle = new JLabel("Encrypt and decrypt files securely (AES-GCM + PBKDF2)");
      subtitle.setForeground(new Color(230, 245, 255));
      subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
      add(subtitle, BorderLayout.SOUTH);

    }
  }

  static class StatusBar extends JPanel {
    private final JLabel statusLabel = new JLabel("Ready");

    StatusBar() {
      setLayout(new BorderLayout());
      setBorder(new EmptyBorder(6, 10, 6, 10));
      add(statusLabel, BorderLayout.WEST);
    }

    void setStatus(String s) {
      statusLabel.setText(s);
    }
  }

  // ---------------- Main content with tabs ----------------
  static class MainContent extends JPanel {
    final StatusBar statusBar;

    MainContent(StatusBar statusBar) {
      this.statusBar = statusBar;
      setLayout(new BorderLayout());
      JTabbedPane tabs = new JTabbedPane();
      EncryptPanel enc = new EncryptPanel(statusBar);
      DecryptPanel dec = new DecryptPanel(statusBar);
      tabs.addTab("Encrypt", enc);
      tabs.addTab("Decrypt", dec);
      enc.setForeground(Color.DARK_GRAY);
      add(tabs, BorderLayout.CENTER);
      setBorder(new EmptyBorder(12, 12, 12, 12));
    }
  }

  // ---------------- Base panel helpers ----------------
  static abstract class PanelBase extends JPanel {
    final JTextField fileField = new JTextField();
    final JPasswordField passField = new JPasswordField();
    final JProgressBar progressBar = new JProgressBar(0, 100);
    final JLabel infoLabel = new JLabel("No file selected");
    final StatusBar statusBar;

    PanelBase(StatusBar statusBar) {
      this.statusBar = statusBar;
      setLayout(new BorderLayout(10, 10));
      setBorder(new EmptyBorder(10, 10, 10, 10));
      progressBar.setStringPainted(true);
    }

    JButton flatButton(String text) {
      JButton b = new JButton(text);
      b.setFocusPainted(false);
      b.setBackground(new Color(60, 150, 255));
      b.setForeground(Color.WHITE);
      b.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
      return b;
    }

    void setStatus(String s) {
      if (statusBar != null)
        statusBar.setStatus(s);
    }

    void enableComponents(boolean v) {
      fileField.setEnabled(v);
      passField.setEnabled(v);
    }

    void enableAll(boolean v, Component... comps) {
      for (Component c : comps)
        if (c != null)
          c.setEnabled(v);
    }

    /**
     * Install a DropTarget with hover highlight on the given JComponent.
     * Saves the original border/background/opaque state and restores them on
     * exit/drop.
     * On drop it sets fileField text (so DocumentListener updates UI).
     */
    void installDragDrop(JComponent comp) {
      final Border origBorder = comp.getBorder();
      final Color origBg = comp.getBackground();
      final boolean origOpaque = comp.isOpaque();

      DropTargetListener listener = new DropTargetListener() {
        @Override
        public void dragEnter(DropTargetDragEvent dtde) {
          try {
            comp.setBorder(BorderFactory.createLineBorder(new Color(30, 144, 255), 2));
            comp.setBackground(new Color(230, 245, 255));
            comp.setOpaque(true);
            comp.repaint();
          } catch (Exception ignored) {
          }
        }

        @Override
        public void dragOver(DropTargetDragEvent dtde) {
          /* no-op */ }

        @Override
        public void dropActionChanged(DropTargetDragEvent dtde) {
          /* no-op */ }

        @Override
        public void dragExit(DropTargetEvent dte) {
          try {
            comp.setBorder(origBorder);
            comp.setBackground(origBg);
            comp.setOpaque(origOpaque);
            comp.repaint();
          } catch (Exception ignored) {
          }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void drop(DropTargetDropEvent dtde) {
          try {
            dtde.acceptDrop(DnDConstants.ACTION_COPY);
            java.util.List<File> dropped = (java.util.List<File>) dtde.getTransferable()
                .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
            if (dropped != null && !dropped.isEmpty()) {
              fileField.setText(dropped.get(0).getAbsolutePath());
            }
            dtde.dropComplete(true);
          } catch (Exception ex) {
            dtde.dropComplete(false);
          } finally {
            // restore visuals
            try {
              comp.setBorder(origBorder);
              comp.setBackground(origBg);
              comp.setOpaque(origOpaque);
              comp.repaint();
            } catch (Exception ignored) {
            }
          }
        }
      };

      // attach the drop target listener
      new DropTarget(comp, DnDConstants.ACTION_COPY, listener, true, null);
    }

    String human(long bytes) {
      if (bytes < 1024)
        return bytes + " B";
      int exp = (int) (Math.log(bytes) / Math.log(1024));
      String pre = "KMGTPE".charAt(exp - 1) + "";
      return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
  }

  // ---------------- Encrypt panel ----------------
  static class EncryptPanel extends PanelBase {
    private final JPasswordField confirm = new JPasswordField();

    EncryptPanel(StatusBar statusBar) {
      super(statusBar);

      // Top area: a visible drag-drop zone + file chooser row
      JPanel top = new JPanel(new BorderLayout(8, 8));
      top.setBorder(BorderFactory.createTitledBorder("File to encrypt"));

      // Visual drop area
      JLabel dropLabel = new JLabel("<html><center>Drag &amp; drop file here<br/>or click Browse</center></html>",
          SwingConstants.CENTER);
      dropLabel.setPreferredSize(new Dimension(260, 80));
      dropLabel.setHorizontalTextPosition(SwingConstants.CENTER);
      dropLabel.setVerticalTextPosition(SwingConstants.CENTER);
      dropLabel.setBorder(BorderFactory.createDashedBorder(Color.GRAY));
      dropLabel.setOpaque(true);
      dropLabel.setBackground(new Color(250, 250, 250));
      installDragDrop(dropLabel);

      // file selection row (fileField + Browse)
      JPanel fileRow = new JPanel(new BorderLayout(8, 8));
      fileField.setEditable(false);
      JButton browse = new JButton("Browse");
      browse.addActionListener(e -> chooseFile());
      fileRow.add(fileField, BorderLayout.CENTER);
      fileRow.add(browse, BorderLayout.EAST);

      // assemble top: left drop area, right fileRow/info
      JPanel rightTop = new JPanel(new BorderLayout(8, 8));
      rightTop.add(fileRow, BorderLayout.NORTH);

      JPanel info = new JPanel(new GridLayout(2, 2, 6, 6));
      info.setBorder(new EmptyBorder(8, 0, 0, 0));
      info.add(new JLabel("File:"));
      info.add(infoLabel);
      info.add(new JLabel("Size:"));
      info.add(new JLabel("—"));
      rightTop.add(info, BorderLayout.SOUTH);

      top.add(dropLabel, BorderLayout.WEST);
      top.add(rightTop, BorderLayout.CENTER);

      // ensure fileField also supports dropping (redundant but handy)
      installDragDrop(fileField);

      JPanel mid = new JPanel(new GridBagLayout());
      mid.setBorder(BorderFactory.createTitledBorder("Encryption"));
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.insets = new Insets(6, 6, 6, 6);
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.anchor = GridBagConstraints.EAST;
      mid.add(new JLabel("Password:"), gbc);
      gbc.gridx = 1;
      gbc.gridy = 0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.weightx = 1.0;
      mid.add(passField, gbc);
      gbc.gridx = 0;
      gbc.gridy = 1;
      gbc.fill = GridBagConstraints.NONE;
      gbc.weightx = 0;
      gbc.anchor = GridBagConstraints.EAST;
      mid.add(new JLabel("Confirm:"), gbc);
      gbc.gridx = 1;
      gbc.gridy = 1;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      mid.add(confirm, gbc);

      JPanel bottom = new JPanel(new BorderLayout(8, 8));
      JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
      JButton enc = flatButton("Encrypt");
      enc.setForeground(Color.black);
      JButton clear = new JButton("Clear");
      clear.addActionListener(e -> clearAll());
      enc.addActionListener(e -> startEncrypt());
      actions.add(clear);
      actions.add(enc);
      bottom.add(actions, BorderLayout.EAST);

      bottom.add(progressBar, BorderLayout.CENTER);

      add(top, BorderLayout.NORTH);
      add(mid, BorderLayout.CENTER);
      add(bottom, BorderLayout.SOUTH);

      fileField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
        private void changed() {
          updateFileInfo();
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
          changed();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
          changed();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
          changed();
        }
      });
    }

    private void chooseFile() {
      JFileChooser fc = new JFileChooser();
      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      int r = fc.showOpenDialog(this);
      if (r == JFileChooser.APPROVE_OPTION) {
        fileField.setText(fc.getSelectedFile().getAbsolutePath());
      }
    }

    // updateFileInfo reads fileField and updates UI labels
    private void updateFileInfo() {
      String p = fileField.getText().trim();
      if (p.isEmpty()) {
        infoLabel.setText("No file selected");
        return;
      }
      File f = new File(p);
      if (!f.exists()) {
        infoLabel.setText("Not found");
        return;
      }
      infoLabel.setText(f.getName());
      try {
        // locate the size label and set it
        JPanel top = (JPanel) getComponent(0);
        Component center = ((BorderLayout) top.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        if (center instanceof JPanel) {
          JPanel rightTop = (JPanel) center;
          Component c = rightTop.getComponent(1);
          if (c instanceof JPanel) {
            JPanel infoPanel = (JPanel) c;
            Component sizeComp = infoPanel.getComponent(3);
            if (sizeComp instanceof JLabel) {
              ((JLabel) sizeComp).setText(human(f.length()));
            }
          }
        }
      } catch (Exception ignored) {
      }
    }

    private void clearAll() {
      fileField.setText("");
      passField.setText("");
      confirm.setText("");
      progressBar.setValue(0);
      infoLabel.setText("No file selected");
      setStatus("Ready");
    }

    private void startEncrypt() {
      String path = fileField.getText().trim();
      if (path.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Select a file to encrypt.", "Info", JOptionPane.INFORMATION_MESSAGE);
        return;
      }
      File in = new File(path);
      if (!in.exists()) {
        JOptionPane.showMessageDialog(this, "File not found.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }
      char[] p1 = passField.getPassword();
      char[] p2 = confirm.getPassword();
      if (p1.length == 0) {
        JOptionPane.showMessageDialog(this, "Password cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }
      if (!Arrays.equals(p1, p2)) {
        JOptionPane.showMessageDialog(this, "Passwords do not match.", "Error", JOptionPane.ERROR_MESSAGE);
        wipe(p1);
        wipe(p2);
        return;
      }
      File out = new File(in.getParentFile(), in.getName() + ".enc");
      if (out.exists()) {
        int c = JOptionPane.showConfirmDialog(this, "Output exists. Overwrite?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (c != JOptionPane.YES_OPTION) {
          wipe(p1);
          wipe(p2);
          return;
        }
      }
      enableComponents(false);
      setStatus("Encrypting...");
      progressBar.setValue(0);
      SwingWorker<Void, Void> wk = new SwingWorker<>() {
        @Override
        protected Void doInBackground() throws Exception {
          CryptoService.encryptToFile(in, out, p1, (processed, total) -> {
            int perc = total > 0 ? (int) ((processed * 100) / total) : 0;
            setProgress(Math.min(100, perc));
          });
          return null;
        }

        @Override
        protected void done() {
          try {
            get();
            progressBar.setValue(100);
            setStatus("Encrypted: " + out.getName() + " (" + human(out.length()) + ")");
            JOptionPane.showMessageDialog(EncryptPanel.this, "Encryption successful:\n" + out.getAbsolutePath(), "Done",
                JOptionPane.INFORMATION_MESSAGE);
          } catch (Exception ex) {
            setStatus("Error");
            JOptionPane.showMessageDialog(EncryptPanel.this, "Encryption failed: " + ex.getMessage(), "Error",
                JOptionPane.ERROR_MESSAGE);
          } finally {
            // always wipe and clear fields, re-enable UI
            wipe(p1);
            wipe(p2);
            passField.setText("");
            confirm.setText("");
            enableComponents(true);
          }
        }
      };
      wk.addPropertyChangeListener(evt -> {
        if ("progress".equals(evt.getPropertyName()))
          progressBar.setValue((Integer) evt.getNewValue());
      });
      wk.execute();
    }
  }

  // ---------------- Decrypt panel ----------------
  static class DecryptPanel extends PanelBase {
    DecryptPanel(StatusBar statusBar) {
      super(statusBar);

      JPanel top = new JPanel(new BorderLayout(8, 8));
      top.setBorder(BorderFactory.createTitledBorder("Select encrypted file (.enc)"));

      // Visual drop area
      JLabel dropLabel = new JLabel("<html><center>Drag &amp; drop .enc file here<br/>or click Browse</center></html>",
          SwingConstants.CENTER);
      dropLabel.setPreferredSize(new Dimension(260, 80));
      dropLabel.setHorizontalTextPosition(SwingConstants.CENTER);
      dropLabel.setVerticalTextPosition(SwingConstants.CENTER);
      dropLabel.setBorder(BorderFactory.createDashedBorder(Color.GRAY));
      dropLabel.setOpaque(true);
      dropLabel.setBackground(new Color(250, 250, 250));
      installDragDrop(dropLabel);

      fileField.setEditable(false);
      JButton browse = new JButton("Browse");
      browse.addActionListener(e -> chooseFile());

      JPanel fileRow = new JPanel(new BorderLayout(8, 8));
      fileRow.add(fileField, BorderLayout.CENTER);
      fileRow.add(browse, BorderLayout.EAST);

      JPanel rightTop = new JPanel(new BorderLayout(8, 8));
      rightTop.add(fileRow, BorderLayout.NORTH);

      JPanel info = new JPanel(new GridLayout(2, 2, 6, 6));
      info.setBorder(new EmptyBorder(8, 0, 0, 0));
      info.add(new JLabel("File:"));
      info.add(infoLabel);
      info.add(new JLabel("Size:"));
      info.add(new JLabel("—"));
      rightTop.add(info, BorderLayout.SOUTH);

      top.add(dropLabel, BorderLayout.WEST);
      top.add(rightTop, BorderLayout.CENTER);

      // also allow dropping directly onto the fileField
      installDragDrop(fileField);

      // update info when fileField changes
      fileField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
        private void changed() {
          updateFileInfo();
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
          changed();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
          changed();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
          changed();
        }
      });

      JPanel mid = new JPanel(new GridBagLayout());
      mid.setBorder(BorderFactory.createTitledBorder("Decryption"));
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.insets = new Insets(6, 6, 6, 6);
      gbc.gridx = 0;
      gbc.gridy = 0;
      gbc.anchor = GridBagConstraints.EAST;
      mid.add(new JLabel("Password:"), gbc);
      gbc.gridx = 1;
      gbc.gridy = 0;
      gbc.fill = GridBagConstraints.HORIZONTAL;
      gbc.weightx = 1.0;
      mid.add(passField, gbc);

      JPanel bottom = new JPanel(new BorderLayout(8, 8));
      JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
      JButton dec = flatButton("Decrypt");
      dec.setForeground(Color.black);
      JButton clear = new JButton("Clear");
      clear.addActionListener(e -> clearAll());
      dec.addActionListener(e -> startDecrypt());
      actions.add(clear);
      actions.add(dec);
      bottom.add(actions, BorderLayout.EAST);
      bottom.add(progressBar, BorderLayout.CENTER);

      add(top, BorderLayout.NORTH);
      add(mid, BorderLayout.CENTER);
      add(bottom, BorderLayout.SOUTH);
    }

    private void chooseFile() {
      JFileChooser fc = new JFileChooser();
      fc.setFileFilter(new FileNameExtensionFilter("Encrypted files (*.enc)", "enc"));
      int r = fc.showOpenDialog(this);
      if (r == JFileChooser.APPROVE_OPTION)
        fileField.setText(fc.getSelectedFile().getAbsolutePath());
    }

    // updateFileInfo for decrypt panel
    private void updateFileInfo() {
      String p = fileField.getText().trim();
      if (p.isEmpty()) {
        infoLabel.setText("No file selected");
        return;
      }
      File f = new File(p);
      if (!f.exists()) {
        infoLabel.setText("Not found");
        return;
      }
      infoLabel.setText(f.getName());
      try {
        JPanel top = (JPanel) getComponent(0);
        Component center = ((BorderLayout) top.getLayout()).getLayoutComponent(BorderLayout.CENTER);
        if (center instanceof JPanel) {
          JPanel rightTop = (JPanel) center;
          Component c = rightTop.getComponent(1);
          if (c instanceof JPanel) {
            JPanel infoPanel = (JPanel) c;
            Component sizeComp = infoPanel.getComponent(3);
            if (sizeComp instanceof JLabel) {
              ((JLabel) sizeComp).setText(human(f.length()));
            }
          }
        }
      } catch (Exception ignored) {
      }
    }

    private void clearAll() {
      fileField.setText("");
      passField.setText("");
      progressBar.setValue(0);
      setStatus("Ready");
    }

    private void startDecrypt() {
      String path = fileField.getText().trim();
      if (path.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Select .enc file.", "Info", JOptionPane.INFORMATION_MESSAGE);
        return;
      }
      File in = new File(path);
      if (!in.exists()) {
        JOptionPane.showMessageDialog(this, "File not found.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }
      char[] pw = passField.getPassword();
      if (pw.length == 0) {
        JOptionPane.showMessageDialog(this, "Password cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
        return;
      }

      enableComponents(false);
      setStatus("Decrypting...");
      progressBar.setValue(0);

      SwingWorker<File, Void> wk = new SwingWorker<>() {
        @Override
        protected File doInBackground() throws Exception {
          CryptoService.DecryptResult res = CryptoService.decryptToFile(in, pw, (processed, total) -> {
            int perc = total > 0 ? (int) ((processed * 100) / total) : 0;
            setProgress(Math.min(100, perc));
          });
          return res.output;
        }

        @Override
        protected void done() {
          try {
            File out = get();
            progressBar.setValue(100);
            int choice = JOptionPane.showConfirmDialog(DecryptPanel.this,
                "Decryption completed.\nOpen the decrypted file now?\n" + out.getAbsolutePath(),
                "Decryption Finished", JOptionPane.YES_NO_OPTION);
            if (choice == JOptionPane.YES_OPTION) {
              openFile(out);
            } else {
              JOptionPane.showMessageDialog(DecryptPanel.this, "Decryption completed: " + out.getAbsolutePath(), "Done",
                  JOptionPane.INFORMATION_MESSAGE);
            }
            setStatus("Decryption complete");
          } catch (Exception ex) {
            setStatus("Error");
            JOptionPane.showMessageDialog(DecryptPanel.this, "Decryption failed: " + ex.getMessage(), "Error",
                JOptionPane.ERROR_MESSAGE);
          } finally {
            // always wipe and clear password field, re-enable UI
            wipe(pw);
            passField.setText("");
            enableComponents(true);
          }
        }
      };
      wk.addPropertyChangeListener(evt -> {
        if ("progress".equals(evt.getPropertyName()))
          progressBar.setValue((Integer) evt.getNewValue());
      });
      wk.execute();
    }

    private void openFile(File f) {
      if (!Desktop.isDesktopSupported()) {
        JOptionPane.showMessageDialog(this, "Opening files is not supported on this platform.", "Info",
            JOptionPane.INFORMATION_MESSAGE);
        return;
      }
      Desktop desk = Desktop.getDesktop();
      if (!desk.isSupported(Desktop.Action.OPEN)) {
        JOptionPane.showMessageDialog(this, "Open action not supported on this platform.", "Info",
            JOptionPane.INFORMATION_MESSAGE);
        return;
      }
      try {
        desk.open(f);
      } catch (IOException ex) {
        JOptionPane.showMessageDialog(this, "Unable to open file: " + ex.getMessage(), "Error",
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  // ---------------- Crypto service (updated to AES-GCM) ----------------
  static class CryptoService {
    static final byte[] MAGIC = new byte[] { 'F', 'I', 'L', 'E' };
    static final byte VERSION = 0x01;
    static final SecureRandom RNG = new SecureRandom();
    static final int SALT_LEN = 16;
    static final int IV_LEN = 12; // 12 bytes recommended for GCM
    static final int KEY_BITS = 256; // use AES-256
    static final int PBKDF2_ITER = 200_000;
    static final int BUFFER = 4096;

    interface Progress {
      void onProgress(long processed, long total);
    }

    static void encryptToFile(File input, File output, char[] password, Progress cb) throws Exception {
      byte[] salt = new byte[SALT_LEN];
      RNG.nextBytes(salt);
      byte[] iv = new byte[IV_LEN];
      RNG.nextBytes(iv);
      SecretKey key = deriveKey(password, salt);

      try (FileInputStream fis = new FileInputStream(input);
          BufferedInputStream bis = new BufferedInputStream(fis);
          FileOutputStream fos = new FileOutputStream(output);
          BufferedOutputStream bos = new BufferedOutputStream(fos)) {

        // header: magic (4) | version (1) | salt | iv | nameLen(8) | name
        bos.write(MAGIC);
        bos.write(VERSION);
        bos.write(salt);
        bos.write(iv);
        byte[] name = input.getName().getBytes(StandardCharsets.UTF_8);
        bos.write(ByteBuffer.allocate(8).putLong(name.length).array());
        bos.write(name);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

        try (CipherOutputStream cos = new CipherOutputStream(bos, cipher)) {
          long total = input.length();
          byte[] buf = new byte[BUFFER];
          int r;
          long processed = 0;
          while ((r = bis.read(buf)) != -1) {
            cos.write(buf, 0, r);
            processed += r;
            if (cb != null)
              cb.onProgress(processed, total);
          }
          cos.flush();
        }
      } finally {
        wipe(password);
        // try to zero secret key material if possible
        try {
          byte[] kb = key.getEncoded();
          if (kb != null)
            Arrays.fill(kb, (byte) 0);
        } catch (Throwable ignored) {
        }
      }
    }

    static class DecryptResult {
      final File output;

      DecryptResult(File f) {
        this.output = f;
      }
    }

    /**
     * decryptToFile:
     * - reads header (salt/iv/original name),
     * - writes decrypted output to a temporary file in same directory,
     * - only when fully successful, moves temp to final "decrypted_<original>"
     * filename,
     * - if any error occurs (AEAD failure/wrong password/tamper/IO), deletes temp
     * and throws an exception.
     */
    static DecryptResult decryptToFile(File input, char[] password, Progress cb) throws Exception {
      // We'll build and return the final File only on success.
      try (FileInputStream fis = new FileInputStream(input);
          BufferedInputStream bis = new BufferedInputStream(fis)) {

        byte[] magic = new byte[4];
        if (bis.read(magic) != 4)
          throw new IOException("Invalid file");
        for (int i = 0; i < 4; i++)
          if (magic[i] != MAGIC[i])
            throw new IOException("Not our encrypted file");
        int ver = bis.read();
        if (ver != VERSION)
          throw new IOException("Unsupported version");

        byte[] salt = new byte[SALT_LEN];
        if (bis.read(salt) != salt.length)
          throw new IOException("Invalid file (salt)");
        byte[] iv = new byte[IV_LEN];
        if (bis.read(iv) != iv.length)
          throw new IOException("Invalid file (iv)");
        byte[] lenB = new byte[8];
        if (bis.read(lenB) != 8)
          throw new IOException("Invalid file (name len)");
        long nameLen = ByteBuffer.wrap(lenB).getLong();
        if (nameLen < 0 || nameLen > 4096)
          throw new IOException("Invalid filename length");
        byte[] nameB = new byte[(int) nameLen];
        if (bis.read(nameB) != nameB.length)
          throw new IOException("Invalid file (name)");
        String original = new String(nameB, StandardCharsets.UTF_8);

        // compute remaining ciphertext length for progress reporting
        long headerLen = 4 + 1 + SALT_LEN + IV_LEN + 8 + nameLen;
        long totalCipher = Math.max(0L, input.length() - headerLen);

        SecretKey key = deriveKey(password, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        // create a temp file in same directory to avoid cross-filesystem rename issues
        File parent = input.getParentFile();
        File tmp = File.createTempFile("dec_tmp_", ".tmp", parent);
        boolean success = false;
        try (FileOutputStream fos = new FileOutputStream(tmp);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            CipherInputStream cis = new CipherInputStream(bis, cipher)) {

          byte[] buf = new byte[BUFFER];
          int r;
          long processed = 0;
          while ((r = cis.read(buf)) != -1) {
            bos.write(buf, 0, r);
            processed += r;
            if (cb != null)
              cb.onProgress(processed, totalCipher);
          }
          bos.flush();
          // if we reached here without exception, authentication succeeded
          success = true;
        } catch (IOException ex) {
          // check cause for AEAD failure (wrong password/tamper)
          Throwable cause = ex.getCause();
          if (cause instanceof AEADBadTagException) {
            // ensure temp file removed before throwing
            try {
              Files.deleteIfExists(tmp.toPath());
            } catch (Exception ignored) {
            }
            throw new IOException("Incorrect password or corrupted file", ex);
          }
          // Other IO errors: clean up and rethrow
          try {
            Files.deleteIfExists(tmp.toPath());
          } catch (Exception ignored) {
          }
          throw ex;
        } finally {
          // wipe password & zero key bytes
          wipe(password);
          try {
            byte[] kb = key.getEncoded();
            if (kb != null)
              Arrays.fill(kb, (byte) 0);
          } catch (Throwable ignored) {
          }
        }

        if (!success) {
          // ensure tmp removed if not successful
          try {
            Files.deleteIfExists(tmp.toPath());
          } catch (Exception ignored) {
          }
          throw new IOException("Decryption failed");
        }

        // move temp -> final name atomically if possible
        File out = new File(parent, "decrypted_" + original);
        try {
          Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException amnse) {
          // fallback to non-atomic move
          Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return new DecryptResult(out);
      }
    }

    static SecretKey deriveKey(char[] password, byte[] salt) throws Exception {
      PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITER, KEY_BITS);
      SecretKeyFactory skf;
      try {
        skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      } catch (Exception ex) {
        skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      }
      SecretKey tmp = skf.generateSecret(spec);
      byte[] keyBytes = tmp.getEncoded();
      SecretKeySpec secret = new SecretKeySpec(keyBytes, "AES");
      spec.clearPassword();
      Arrays.fill(keyBytes, (byte) 0);
      return secret;
    }

    static void wipe(char[] c) {
      if (c != null)
        Arrays.fill(c, '\0');
    }
  }

  // ---------------- utility ----------------
  static void wipe(char[] c) {
    if (c != null)
      Arrays.fill(c, '\0');
  }
}
