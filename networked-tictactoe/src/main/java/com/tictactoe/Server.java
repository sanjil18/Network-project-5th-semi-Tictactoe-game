package com.tictactoe;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int PORT = 12345;
    private static final int MAX_PLAYERS = 2;
    private static List<ClientHandler> clients = new ArrayList<>();
    private static char[][] board = new char[3][3];
    private static char currentPlayer = 'X';
    private static String playerXName = "";
    private static String playerOName = "";
    private static int playerXWins = 0;
    private static int playerOWins = 0;
    private static int draws = 0;
    private static ServerSocket serverSocket;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  NETWORKED TIC-TAC-TOE SERVER");
        System.out.println("========================================");

        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on port: " + PORT);
            System.out.println("Server IP Addresses:");
            printServerIPs();
            System.out.println("Waiting for 2 players to connect...");

            while (clients.size() < MAX_PLAYERS) {
                Socket socket = serverSocket.accept();
                socket.setKeepAlive(true);

                int playerId = clients.size() + 1;
                System.out.println("Player " + playerId + " connected: " +
                        socket.getInetAddress().getHostAddress());

                ClientHandler handler = new ClientHandler(socket, playerId);
                clients.add(handler);
                new Thread(handler).start();
            }

            System.out.println("Both players connected. Game starting!");
            broadcast("START");
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printServerIPs() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isUp() && !iface.isLoopback()) {
                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof Inet4Address) {
                            System.out.println("  -> " + addr.getHostAddress());
                        }
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("  Unable to retrieve IPs");
        }
    }

    public static synchronized boolean makeMove(int row, int col, char player, ClientHandler client) {
        if (board[row][col] == '\0' && player == currentPlayer) {
            board[row][col] = player;
            currentPlayer = (currentPlayer == 'X') ? 'O' : 'X';
            broadcast("MOVE " + row + " " + col + " " + player);
            broadcast("TURN " + currentPlayer);
            checkGameStatus();
            return true;
        }
        return false;
    }

    private static List<int[]> checkWin(char player) {
        List<int[]> cells = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (board[i][0] == player && board[i][1] == player && board[i][2] == player) {
                Collections.addAll(cells, new int[]{i, 0}, new int[]{i, 1}, new int[]{i, 2});
                return cells;
            }
        }
        for (int j = 0; j < 3; j++) {
            if (board[0][j] == player && board[1][j] == player && board[2][j] == player) {
                Collections.addAll(cells, new int[]{0, j}, new int[]{1, j}, new int[]{2, j});
                return cells;
            }
        }
        if (board[0][0] == player && board[1][1] == player && board[2][2] == player) {
            Collections.addAll(cells, new int[]{0, 0}, new int[]{1, 1}, new int[]{2, 2});
            return cells;
        }
        if (board[0][2] == player && board[1][1] == player && board[2][0] == player) {
            Collections.addAll(cells, new int[]{0, 2}, new int[]{1, 1}, new int[]{2, 0});
            return cells;
        }
        return null;
    }

    private static void checkGameStatus() {
        List<int[]> winCells = checkWin('X');
        if (winCells != null) {
            playerXWins++;
            String msg = "WIN X " + playerXName;
            for (int[] cell : winCells) msg += " " + cell[0] + " " + cell[1];
            broadcast(msg);
            broadcast("STATS " + playerXWins + " " + playerOWins + " " + draws);
            return;
        }
        winCells = checkWin('O');
        if (winCells != null) {
            playerOWins++;
            String msg = "WIN O " + playerOName;
            for (int[] cell : winCells) msg += " " + cell[0] + " " + cell[1];
            broadcast(msg);
            broadcast("STATS " + playerXWins + " " + playerOWins + " " + draws);
            return;
        }
        if (isBoardFull()) {
            draws++;
            broadcast("DRAW");
            broadcast("STATS " + playerXWins + " " + playerOWins + " " + draws);
        }
    }

    private static boolean isBoardFull() {
        for (char[] row : board) {
            for (char cell : row) {
                if (cell == '\0') return false;
            }
        }
        return true;
    }

    public static synchronized void resetGame() {
        board = new char[3][3];
        currentPlayer = 'X';
        broadcast("RESET");
    }

    public static void setPlayerName(char player, String name) {
        if (player == 'X') playerXName = name;
        else if (player == 'O') playerOName = name;
    }

    public static void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }
}

class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private int playerId;
    private String playerName;

    public ClientHandler(Socket socket, int playerId) {
        this.socket = socket;
        this.playerId = playerId;
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            out.println("ASSIGN " + playerId);
            playerName = in.readLine();
            if (playerName == null || playerName.isEmpty()) playerName = "Player" + playerId;

            System.out.println("Player " + playerId + " name: " + playerName);
            Server.setPlayerName((playerId == 1) ? 'X' : 'O', playerName);

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                String[] tokens = inputLine.split(" ");
                if (tokens[0].equals("MOVE")) {
                    int row = Integer.parseInt(tokens[1]);
                    int col = Integer.parseInt(tokens[2]);
                    char player = tokens[3].charAt(0);
                    Server.makeMove(row, col, player, this);
                } else if (tokens[0].equals("RESET_REQUEST")) {
                    Server.resetGame();
                } else if (tokens[0].equals("QUIT")) {
                    Server.broadcast("QUIT " + playerName);
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Player " + playerId + " disconnected");
        }
    }

    public void sendMessage(String message) {
        if (out != null) out.println(message);
    }
}