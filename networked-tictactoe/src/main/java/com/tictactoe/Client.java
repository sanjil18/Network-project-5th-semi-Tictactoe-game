package com.tictactoe;

import javax.swing.*;
import javax.swing.Timer;
import java.util.List;
import java.util.ArrayList;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.sound.sampled.*;
import java.util.*;

public class Client extends JFrame {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private char player;
    private String playerName;
    private JButton[][] buttons = new JButton[3][3];
    private JLabel statusLabel;
    private JButton restartButton, quitButton;

    // Sound clips
    private Clip backgroundClip;
    private Clip moveClip;
    private Clip errorClip;
    private Clip winClip;
    private Clip loseClip;
    private Clip drawClip;

    // Statistics fields
    private int wins = 0;
    private int losses = 0;
    private int draws = 0;
    private JLabel statsLabel;

    public Client(String serverAddress) {
        try {
            socket = new Socket(serverAddress, 12345);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            playerName = JOptionPane.showInputDialog("Enter your name:");
            out.println(playerName);

            String response = in.readLine();
            if (response.startsWith("ASSIGN")) {
                player = (response.split(" ")[1].equals("1")) ? 'X' : 'O';
                setTitle("Tic Tac Toe - " + playerName + " (Player " + player + ")");
            }

            loadSounds();
            if (backgroundClip != null) {
                backgroundClip.loop(Clip.LOOP_CONTINUOUSLY);
            }

            initializeUI();
            new Thread(this::listenForUpdates).start();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to connect to the server.", "Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void loadSounds() {
        backgroundClip = loadClip("/sounds/background.wav");
        moveClip = loadClip("/sounds/move.wav");
        errorClip = loadClip("/sounds/error.wav");
        winClip = loadClip("/sounds/win.wav");
        loseClip = loadClip("/sounds/lose.wav");
        drawClip = loadClip("/sounds/draw.wav");

        // Log if any sound failed to load
        if (backgroundClip == null) System.err.println("Failed to load background sound.");
        if (moveClip == null) System.err.println("Failed to load move sound.");
        if (errorClip == null) System.err.println("Failed to load error sound.");
        if (winClip == null) System.err.println("Failed to load win sound.");
        if (loseClip == null) System.err.println("Failed to load lose sound.");
        if (drawClip == null) System.err.println("Failed to load draw sound.");
    }

    private Clip loadClip(String path) {
        try {
            InputStream audioStream = getClass().getResourceAsStream(path);
            if (audioStream == null) {
                System.err.println("Audio file not found: " + path);
                return null;
            }
            AudioInputStream audioIn = AudioSystem.getAudioInputStream(audioStream);
            Clip clip = AudioSystem.getClip();
            clip.open(audioIn);
            return clip;
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println("Error loading sound: " + path);
            e.printStackTrace();
            return null;
        }
    }

    private void playSound(Clip clip) {
        if (clip != null && clip != backgroundClip) {
            clip.setFramePosition(0);
            clip.start();
        }
    }

    private void initializeUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 450);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new BorderLayout());
        quitButton = new JButton("Quit");
        quitButton.addActionListener(e -> {
            out.println("QUIT");
            System.exit(0);
        });
        topPanel.add(quitButton, BorderLayout.EAST);

        statusLabel = new JLabel("Waiting for opponent...", SwingConstants.CENTER);
        topPanel.add(statusLabel, BorderLayout.CENTER);

