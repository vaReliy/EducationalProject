package chat.chatClient;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;


public class ChatClientGui extends JFrame {
    private Socket socket;
    private PrintWriter pw;
    private BufferedReader br;
    private boolean isConnected;
    private String nickName;
    private volatile Collection<String> usersOnline = new PriorityQueue<>();

    private JTextField inputField;
    private JTextArea chatField;
    private JTextArea usersField;
    private JTextField ipField;
    private JTextField portField;
    private JLabel statusConnection;
    private JLabel chatRoom;

    public ChatClientGui() {
        setTitle("ChatClientGui v0.5 beta");
        setLayout(new FlowLayout());
        setBounds(100, 50, 800, 650);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        initComponents();
        setVisible(true);
    }

    private void initComponents(){
        //up panel:
        JPanel upPanel = new JPanel();
        upPanel.setBorder(new TitledBorder(new EtchedBorder(), "Connection information"));
        upPanel.setPreferredSize(new Dimension(780, 100));
        ipField = new JTextField("localhost");
        ipField.setPreferredSize(new Dimension(200, 25));
        ipField.setHorizontalAlignment(JTextField.RIGHT);
        portField = new JTextField("30000");
        portField.setPreferredSize(new Dimension(100, 25));
        JButton connectBtn = new JButton("Connect");
        connectBtn.setPreferredSize(new Dimension(100, 25));
        JButton disconnectBtn = new JButton("Disconnect");
        disconnectBtn.setPreferredSize(new Dimension(120, 25));
        statusConnection = new JLabel("NOT CONNECTED!");
        chatRoom = new JLabel("CHAT ROOM");
        upPanel.add(ipField);
        upPanel.add(portField);
        upPanel.add(connectBtn);
        upPanel.add(disconnectBtn);
        upPanel.add(statusConnection);
        upPanel.add(new JLabel("--- --- --- --- --- --- --- --- (Server IP) | (Sever port) --- --- --- --- --- --- --- --- --- --- --- ---  Status connection  --- --- ---"));
        upPanel.add(chatRoom);
        //middle panel (left):
        JPanel middlePanelLeft = new JPanel();
        middlePanelLeft.setBorder(new TitledBorder(new EtchedBorder(), "Chat Room"));
        chatField = new JTextArea();
        chatField.setLineWrap(true);
        chatField.setEditable(false);
        JScrollPane scrollMSG =new JScrollPane(chatField);
        scrollMSG.setPreferredSize(new Dimension(600,400));
        scrollMSG.getViewport().setView(chatField);
        scrollMSG.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        DefaultCaret caret = (DefaultCaret) chatField.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        middlePanelLeft.add(scrollMSG);
        //down panel:
        JPanel downPanel = new JPanel();
        downPanel.setBorder(new TitledBorder(new EtchedBorder(), "Input your message here:"));
        inputField = new JTextField();
        inputField.setPreferredSize(new Dimension(760, 30));
        downPanel.add(inputField);
        //right panel:
        JPanel rightPanel = new JPanel();
        rightPanel.setBorder(new TitledBorder(new EtchedBorder(), "Users online:"));
        usersField = new JTextArea();
        usersField.setLineWrap(true);
        usersField.setEditable(false);
        JScrollPane scrollUsersPanel =new JScrollPane(usersField);
        scrollUsersPanel.setPreferredSize(new Dimension(130,400));
        scrollUsersPanel.getViewport().setView(usersField);
        scrollUsersPanel.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        rightPanel.add(scrollUsersPanel);

        //add comp to frame:
        add(upPanel);
        add(middlePanelLeft);
        add(rightPanel);
        add(downPanel);


        //actions:
        inputField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pw.println(inputField.getText());
                inputField.setText("");
            }
        });

        connectBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isConnected) {
                    try {
                        int port = Integer.parseInt(portField.getText());
                        socket = new Socket(ipField.getText(), port);
                        pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                        br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        isConnected = true;

                        Thread thChatRoom = new ChatRoomBlink();
                        thChatRoom.setDaemon(true);

                        Thread chatReaderFromServer = new ChatReaderToChatRoom();
                        chatReaderFromServer.setDaemon(true);

                        Thread statusLabel = new StatusConnection();

                        Thread statusOnlineUsers = new StatusOnlineUsers();
                        statusOnlineUsers.setDaemon(true);

                        thChatRoom.start();
                        chatReaderFromServer.start();
                        statusLabel.start();
                        statusOnlineUsers.start();

                    } catch (IOException err) {
                        err.printStackTrace();
                    } /*finally {
                    try {
                        statusConnection.setText("disconnected from " + socket.getInetAddress().getHostAddress());
                        socket.close();
                    } catch (IOException err) {
                        System.out.println("Error closing socket " + err);
                    }
                }*/

                }
            }
        });

        disconnectBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    pw.println("/exit");
                    isConnected = false;
                    socket.close();
                } catch (IOException err) {
                    System.out.println("Error closing socket " + err);
                }
            }
        });

    }

    private boolean isCommand(String msg){
        boolean result = false;
        if (msg.charAt(0) == '@') {
            result = true;
        }
        return result;
    }

    private synchronized void command(String msg) {
        String tmp[] = msg.split(" ");
        if (tmp[0].equals("@YouNickNameIs")) {
            nickName = tmp[1];
        }
        if (tmp[0].equals("@NowOnlineUsers")) {
            usersOnline.clear();
            for (int i = 1; i < tmp.length; i++) {
                usersOnline.add(tmp[i]);
            }
            usersField.setText("");
            for (String nick : usersOnline) {
                usersField.append(nick);
                usersField.append("\n");
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ChatClientGui();
            }
        });
    }

    private class ChatRoomBlink extends Thread {
        @Override
        public void run() {
            try {
                while (!isInterrupted()){
                    String str = "CHAT ROOM";
                    if (nickName != null) {
                        str = nickName;
                    }
                    chatRoom.setText("            " +str+ "            ");
                    Thread.sleep(1000);
                    chatRoom.setText("   :::   " +str+ "   :::   ");
                    Thread.sleep(1000);
                    chatRoom.setText("===   :::   " +str+ "   :::   ===");
                    Thread.sleep(1000);
                    chatRoom.setText("---   ===   :::   " +str+ "   :::   ===   ---");
                    Thread.sleep(1000);
                    chatRoom.setText("---   ---   ===   :::   " +str+ "   :::   ===   ---   ---");
                    Thread.sleep(1000);
                    chatRoom.setText("---   ---   ---   ===   :::   " +str+ "   :::   ===   ---   ---   ---");
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
        }
    }

    private class ChatReaderToChatRoom extends Thread {
        @Override
        public void run() {
            String msg = "";
            chatField.setText("");
            try {
                while (socket != null && ((msg = br.readLine()) != null)) {
                    if (!isCommand(msg)) {
                        chatField.append(msg + "\n");
                    } else {
                        command(msg);
                    }
                }
            } catch (SocketException se) {
                System.out.println("Exception on thread ChatReaderToTextArray: " + se);
                isConnected = false;
            } catch (IOException e1) {
                System.out.println("Exception on thread ChatReaderToTextArray: " + e1);
            }
        }
    }

    private class StatusConnection extends Thread{
        @Override
        public void run() {

            try {
                while (!isInterrupted()){
                    if (isConnected) {
                        statusConnection.setText("connected to " + socket.getInetAddress().getHostAddress());
                    } else {
                        statusConnection.setText("disconnected from " + socket.getInetAddress().getHostAddress());
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e1) {
                System.out.println("Exception on thread StatusConnection: " + e1);
            }
        }
    }

    private class StatusOnlineUsers extends Thread{
        @Override
        public void run() {
            try {
                while (!isInterrupted()) {
                    pw.println("/nowOnline");
                    Thread.sleep(10000);
                }
            } catch (ConcurrentModificationException ex) {
                System.out.println(ex);
                ex.printStackTrace();
            } catch (InterruptedException e1) {
                System.out.println("Exception on thread StatusOnlineUsers: " + e1);
            }
        }
    }
}
