package com.xqbase.net.tools;

import java.io.IOException;

import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.JTextField;

import com.xqbase.net.misc.DumpFilterFactory;
import com.xqbase.net.misc.ForwardServer;

public class ForwardFrame extends ConnectorFrame {
	private static final long serialVersionUID = 1L;

	private static final String DUMP_NONE = "None";
	private static final String DUMP_BINARY = "Binary";
	private static final String DUMP_TEXT = "Text";
	private static final String DUMP_FOLDER = "Folder";

	private static final int SLIDER_NO_LIMIT = 4;

	private JSlider slider = new JSlider(0, SLIDER_NO_LIMIT, SLIDER_NO_LIMIT);
	private JTextField txtPort = new JTextField("23");
	private JTextField txtRemoteHost = new JTextField("localhost");
	private JTextField txtRemotePort = new JTextField("2323");
	private JComboBox<String> cmbDump = new JComboBox<>(new
			String[] {DUMP_NONE, DUMP_BINARY, DUMP_TEXT, DUMP_FOLDER});
	private JFileChooser chooser = new JFileChooser();

	private ForwardServer server = null;

	void choose() {
		if (!cmbDump.getSelectedItem().equals(DUMP_FOLDER)) {
			return;
		}
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int retVal = chooser.showOpenDialog(ForwardFrame.this);
		if (retVal != JFileChooser.APPROVE_OPTION) {
			cmbDump.setSelectedItem(DUMP_NONE);
		}
	}

	void stop() {
		trayIcon.setToolTip(getTitle());
		startMenuItem.setLabel("Start");
		startButton.setText("Start");
		txtPort.setEnabled(true);
		txtRemoteHost.setEnabled(true);
		txtRemotePort.setEnabled(true);
		cmbDump.setEnabled(true);
	}

	@Override
	protected void start() {
		if (server != null) {
			connector.remove(server);
			server = null;
			stop();
			return;
		}
		trayIcon.setToolTip(String.format(getTitle() + " (%s->%s/%s)",
				txtPort.getText(), txtRemoteHost.getText(), txtRemotePort.getText()));
		startMenuItem.setLabel("Stop");
		startButton.setText("Stop");
		txtPort.setEnabled(false);
		txtRemoteHost.setEnabled(false);
		txtRemotePort.setEnabled(false);
		cmbDump.setEnabled(false);

		try {
			server = new ForwardServer(connector, Integer.parseInt(txtPort.getText()),
					txtRemoteHost.getText(), Integer.parseInt(txtRemotePort.getText()));
		} catch (IOException | IllegalArgumentException e) {
			stop();
			JOptionPane.showMessageDialog(this, e.getMessage(),
					getTitle(), JOptionPane.WARNING_MESSAGE);
			return;
		}
		connector.add(server);
		if (cmbDump.getSelectedItem().equals(DUMP_NONE)) {
			return;
		}
		DumpFilterFactory dff = new DumpFilterFactory();
		if (cmbDump.getSelectedItem().equals(DUMP_TEXT)) {
			dff.setDumpText(true);
		} else if (cmbDump.getSelectedItem().equals(DUMP_FOLDER)) {
			dff.setDumpStream(null);
			dff.setDumpFolder(chooser.getSelectedFile());
		}
		server.getFilterFactories().add(dff);
	}

	public ForwardFrame() {
		super("Forward", "Terminal", 288, 102, true);

		JLabel lblLimit = new JLabel("Speed Limit");
		lblLimit.setBounds(6, 6, 96, 24);
		add(lblLimit);

		final JLabel lblSlider = new JLabel("No Limit");
		lblSlider.setBounds(180, 6, 84, 24);
		add(lblSlider);

		slider.setBounds(90, 6, 78, 24);
		slider.setSnapToTicks(true);
		slider.addChangeListener(e -> {
			int sliderValue = ((JSlider) e.getSource()).getValue();
			lblSlider.setText(sliderValue == SLIDER_NO_LIMIT ?
					"No Limit" : (1 << (sliderValue << 1)) + "KB/s");
			// TODO Speed Limit
		});
		add(slider);

		JLabel lblPort = new JLabel("Port");
		lblPort.setBounds(6, 36, 36, 24);
		add(lblPort);

		txtPort.setBounds(42, 36, 42, 24);
		txtPort.enableInputMethods(false);
		add(txtPort);

		JLabel lblRemote = new JLabel("Remote");
		lblRemote.setBounds(96, 36, 48, 24);
		add(lblRemote);

		txtRemoteHost.setBounds(144, 36, 84, 24);
		txtRemoteHost.enableInputMethods(false);
		add(txtRemoteHost);

		JLabel lblSlash = new JLabel("/");
		lblSlash.setBounds(234, 36, 12, 24);
		add(lblSlash);

		txtRemotePort.setBounds(246, 36, 36, 24);
		txtRemotePort.enableInputMethods(false);
		add(txtRemotePort);

		JLabel lblDump = new JLabel("Dump");
		lblDump.setBounds(6, 66, 36, 24);
		add(lblDump);

		cmbDump.setBounds(42, 66, 72, 24);
		cmbDump.setEditable(false);
		cmbDump.setSelectedItem(DUMP_NONE);
		cmbDump.addActionListener(e -> choose());
		add(cmbDump);

		startButton.setBounds(120, 66, 78, 30);
		exitButton.setBounds(204, 66, 78, 30);
	}

	public static void main(String[] args) {
		invoke(ForwardFrame.class);
	}
}