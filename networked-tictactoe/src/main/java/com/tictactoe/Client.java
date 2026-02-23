
package com.tictactoe;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.sound.sampled.*;

public class Client extends JFrame {
    private JButton[][] buttons = new JButton[3][3];
    private JLabel statusLabel;
    private JLabel titleLabel;
    private JLabel statsLabel;
    private JButton restartButton;
    private JButton quitButton;
    private JPanel boardPanel;

    private char playerSymbol;
    private String playerName;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private int myWins = 0;
    private int myLosses = 0;
    private int draws = 0;

    private Clip backgroundClip, moveClip, errorClip, winClip, loseClip, drawClip;

    private final Color BG_COLOR = new Color(34, 40, 49);
    private final Color BUTTON_COLOR = new Color(57, 62, 70);
    private final Color BUTTON_HOVER = new Color(0, 173, 181);
    private final Color TEXT_COLOR = new Color(238, 238, 238);
    private final Color X_COLOR = new Color(255, 107, 107);
    private final Color O_COLOR = new Color(78, 205, 196);

    public Client(String serverAddress) {
        setTitle("Tic-Tac-Toe");
        setSize(500, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(BG_COLOR);
        setResizable(false);

        loadSounds();

        try {
            socket = new Socket(serverAddress, 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String message = in.readLine();
            if (message.startsWith("ASSIGN")) {
                int playerId = Integer.parseInt(message.split(" ")[1]);
                playerSymbol = (playerId == 1) ? 'X' : 'O';

                playerName = JOptionPane.showInputDialog(this, "Enter your name:");
                if (playerName == null || playerName.trim().isEmpty()) {
                    playerName = "Player " + playerId;
                }
                out.println(playerName);
            }

            initializeUI();
            new Thread(this::listenForUpdates).start();
            setLocationRelativeTo(null);
            setVisible(true);

            if (backgroundClip != null) {
                backgroundClip.loop(Clip.LOOP_CONTINUOUSLY);
                backgroundClip.start();
            }

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Cannot connect to server!",
                    "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void initializeUI() {
        JPanel titlePanel = new JPanel();
        titlePanel.setBackground(BG_COLOR);
        titlePanel.setBorder(new EmptyBorder(10, 10, 5, 10));

        titleLabel = new JLabel("TIC-TAC-TOE", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 32));
        titleLabel.setForeground(TEXT_COLOR);
        titlePanel.add(titleLabel);

        statusLabel = new JLabel("You are: " + playerSymbol, SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 18));
        statusLabel.setForeground(playerSymbol == 'X' ? X_COLOR : O_COLOR);
        titlePanel.add(statusLabel);

        add(titlePanel, BorderLayout.NORTH);

        boardPanel = new JPanel(new GridLayout(3, 3, 8, 8));
        boardPanel.setBackground(BG_COLOR);
        boardPanel.setBorder(new EmptyBorder(10, 30, 10, 30));

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                buttons[i][j] = new JButton("");
                buttons[i][j].setFont(new Font("Arial", Font.BOLD, 60));
                buttons[i][j].setFocusPainted(false);
                buttons[i][j].setBackground(BUTTON_COLOR);
                buttons[i][j].setForeground(TEXT_COLOR);
                buttons[i][j].setBorder(BorderFactory.createLineBorder(BG_COLOR, 2));

                final int row = i, col = j;
                buttons[i][j].addActionListener(e -> handleButtonClick(row, col));

                buttons[i][j].addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) {
                        if (buttons[row][col].getText().isEmpty()) {
                            buttons[row][col].setBackground(BUTTON_HOVER);
                        }
                    }
                    public void mouseExited(MouseEvent e) {
                        if (buttons[row][col].getText().isEmpty()) {
                            buttons[row][col].setBackground(BUTTON_COLOR);
                        }
                    }
                });

                boardPanel.add(buttons[i][j]);
            }
        }
        add(boardPanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        controlPanel.setBackground(BG_COLOR);

        restartButton = new JButton("New Game");
        restartButton.setFont(new Font("Arial", Font.BOLD, 16));
        restartButton.setBackground(new Color(78, 205, 196));
        restartButton.setForeground(Color.BLACK);
        restartButton.setFocusPainted(false);
        restartButton.setBorder(new EmptyBorder(10, 25, 10, 25));
        restartButton.addActionListener(e -> requestRestart());
        restartButton.setEnabled(false);

        quitButton = new JButton("Quit");
        quitButton.setFont(new Font("Arial", Font.BOLD, 16));
        quitButton.setBackground(new Color(255, 107, 107));
        quitButton.setForeground(Color.BLACK);
        quitButton.setFocusPainted(false);
        quitButton.setBorder(new EmptyBorder(10, 25, 10, 25));
        quitButton.addActionListener(e -> quit());

        controlPanel.add(restartButton);
        controlPanel.add(quitButton);

        JPanel statsPanel = new JPanel();
        statsPanel.setBackground(BG_COLOR);
        statsLabel = new JLabel("Wins: 0 | Losses: 0 | Draws: 0");
        statsLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        statsLabel.setForeground(TEXT_COLOR);
        statsPanel.add(statsLabel);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(BG_COLOR);
        bottomPanel.add(controlPanel, BorderLayout.CENTER);
        bottomPanel.add(statsPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void handleButtonClick(int row, int col) {
        if (buttons[row][col].getText().isEmpty()) {
            out.println("MOVE " + row + " " + col + " " + playerSymbol);
        }
    }

    private void listenForUpdates() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                final String msg = message;
                SwingUtilities.invokeLater(() -> processMessage(msg));
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this, "Connection lost!",
                        "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            });
        }
    }

    private void processMessage(String message) {
        String[] parts = message.split(" ");

        if (parts[0].equals("START")) {
            statusLabel.setText("Game started! Waiting...");
        } else if (parts[0].equals("MOVE")) {
            int row = Integer.parseInt(parts[1]);
            int col = Integer.parseInt(parts[2]);
            char player = parts[3].charAt(0);
            buttons[row][col].setText(String.valueOf(player));
            buttons[row][col].setForeground(player == 'X' ? X_COLOR : O_COLOR);
            playSound(moveClip);
        } else if (parts[0].equals("TURN")) {
            char turn = parts[1].charAt(0);
            if (turn == playerSymbol) {
                statusLabel.setText("Your turn!");
                enableBoard(true);
            } else {
                statusLabel.setText("Opponent's turn...");
                enableBoard(false);
            }
        } else if (parts[0].equals("WIN")) {
            handleWin(parts);
        } else if (parts[0].equals("DRAW")) {
            statusLabel.setText("Draw! 🤝");
            enableBoard(false);
            restartButton.setEnabled(true);
            playSound(drawClip);
        } else if (parts[0].equals("RESET")) {
            resetBoard();
        } else if (parts[0].equals("STATS")) {
            updateStats(message);
        } else if (parts[0].equals("WRONG_MOVE")) {
            playSound(errorClip);
            JOptionPane.showMessageDialog(this, "Invalid move!",
                    "Error", JOptionPane.WARNING_MESSAGE);
        } else if (parts[0].equals("RESTART_REQUEST")) {
            handleRestartRequest(parts[1]);
        } else if (parts[0].equals("RESTART_CONFIRMED")) {
            statusLabel.setText("Restarting...");
        } else if (parts[0].equals("RESTART_DECLINED")) {
            JOptionPane.showMessageDialog(this, parts[1] + " declined restart.",
                    "Declined", JOptionPane.INFORMATION_MESSAGE);
        } else if (parts[0].equals("QUIT")) {
            JOptionPane.showMessageDialog(this, parts[1] + " quit!",
                    "Game Over", JOptionPane.INFORMATION_MESSAGE);
            System.exit(0);
        }
    }

    private void handleWin(String[] parts) {
        char winner = parts[1].charAt(0);
        enableBoard(false);

        if (winner == playerSymbol) {
            statusLabel.setText("You WIN! 🎉");
            playSound(winClip);
        } else {
            statusLabel.setText("You LOSE! 😢");
            playSound(loseClip);
        }

        for (int i = 3; i < parts.length; i += 2) {
            int row = Integer.parseInt(parts[i]);
            int col = Integer.parseInt(parts[i + 1]);
            highlightWinningCell(buttons[row][col]);
        }

        restartButton.setEnabled(true);
    }

    private void highlightWinningCell(JButton button) {
        Timer timer = new Timer(300, new ActionListener() {
            boolean toggle = false;
            public void actionPerformed(ActionEvent e) {
                button.setBackground(toggle ? Color.YELLOW : BUTTON_COLOR);
                toggle = !toggle;
            }
        });
        timer.start();
    }

    private void enableBoard(boolean enable) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                buttons[i][j].setEnabled(enable);
            }
        }
    }

    private void resetBoard() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                buttons[i][j].setText("");
                buttons[i][j].setBackground(BUTTON_COLOR);
                buttons[i][j].setEnabled(true);
            }
        }
        statusLabel.setText("New game!");
        restartButton.setEnabled(false);
    }

    private void updateStats(String message) {
        String[] parts = message.split(" ");
        int xWins = Integer.parseInt(parts[1]);
        int oWins = Integer.parseInt(parts[2]);
        draws = Integer.parseInt(parts[3]);

        if (playerSymbol == 'X') {
            myWins = xWins;
            myLosses = oWins;
        } else {
            myWins = oWins;
            myLosses = xWins;
        }

        statsLabel.setText("Wins: " + myWins + " | Losses: " + myLosses + " | Draws: " + draws);
    }

    private void requestRestart() {
        out.println("RESTART_REQUEST");
    }

    private void handleRestartRequest(String requester) {
        if (!requester.equals(playerName)) {
            int choice = JOptionPane.showConfirmDialog(this,
                    requester + " wants to restart. Agree?",
                    "Restart?", JOptionPane.YES_NO_OPTION);
            out.println("RESTART_CONFIRM " + (choice == JOptionPane.YES_OPTION));
        }
    }

    private void quit() {
        out.println("QUIT");
        System.exit(0);
    }

    private void loadSounds() {
        backgroundClip = loadSound("/com/resources/sounds/background.wav");
        moveClip = loadSound("/com/resources/sounds/move.wav");
        errorClip = loadSound("/com/resources/sounds/error.wav");
        winClip = loadSound("/com/resources/sounds/win.wav");
        loseClip = loadSound("/com/resources/sounds/lose.wav");
        drawClip = loadSound("/com/resources/sounds/draw.wav");
    }

    private Clip loadSound(String path) {
        try {
            InputStream audioSrc = getClass().getResourceAsStream(path);
            if (audioSrc == null) return null;
            InputStream bufferedIn = new BufferedInputStream(audioSrc);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedIn);
            Clip clip = AudioSystem.getClip();
            clip.open(audioStream);
            return clip;
        } catch (Exception e) {
            System.err.println("Cannot load sound: " + path);
            return null;
        }
    }

    private void playSound(Clip clip) {
        if (clip != null) {
            clip.setFramePosition(0);
            clip.start();
        }
    }

    public static void main(String[] args) {
        String serverAddress = JOptionPane.showInputDialog("Enter server IP:");
        if (serverAddress == null || serverAddress.trim().isEmpty()) {
            serverAddress = "localhost";
        }
        new Client(serverAddress);
    }
}
