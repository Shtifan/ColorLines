import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.io.*;

public class GameBoard {
    private static final int SIZE = 9;
    private static final int CONNECT_COUNT = 5;
    private static final String SAVE_FILE = System.getProperty("user.dir") + File.separator + "game_save.dat";

    private BallButton[][] board;
    private int selectedRow = -1, selectedCol = -1;
    private ColorManager colorManager;
    private ScoreManager scoreManager;
    private JPanel gamePanel;
    private Random random;
    private Color[] tileColors;

    public GameBoard(ScoreManager scoreManager) {
        this.scoreManager = scoreManager;
        board = new BallButton[SIZE][SIZE];
        random = new Random();
        colorManager = new ColorManager();
        tileColors = colorManager.getTileColors();
        initializeBoard();
    }

    public BallButton[][] getBoard() {
        return board;
    }

    public int getSelectedRow() {
        return selectedRow;
    }

    public int getSelectedCol() {
        return selectedCol;
    }

    public void setNextColorsPanel(NextColorsPanel panel) {
        colorManager.setNextColorsPanel(panel);
    }

    public ColorManager getColorManager() {
        return colorManager;
    }

    private void initializeBoard() {
        gamePanel = new JPanel(new GridLayout(SIZE, SIZE));

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                BallButton cell = new BallButton();
                cell.addActionListener(new CellClickListener(row, col));
                board[row][col] = cell;
                gamePanel.add(cell);
            }
        }
    }

    public JPanel getPanel() {
        return gamePanel;
    }

    public void tryMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (!isValidMove(fromRow, fromCol, toRow, toCol)) return;
        board[toRow][toCol].setBallColor(board[fromRow][fromCol].getBallColor());
        board[fromRow][fromCol].setBallColor(null);
        board[fromRow][fromCol].setSelected(false);

        selectedRow = -1;
        selectedCol = -1;
        gamePanel.revalidate();
        gamePanel.repaint();

        boolean cleared = checkForConnects();
        if (cleared) {
            colorManager.generateNextColors();
            updateUI();
            saveState();
            return;
        }
        if (!addNextBallsAndCheck()) {
            showGameOver("Game Over! No more moves available.");
        }
        updateUI();
        saveState();
    }

    private boolean addNextBallsAndCheck() {
        int emptySpaces = 0;
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col].getBallColor() == null) emptySpaces++;
            }
        }
        Color[] nextColors = colorManager.getNextColors();
        if (emptySpaces < nextColors.length) {
            return false;
        }
        for (Color color : nextColors) {
            int row, col;
            do {
                row = random.nextInt(SIZE);
                col = random.nextInt(SIZE);
            } while (board[row][col].getBallColor() != null);
            board[row][col].setBallColor(color);
        }
        colorManager.generateNextColors();
        checkForConnects();
        return !isBoardFull();
    }

    private void updateUI() {
        gamePanel.revalidate();
        gamePanel.repaint();
    }

    private boolean isValidMove(int fromRow, int fromCol, int toRow, int toCol) {
        return board[toRow][toCol].getBallColor() == null && hasPath(fromRow, fromCol, toRow, toCol);
    }

    private boolean hasPath(int fromRow, int fromCol, int toRow, int toCol) {
        boolean[][] visited = new boolean[SIZE][SIZE];
        Queue<int[]> queue = new LinkedList<>();
        queue.add(new int[]{fromRow, fromCol});
        visited[fromRow][fromCol] = true;

        int[] rowDir = {-1, 1, 0, 0};
        int[] colDir = {0, 0, -1, 1};

        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int currRow = current[0];
            int currCol = current[1];

            if (currRow == toRow && currCol == toCol) {
                return true;
            }

            for (int i = 0; i < 4; i++) {
                int newRow = currRow + rowDir[i];
                int newCol = currCol + colDir[i];

                if (isValidCell(newRow, newCol) && !visited[newRow][newCol] && board[newRow][newCol].getBallColor() == null) {
                    visited[newRow][newCol] = true;
                    queue.add(new int[]{newRow, newCol});
                }
            }
        }

        return false;
    }

    private boolean isValidCell(int row, int col) {
        return row >= 0 && row < SIZE && col >= 0 && col < SIZE;
    }

    private boolean checkForConnects() {
        boolean[][] toClear = new boolean[SIZE][SIZE];

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col].getBallColor() != null) {
                    Color color = board[row][col].getBallColor();

                    if (col <= SIZE - CONNECT_COUNT && checkDirection(row, col, 0, 1, color)) {
                        markForClear(row, col, 0, 1, toClear);
                    }

                    if (row <= SIZE - CONNECT_COUNT && checkDirection(row, col, 1, 0, color)) {
                        markForClear(row, col, 1, 0, toClear);
                    }

                    if (row <= SIZE - CONNECT_COUNT && col <= SIZE - CONNECT_COUNT && checkDirection(row, col, 1, 1, color)) {
                        markForClear(row, col, 1, 1, toClear);
                    }

                    if (row >= CONNECT_COUNT - 1 && col <= SIZE - CONNECT_COUNT && checkDirection(row, col, -1, 1, color)) {
                        markForClear(row, col, -1, 1, toClear);
                    }
                }
            }
        }

        boolean[][] crossPattern = detectCrossPatterns(toClear);

        return clearMarkedCells(toClear, crossPattern);
    }

    private boolean[][] detectCrossPatterns(boolean[][] toClear) {
        boolean[][] crossPattern = new boolean[SIZE][SIZE];

        for (int row = 1; row < SIZE - 1; row++) {
            for (int col = 1; col < SIZE - 1; col++) {
                if (toClear[row][col] && toClear[row - 1][col] && toClear[row + 1][col] && toClear[row][col - 1] && toClear[row][col + 1]) {

                    crossPattern[row][col] = true;
                    crossPattern[row - 1][col] = true;
                    crossPattern[row + 1][col] = true;
                    crossPattern[row][col - 1] = true;
                    crossPattern[row][col + 1] = true;
                }
            }
        }
        return crossPattern;
    }

    private boolean checkDirection(int row, int col, int dRow, int dCol, Color color) {
        for (int i = 0; i < CONNECT_COUNT; i++) {
            if (board[row + i * dRow][col + i * dCol].getBallColor() != color) {
                return false;
            }
        }
        return true;
    }

    private void markForClear(int row, int col, int dRow, int dCol, boolean[][] toClear) {
        for (int i = 0; i < CONNECT_COUNT; i++) {
            toClear[row + i * dRow][col + i * dCol] = true;
        }
    }

    private boolean clearMarkedCells(boolean[][] toClear, boolean[][] crossPattern) {
        int cleared = 0;
        int crossBonus = 0;

        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (toClear[row][col]) {
                    board[row][col].setBallColor(null);
                    cleared++;
                    if (crossPattern[row][col]) {
                        crossBonus++;
                    }
                }
            }
        }

        if (cleared > 0) {
            int regularScore = cleared * 2;
            int bonusScore = crossBonus * 8;
            scoreManager.addScore(regularScore + bonusScore);
            scoreManager.updateHighScore();
            return true;
        }
        return false;
    }

    private boolean isBoardFull() {
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                if (board[row][col].getBallColor() == null) {
                    return false;
                }
            }
        }
        return true;
    }

    private class CellClickListener implements ActionListener {
        private final int row;
        private final int col;

        public CellClickListener(int row, int col) {
            this.row = row;
            this.col = col;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (selectedRow != -1 && selectedCol != -1) {
                board[selectedRow][selectedCol].setSelected(false);
            }
            Color ballColor = board[row][col].getBallColor();
            if (ballColor != null) {
                selectedRow = row;
                selectedCol = col;
                board[row][col].setSelected(true);
            } else if (selectedRow != -1 && selectedCol != -1) {
                tryMove(selectedRow, selectedCol, row, col);
            }
        }
    }

    public void startNewGame() {
        scoreManager.resetScore();
        selectedRow = -1;
        selectedCol = -1;
        for (int row = 0; row < SIZE; row++) {
            for (int col = 0; col < SIZE; col++) {
                board[row][col].setBallColor(null);
                board[row][col].setSelected(false);
            }
        }
        colorManager.generateNextColors();
        addNextBallsAndCheck();
        updateUI();
        saveState();
    }

    public boolean loadState() {
        File file = new File(SAVE_FILE);
        if (!file.exists()) return false;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            GameState state = (GameState) in.readObject();
            for (int row = 0; row < SIZE; row++) {
                for (int col = 0; col < SIZE; col++) {
                    int idx = state.boardColors[row][col];
                    board[row][col].setBallColor(indexToColor(idx));
                    board[row][col].setSelected(false);
                }
            }
            Color[] next = new Color[state.nextColors.length];
            for (int i = 0; i < next.length; i++) {
                next[i] = indexToColor(state.nextColors[i]);
            }
            colorManager.setNextColors(next);
            scoreManager.setScore(state.score);
            scoreManager.setHighScore(state.highScore);
            selectedRow = -1;
            selectedCol = -1;
            updateUI();
            return true;
        } catch (Exception e) {
            System.err.println("Failed to load game state: " + e.getMessage());
            return false;
        }
    }

    private void showGameOver(String message) {
        int choice = JOptionPane.showOptionDialog(null, message + "\nFinal Score: " + scoreManager.getScore(), "Game Over", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null, new String[]{"Play Again", "Exit"}, "Play Again");

        if (choice == JOptionPane.YES_OPTION) {
            startNewGame();
        } else {
            System.exit(0);
        }
    }

    public void saveState() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(SAVE_FILE))) {
            GameState state = new GameState();
            for (int row = 0; row < SIZE; row++) {
                for (int col = 0; col < SIZE; col++) {
                    Color c = board[row][col].getBallColor();
                    state.boardColors[row][col] = colorToIndex(c);
                }
            }
            Color[] next = colorManager.getNextColors();
            for (int i = 0; i < next.length; i++) {
                state.nextColors[i] = colorToIndex(next[i]);
            }
            state.score = scoreManager.getScore();
            state.highScore = scoreManager.getHighScore();
            out.writeObject(state);
        } catch (Exception e) {
            System.err.println("Failed to save game state: " + e.getMessage());
        }
    }

    private int colorToIndex(Color c) {
        if (c == null) return -1;
        for (int i = 0; i < tileColors.length; i++) {
            if (tileColors[i].equals(c)) return i;
        }
        return -1;
    }

    private Color indexToColor(int idx) {
        if (idx < 0 || idx >= tileColors.length) return null;
        return tileColors[idx];
    }
}