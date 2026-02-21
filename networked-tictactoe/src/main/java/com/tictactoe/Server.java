package com.tictactoe;

import java.io.*;
import java.net.*;
import java.util.*;

public class Server {
    private static final int PORT = 12345;
    private static List<ClientHandler> clients = new ArrayList<>();
    private static char[][] board = new char[3][3];
    private static char currentPlayer = 'X';
    private static boolean gameStarted = false;
    private static String playerXName, playerOName;
    private static boolean restartRequested = false;
    private static String restartRequester;
    private static boolean secondPlayerConfirmed = false;

    // Statistics fields
    private static int playerXWins = 0;
    private static int playerOWins = 0;
    private static int draws = 0;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started. Waiting for players...");

            while (clients.size() < 2) {
                Socket socket = serverSocket.accept();
                System.out.println("Player connected: " + socket);
                ClientHandler clientHandler = new ClientHandler(socket, clients.size() + 1);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }

            System.out.println("Both players connected. Game can start.");
            broadcast("START");
            gameStarted = true;
        } catch (IOException e) {
            e.printStackTrace();
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
        } else {
            notifyWrongMove(client);
            return false;
        }
    }

    private static void notifyWrongMove(ClientHandler client) {
        client.sendMessage("WRONG_MOVE");
    }

    private static List<int[]> checkWin(char player) {
        List<int[]> cells = new ArrayList<>();
        // Check rows
        for (int i = 0; i < 3; i++) {
            if (board[i][0] == player && board[i][1] == player && board[i][2] == player) {
                Collections.addAll(cells, new int[]{i, 0}, new int[]{i, 1}, new int[]{i, 2});
                return cells;
            }
        }
        // Check columns
        for (int j = 0; j < 3; j++) {
            if (board[0][j] == player && board[1][j] == player && board[2][j] == player) {
                Collections.addAll(cells, new int[]{0, j}, new int[]{1, j}, new int[]{2, j});
                return cells;
            }
        }
        // Check diagonals
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
            String winMsg = "WIN X " + playerXName;
            for (int[] cell : winCells) winMsg += " " + cell[0] + " " + cell[1];
            broadcast(winMsg);
            broadcastStats();
        } else {
            winCells = checkWin('O');
            if (winCells != null) {
                playerOWins++;
                String winMsg = "WIN O " + playerOName;
                for (int[] cell : winCells) winMsg += " " + cell[0] + " " + cell[1];
                broadcast(winMsg);
                broadcastStats();
            } else if (isBoardFull()) {
                draws++;
                broadcast("DRAW");
                broadcastStats();
            }
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

    public static void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    public static synchronized void resetGame() {
        board = new char[3][3];
        currentPlayer = 'X';
        broadcast("RESET");
        restartRequested = false;
        restartRequester = null;
        secondPlayerConfirmed = false;
    }

    public static void setPlayerName(char player, String name) {
        if (player == 'X') {
            playerXName = name;
        } else if (player == 'O') {
            playerOName = name;
        }
    }

    public static synchronized void requestRestart(String playerName) {
        if (!restartRequested) {
            restartRequested = true;
            restartRequester = playerName;
            broadcast("RESTART_REQUEST " + playerName);
        }
    }

    public static synchronized void confirmRestart(boolean confirm, String playerName) {
        if (restartRequested) {
            if (confirm) {
                if (!playerName.equals(restartRequester)) {
                    secondPlayerConfirmed = true;
                }
                if (secondPlayerConfirmed) {
                    broadcast("RESTART_CONFIRMED");
                    resetGame();
                }
            } else {
                broadcast("RESTART_DECLINED " + playerName);
                restartRequested = false;
                restartRequester = null;
                secondPlayerConfirmed = false;
            }
        }
    }

    private static void broadcastStats() {
        broadcast("STATS " + playerXWins + " " + playerOWins + " " + draws);
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
                } else if (tokens[0].equals("RESTART_REQUEST")) {
                    Server.requestRestart(playerName);
                } else if (tokens[0].equals("RESTART_CONFIRM")) {
                    boolean confirm = Boolean.parseBoolean(tokens[1]);
                    Server.confirmRestart(confirm, playerName);
                } else if (tokens[0].equals("QUIT")) {
                    Server.broadcast("QUIT " + playerName);
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }
}