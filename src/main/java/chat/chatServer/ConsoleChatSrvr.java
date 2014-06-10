package chat.chatServer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;


public class ConsoleChatSrvr {
    private ServerSocket serverSocket;
    private Socket socket;
    private static Set<Socket> list = new HashSet<>();
    private static Map<Integer, String> userIdNick = new HashMap<>();
    private Integer userID = 0;

    public static void main(String[] args) {
        new ConsoleChatSrvr().startServerChat();
    }

    public void startServerChat() {
        try {
            serverSocket = new ServerSocket(30000);
            while (true) {
                socket = serverSocket.accept();
                userID++;
                list.add(socket);
                new Thread(new ClientThread(socket, userID)).start();

            }

        } catch (IOException e) {
            e.printStackTrace();
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

    static class ClientThread implements Runnable{
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
            nickName = "someChatUser-" + id + "-(" + socket.getInetAddress().getHostAddress() + ")";
        }

        @Override
        public void run() {
            try {
                pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
                br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                nowOnlineUsers();
                String tmpMess = "[SERVER]>: user '" + nickName + "' join to chat..";
                System.out.println(tmpMess);
                sendAll(tmpMess);
                sendNickNameToClient();
                pw.println("[SERVER]>: to change your nick name, input '/nick yourName'.");
                pw.println("[SERVER]>: to view list all commands, input '/help'.");
                sendCurrentUsersOnline();

                String msg;
                while (((msg = br.readLine()) != null) && !done) {

                    if (!msgIsCommand(msg) && !msg.equals("")) {
                        tmpMess = " " + nickName + ">: " + msg;
                        System.out.println(tmpMess);
                        sendAll(tmpMess);
                    }
                }
            } catch (IOException e) {
                System.out.println("exception on ClientThread.run(): " + e);
//                e.printStackTrace();
            } finally {
                if (socket != null) {
                    try {
                        list.remove(socket);
                        userIdNick.remove(id);
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
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
                    System.out.println(m);
                    sendAll(m);
                    sendNickNameToClient();
                    isCommand = true;
                }
                if (msg.trim().equalsIgnoreCase("/exit")) {
                    done = true;
                    m = "[SERVER]>: user '" + nickName + "' left chat..";
                    System.out.println(m);
                    sendAll(m);
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
                    System.out.println("error in sendAll() method: " + e);
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
}
