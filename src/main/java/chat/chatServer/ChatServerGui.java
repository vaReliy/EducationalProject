package chat.chatServer;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;


public class ChatServerGui extends JFrame {
    private JTextArea consoleArea;
    private ServerSocket serverSocket;
    private Socket socket;
    private static Set<Socket> list = new HashSet<>();
    private static Map<Integer, String> userIdNick = new HashMap<>();
    private Integer userID = 0;
    private JTextField portField;
    private Thread serverThread;
    private JLabel statusServerLabel;
    private JButton startServerBtn;
    private StatusConnectionLabelBlink statusConnectionLabelBlink;

    public ChatServerGui() {
        setTitle("ChatServer v0.3 alpha");
        setLayout(new FlowLayout());
        setBounds(100, 50, 680, 575);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        initComponents();
        setVisible(true);
    }

    private void initComponents() {
        //up panel
        JPanel upPanel = new JPanel();
        upPanel.setBorder(new TitledBorder(new EtchedBorder(), "Server settings"));
        upPanel.setPreferredSize(new Dimension(620, 80));
        Integer serverPort = 30000;
        portField = new JTextField(Integer.toString(serverPort));
        portField.setPreferredSize(new Dimension(100, 25));
        startServerBtn = new JButton("Start server");
        startServerBtn.setPreferredSize(new Dimension(150, 25));
        final JButton stopServerBtn = new JButton("Stop server");
        stopServerBtn.setPreferredSize(new Dimension(150, 25));
        stopServerBtn.setEnabled(false);
        statusServerLabel = new JLabel(" SERVER IS NOT RUN! ");
        JLabel infoSettingsLabel = new JLabel("|  server port  |  push to start server  |  push to stop server  |        server info        |");

        //down panel
        JPanel downPanel = new JPanel();
        downPanel.setBorder(new TitledBorder(new EtchedBorder(), "Service console"));
        consoleArea = new JTextArea();
        consoleArea.setLineWrap(true);
        consoleArea.setEditable(false);
        JScrollPane scrollMSG =new JScrollPane(consoleArea);
        scrollMSG.setPreferredSize(new Dimension(600,400));
        scrollMSG.getViewport().setView(consoleArea);
        scrollMSG.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        DefaultCaret caret = (DefaultCaret) consoleArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        downPanel.add(scrollMSG);

        upPanel.add(portField);
        upPanel.add(startServerBtn);
        upPanel.add(stopServerBtn);
        upPanel.add(statusServerLabel);
        upPanel.add(infoSettingsLabel);

        add(upPanel);
        add(downPanel);

        //actions:
        startServerBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                actionStartServer();
                startServerBtn.setEnabled(false);
                stopServerBtn.setEnabled(true);
                portField.setEnabled(false);
            }
        });

        stopServerBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                actionStopServer();
                startServerBtn.setEnabled(true);
                stopServerBtn.setEnabled(false);
                portField.setEnabled(true);
            }
        });

        statusConnectionLabelBlink = new StatusConnectionLabelBlink();
        statusConnectionLabelBlink.setDaemon(true);
        statusConnectionLabelBlink.start();
    }

    private void actionStartServer() {
        consoleArea.setText("");
        serverThread = new Thread(){
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(Integer.parseInt(portField.getText()));
                    while (true) {
                        socket = serverSocket.accept();
                        userID++;
                        list.add(socket);
                        new Thread(new ClientThread(socket, userID)).start();
                    }
                } catch (IOException e) {
                    consoleArea.append(e + "\n");
//                    consoleArea.append("Exception on actionStartServer(): " + e + "\n");
//                    consoleArea.append(e.getStackTrace().toString() + "\n");
                } finally {
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                        if (serverSocket != null) {
                            serverSocket.close();
                        }
                    } catch (IOException e) {}
                }
            }
        };
        serverThread.setDaemon(true);
        serverThread.start();

    }

    private void actionStopServer(){
        try {
            serverThread.interrupt();
            serverSocket.close();
//            socket.close();
        } catch (IOException e) {
            consoleArea.append(e.getStackTrace().toString() + "\n");
        }
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ChatServerGui();
            }
        });
    }


    class ClientThread implements Runnable{
        private PrintWriter pw;
        private PrintWriter pwAll;
        private BufferedReader br;
        private Socket socket;
        private boolean done = false;
        private String nickName;
        private Integer id;
        private String[] commands = {
                "'/exit' - leave chat",
                "'/help' - view list all commands (this list)",
                "'/nick' - change your nick name",
                "'/users' - view list all users in online now"};

        public ClientThread(Socket socket, Integer id) {
            this.socket = socket;
            this.id = id;
            nickName = "User-" + id + "-(" + socket.getInetAddress().getHostAddress() + ")";
        }

        @Override
        public void run() {
            try {
                pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                nowOnlineUsers();
                String tmpMess = "[SERVER]>: user '" + nickName + "' join to chat..";
                consoleArea.append(tmpMess + "\n");
                sendAll(tmpMess);
                sendNickNameToClient();
                pw.println("[SERVER]>: to change your nick name, input '/nick yourName'.");
                pw.println("[SERVER]>: to view list all commands, input '/help'.");
                sendCurrentUsersOnline();

                String msg;
                while (((msg = br.readLine()) != null) && !done) {

                    if (!msgIsCommand(msg) && !msg.equals("")) {
                        tmpMess = " " + nickName + ">: " + msg;
                        consoleArea.append(tmpMess + "\n");
                        sendAll(tmpMess);
                    }
                }
            } catch (IOException e) {
                consoleArea.append("exception on ClientThread.run(): " + e + "\n");
            } finally {
                if (socket != null) {
                    try {
                        list.remove(socket);
                        userIdNick.remove(id);
                        socket.close();
                    } catch (IOException e) {
                        consoleArea.append(e.getStackTrace().toString() + "\n");
                    }
                }
            }
        }

        private boolean msgIsCommand(String msg) {
            String m;
            boolean isCommand = false;
            if (msg.length() > 4 && msg.charAt(0) == '/') {
                if (msg.substring(0,5).equalsIgnoreCase("/nick") && (msg.length() > 8 && msg.charAt(6) != ' ')) {
                    String oldNick = nickName;
                    nickName = msg.substring(6, msg.length());
                    m = "[SERVER]>: user '" + oldNick + "' change nick to '" + nickName + "'.";
//                    System.out.println(m);
                    consoleArea.append(m + "\n");
                    sendAll(m);
                    sendNickNameToClient();
                    isCommand = true;
                }
                if (msg.trim().equalsIgnoreCase("/exit")) {
                    done = true;
                    m = "[SERVER]>: user '" + nickName + "' left chat..";
//                    System.out.println(m);
                    consoleArea.append(m + "\n");
                    sendAll(m);
                    pw.println("@YouIsDisconnect");
                    isCommand = true;
                }
                if (msg.trim().equalsIgnoreCase("/users")) {
                    sendCurrentUsersOnline();
                    isCommand = true;
                }
                if (msg.trim().equalsIgnoreCase("/nowOnline")) {
                    nowOnlineUsers();
                    isCommand = true;
                }
                if (msg.trim().equalsIgnoreCase("/help")) {
                    printAllCommands();
                    isCommand = true;
                }
                if (!isCommand) {
                    pw.println(msg + " - wrong COMMAND!");
                }
                return true;
            }
            return false;
        }

        private void sendNickNameToClient(){
            pw.println("@YouNickNameIs " + nickName);
            userIdNick.put(id, nickName);
        }

        private void sendCurrentUsersOnline(){
            StringBuilder sb = new StringBuilder("[SERVER]>: users on chat: ");
            for (Map.Entry<Integer, String> entry: userIdNick.entrySet()) {
                sb.append("[").append(entry.getValue()).append("] ");
            }
            pw.println(sb.toString());
        }
        private void nowOnlineUsers(){
            StringBuilder sb = new StringBuilder("@NowOnlineUsers ");
            for (Map.Entry<Integer, String> entry: userIdNick.entrySet()) {
                sb.append(entry.getValue()).append(" ");
            }
            pw.println(sb.toString());
        }

        private void sendAll(String message) {
            for (Socket s: list) {
                try {
                    pwAll = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true);
                } catch (IOException e) {
                    consoleArea.append("error in sendAll() method: " + "\n");
                }
                pwAll.println(message);
            }
        }

        private void printAllCommands(){
            for (String command: commands) {
                pw.println(command);
            }
        }
    }

    private class StatusConnectionLabelBlink extends Thread {
        @Override
        public void run() {
            try {
                while (!isInterrupted()){
                    //FIXME label DO Start server
                    if (serverSocket != null && !startServerBtn.isEnabled()) {
                        statusServerLabel.setText(" SERVER IS RUN! ");
                    }
                    if (serverSocket.isClosed() && startServerBtn.isEnabled()) {
                        statusServerLabel.setText(" SERVER IS NOT RUN! ");
                    }
                    if (socket != null && !startServerBtn.isEnabled()) {
                        statusServerLabel.setText(socket.getInetAddress().getHostAddress());
                    } else {
                        statusServerLabel.setText(" SERVER IS NOT RUN! ");
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
        }
    }

}