        statsLabel = new JLabel("Wins: 0 | Losses: 0 | Draws: 0", SwingConstants.CENTER);
        topPanel.add(statsLabel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        JPanel boardPanel = new JPanel(new GridLayout(3, 3));
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                buttons[i][j] = new JButton();
                buttons[i][j].setFont(new Font("Arial", Font.BOLD, 40));
                buttons[i][j].setEnabled(false);
                buttons[i][j].setOpaque(true);
                buttons[i][j].setBackground(Color.WHITE);
                buttons[i][j].setBorder(BorderFactory.createLineBorder(Color.GRAY, 2));
                buttons[i][j].addActionListener(new ButtonClickListener(i, j));
                buttons[i][j].addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        if (((JButton) e.getSource()).isEnabled()) {
                            ((JButton) e.getSource()).setBackground(new Color(225, 225, 225));
                        }
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        if (((JButton) e.getSource()).isEnabled()) {
                            ((JButton) e.getSource()).setBackground(Color.WHITE);
                        }
                    }
                });
                boardPanel.add(buttons[i][j]);
            }
        }
        add(boardPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        restartButton = new JButton("Restart");
        restartButton.setEnabled(false);
        restartButton.addActionListener(e -> out.println("RESTART_REQUEST"));
        bottomPanel.add(restartButton);
        add(bottomPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void listenForUpdates() {
        try {
            String serverMessage;
            while ((serverMessage = in.readLine()) != null) {
                if (serverMessage.startsWith("START")) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Game started! Your turn: " + player);
                        enableBoard(true);
                    });
                } else if (serverMessage.startsWith("MOVE")) {
                    String[] tokens = serverMessage.split(" ");
                    int row = Integer.parseInt(tokens[1]);
                    int col = Integer.parseInt(tokens[2]);
                    char movePlayer = tokens[3].charAt(0);
                    SwingUtilities.invokeLater(() -> {
                        buttons[row][col].setText(String.valueOf(movePlayer));
                        buttons[row][col].setEnabled(false);
                        playSound(moveClip);
                    });
                } else if (serverMessage.startsWith("WIN")) {
                    String[] tokens = serverMessage.split(" ");
                    String winner = tokens[1];
                    String winnerName = tokens[2];
                    List<int[]> winCells = new ArrayList<>();
                    for (int i = 3; i < tokens.length; i += 2) {
                        int row = Integer.parseInt(tokens[i]);
                        int col = Integer.parseInt(tokens[i + 1]);
                        winCells.add(new int[]{row, col});
                    }
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText(winnerName + " (" + winner + ") wins!");
                        enableBoard(false);
                        restartButton.setEnabled(true);
                        updateStats(winner);
                        animateWinningCells(winCells);
                        if (winner.charAt(0) == player) playSound(winClip);
                        else playSound(loseClip);
                    });
                } else if (serverMessage.equals("DRAW")) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("It's a draw!");
                        enableBoard(false);
                        restartButton.setEnabled(true);
                        updateStats("DRAW");
                        playSound(drawClip);
                    });
                } else if (serverMessage.startsWith("RESTART_REQUEST")) {
                    String requester = serverMessage.split(" ")[1];
                    if (!requester.equals(playerName)) {
                        int response = JOptionPane.showConfirmDialog(this, requester + " wants to restart. Accept?", "Restart Request", JOptionPane.YES_NO_OPTION);
                        out.println("RESTART_CONFIRM " + (response == JOptionPane.YES_OPTION));
                    }
                } else if (serverMessage.equals("RESTART_CONFIRMED")) {
                    SwingUtilities.invokeLater(() -> {
                        resetBoard();
                        statusLabel.setText("Game restarted! Your turn: " + player);
                        enableBoard(true);
                        restartButton.setEnabled(false);
                    });
                } else if (serverMessage.startsWith("QUIT")) {
                    String quitterName = serverMessage.split(" ")[1];
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText(quitterName + " has quit the game. Waiting for opponent...");
                        enableBoard(false);
                        restartButton.setEnabled(false);
                    });
                } else if (serverMessage.startsWith("TURN")) {
                    char currentTurn = serverMessage.split(" ")[1].charAt(0);
                    SwingUtilities.invokeLater(() -> {
                        if (currentTurn == player) {
                            statusLabel.setText("Your turn!");
                            enableBoard(true);
                        } else {
                            statusLabel.setText("Opponent's turn...");
                            enableBoard(false);
                        }
                    });
                } else if (serverMessage.startsWith("STATS")) {
                    String[] tokens = serverMessage.split(" ");
                    int playerXWins = Integer.parseInt(tokens[1]);
                    int playerOWins = Integer.parseInt(tokens[2]);
                    int draws = Integer.parseInt(tokens[3]);
                    SwingUtilities.invokeLater(() -> {
                        if (player == 'X') {
                            wins = playerXWins;
                            losses = playerOWins;
                        } else {
                            wins = playerOWins;
                            losses = playerXWins;
                        }
                        this.draws = draws;
                        statsLabel.setText("Wins: " + wins + " | Losses: " + losses + " | Draws: " + draws);
                    });
                } else if (serverMessage.startsWith("WRONG_MOVE")) {
                    SwingUtilities.invokeLater(() -> playSound(errorClip));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void animateWinningCells(List<int[]> cells) {
        Color originalColor = buttons[0][0].getBackground();
        Timer timer = new Timer(500, new ActionListener() {
            boolean toggle = false;
            @Override
            public void actionPerformed(ActionEvent e) {
                for (int[] cell : cells) {
                    int row = cell[0];
                    int col = cell[1];
                    buttons[row][col].setBackground(toggle ? Color.GREEN : originalColor);
                }
                toggle = !toggle;
            }
        });
        timer.start();

        Timer stopTimer = new Timer(3000, e -> {
            timer.stop();
            for (int[] cell : cells) {
                int row = cell[0];
                int col = cell[1];
                buttons[row][col].setBackground(originalColor);
            }
        });
        stopTimer.setRepeats(false);
        stopTimer.start();
    }

    private void updateStats(String result) {
        if (result.equals("DRAW")) {
            draws++;
        } else if (result.equals(String.valueOf(player))) {
            wins++;
        } else {
            losses++;
        }
        statsLabel.setText("Wins: " + wins + " | Losses: " + losses + " | Draws: " + draws);
    }

    private void enableBoard(boolean enable) {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                buttons[i][j].setEnabled(enable && buttons[i][j].getText().isEmpty());
            }
        }
    }

    private void resetBoard() {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                buttons[i][j].setText("");
            }
        }
    }

    private class ButtonClickListener implements ActionListener {
        private int row, col;

        public ButtonClickListener(int row, int col) {
            this.row = row;
            this.col = col;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (buttons[row][col].getText().isEmpty()) {
                out.println("MOVE " + row + " " + col + " " + player);
                buttons[row][col].setEnabled(false);
                statusLabel.setText("Opponent's turn...");
            }
        }
    }

    public static void main(String[] args) {
        String serverAddress = JOptionPane.showInputDialog("Enter server IP:");
        new Client(serverAddress);
    }
}