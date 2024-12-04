import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnhancedMinecraftServerPing {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Minecraft Server Ping");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(new Color(30, 33, 40));

        JLabel titleLabel = new JLabel("Minecraft Server Ping Tool", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(new Color(173, 216, 230));
        titleLabel.setBorder(new EmptyBorder(20, 0, 20, 0));
        frame.add(titleLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new GridLayout(5, 1, 10, 10));
        centerPanel.setBackground(new Color(30, 33, 40));
        centerPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        JTextField serverField = new JTextField();
        serverField.setFont(new Font("Arial", Font.PLAIN, 18));
        serverField.setBackground(new Color(40, 44, 52));
        serverField.setForeground(Color.WHITE);
        serverField.setCaretColor(Color.WHITE);
        serverField.setText("play.hypixel.net");
        serverField.setToolTipText("Enter the server IP here...");
        centerPanel.add(serverField);

        JLabel statusLabel = new JLabel("Status: Waiting for input...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 20));
        statusLabel.setForeground(Color.LIGHT_GRAY);
        centerPanel.add(statusLabel);

        JLabel playerLabel = new JLabel("Players: N/A", SwingConstants.CENTER);
        playerLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        playerLabel.setForeground(Color.LIGHT_GRAY);
        centerPanel.add(playerLabel);

        JLabel motdLabel = new JLabel("<html>MOTD: <span style='color:gray;'>N/A</span></html>", SwingConstants.CENTER);
        motdLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        centerPanel.add(motdLabel);

        frame.add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout());
        bottomPanel.setBackground(new Color(30, 33, 40));

        JButton pingButton = new JButton("Ping Server");
        pingButton.setFont(new Font("Arial", Font.BOLD, 18));
        pingButton.setForeground(Color.WHITE);
        pingButton.setBackground(new Color(34, 139, 34));
        pingButton.setFocusPainted(false);
        pingButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        bottomPanel.add(pingButton, BorderLayout.CENTER);

        frame.add(bottomPanel, BorderLayout.SOUTH);

        pingButton.addActionListener(e -> {
            String server = serverField.getText().trim();
            if (server.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please enter a server address.");
                return;
            }
            try {
                String result = pingServer(server, 25565);

                String motd = extractFromJson(result, "\"description\"\\s*:\\s*\\{.*?\"text\"\\s*:\\s*\"(.*?)\"");
                motd = formatMotd(motd);
                String players = extractFromJson(result, "\"online\"\\s*:\\s*(\\d+)");
                String maxPlayers = extractFromJson(result, "\"max\"\\s*:\\s*(\\d+)");

                statusLabel.setText("Status: Online");
                statusLabel.setForeground(new Color(0, 255, 0));

                playerLabel.setText("Players: " + players + "/" + maxPlayers);
                playerLabel.setForeground(Color.WHITE);

                motdLabel.setText("<html>MOTD: " + motd + "</html>");
                motdLabel.setForeground(Color.WHITE);

            } catch (Exception ex) {
                statusLabel.setText("Status: Offline");
                statusLabel.setForeground(Color.RED);
                playerLabel.setText("Players: N/A");
                motdLabel.setText("<html>MOTD: <span style='color:gray;'>N/A</span></html>");
            }
        });

        frame.setVisible(true);
    }

    private static String pingServer(String host, int port) throws Exception {
        Socket socket = new Socket(host, port);
        OutputStream out = socket.getOutputStream();
        InputStream in = socket.getInputStream();

        byte[] handshake = createHandshakePacket(host, port);
        out.write(handshake);

        out.write(new byte[]{1, 0});

        int length = readVarInt(in);
        int packetId = readVarInt(in);

        if (packetId == 0) {
            int jsonLength = readVarInt(in);
            byte[] jsonBytes = new byte[jsonLength];
            in.read(jsonBytes);
            String jsonResponse = new String(jsonBytes);

            socket.close();
            return jsonResponse;
        } else {
            socket.close();
            throw new Exception("Invalid packet ID: " + packetId);
        }
    }

    private static byte[] createHandshakePacket(String host, int port) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(buffer);

        data.writeByte(0x00);
        writeVarInt(data, 754);
        writeVarInt(data, host.length());
        data.writeBytes(host);
        data.writeShort(port);
        writeVarInt(data, 1);

        byte[] handshakePacket = buffer.toByteArray();
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        writeVarInt(packet, handshakePacket.length);
        packet.write(handshakePacket);

        return packet.toByteArray();
    }

    private static int readVarInt(InputStream in) throws Exception {
        int value = 0;
        int position = 0;
        byte currentByte;

        while (true) {
            currentByte = (byte) in.read();
            value |= (currentByte & 0x7F) << position;

            if ((currentByte & 0x80) != 0x80) break;
            position += 7;

            if (position >= 32) throw new Exception("VarInt is too big");
        }
        return value;
    }

    private static void writeVarInt(OutputStream out, int value) throws Exception {
        while (true) {
            if ((value & 0xFFFFFF80) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static String extractFromJson(String json, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "N/A";
    }

    private static String formatMotd(String motd) {
        motd = motd.replace("§0", "<span style='color:#000000;'>");
        motd = motd.replace("§1", "<span style='color:#0000AA;'>");
        motd = motd.replace("§2", "<span style='color:#00AA00;'>");
        motd = motd.replace("§3", "<span style='color:#00AAAA;'>");
        motd = motd.replace("§4", "<span style='color:#AA0000;'>");
        motd = motd.replace("§5", "<span style='color:#AA00AA;'>");
        motd = motd.replace("§6", "<span style='color:#FFAA00;'>");
        motd = motd.replace("§7", "<span style='color:#AAAAAA;'>");
        motd = motd.replace("§8", "<span style='color:#555555;'>");
        motd = motd.replace("§9", "<span style='color:#5555FF;'>");
        motd = motd.replace("§a", "<span style='color:#55FF55;'>");
        motd = motd.replace("§b", "<span style='color:#55FFFF;'>");
        motd = motd.replace("§c", "<span style='color:#FF5555;'>");
        motd = motd.replace("§d", "<span style='color:#FF55FF;'>");
        motd = motd.replace("§e", "<span style='color:#FFFF55;'>");
        motd = motd.replace("§f", "<span style='color:#FFFFFF;'>");
        motd = motd.replace("§r", "</span>");
        return motd;
    }
}
