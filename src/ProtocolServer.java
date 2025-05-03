import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

public class ProtocolServer extends Thread {
    private Socket p;
    static Semaphore sem = new Semaphore(6);
    static ArrayList<Socket> waitingPlayers = new ArrayList<>();
    static ArrayList<String> nicknames = new ArrayList<>();
    static ArrayList<Game> games = new ArrayList<>();
    static ArrayList<Player> leaderboard = new ArrayList<>();
    static ReentrantLock lockId = new ReentrantLock();
    static int playerIdCounter = 1;
    private static int nextGameId = 1;

    public ProtocolServer(Socket p) {
        this.p = p;
    }

    public static synchronized void updateLeaderboard(Player winner) {
        leaderboard.removeIf(player -> player.getNickname().equals(winner.getNickname()));
        leaderboard.add(winner);
        leaderboard.sort((p1, p2) -> p2.getChips() - p1.getChips());
        
        while (leaderboard.size() > 5) {
            leaderboard.remove(5);
        }
    }

    public static String displayLeaderboard() {
        StringBuilder leaderboardString = new StringBuilder("Leaderboard (Top 5 por fichas):\n");
        for (Player player : leaderboard) {
            leaderboardString.append(player.getNickname())
                           .append(": ")
                           .append(player.getChips())
                           .append(" fichas\n");
        }
        return leaderboardString.toString();
    }

    public static void addNewGame() {
        Game newGame = new Game(nextGameId++);
        newGame.setSemaphore(sem);
        games.add(newGame);
    }
    
    public static void sendWelcomeMessage(PrintWriter out) {
        String mensagem = "Bem-vindo ao Blackjack Online!\n" +
                         "-----------------------------------------\n" +
                         "Jogue contra o dealer e outros jogadores. O objetivo é chegar o mais próximo de 21 sem estourar.\n" +
                         "Comandos: hit (pedir carta), stand (parar)\n";
        out.println(mensagem);
    }

    public static String issueTicket(String nickname) {
        int playerId;
        lockId.lock();
        playerId = playerIdCounter;
        playerIdCounter++;
        lockId.unlock();
        return nickname + playerId;
    }

    public static String showGames() {
        StringBuilder gamesList = new StringBuilder();
        boolean allFinished = true;

        for (Game g : games) {
            if (!g.getGameFinishs()) {
                allFinished = false;
                break;
            }
        }

        if (games.isEmpty() || allFinished) {
            System.out.println("Não há jogos disponíveis. Um novo jogo será criado automaticamente.");
            addNewGame();
        }

        gamesList.append(String.format("%-10s %-20s %-10s %-15s\n", "MESA ID", "NOME DA MESA", "STATUS", "JOGADORES"));
        gamesList.append("----------------------------------------------------------\n");

        for (Game g : games) {
            if (g.getPlayers().size() < 6 && !g.getGameFinishs()) {
                String status = g.isGameInProgress() ? "Em Jogo" : "Aguardando";
                gamesList.append(String.format("%-10d %-20s %-10s %-15s\n",
                    g.getGameid(),
                    g.getGameName().isEmpty() ? "Mesa " + g.getGameid() : g.getGameName(),
                    status,
                    g.getNicknames().toString()
                ));
            }
        }

        gamesList.append("Escolha um ID de jogo ou digite 'criar' para criar um novo jogo.");
        return gamesList.toString();
    }

    public static boolean handleGameSelection(int gameSelection) {
        Game gameSelected = null;
        for (Game g : games) {
            if (g.getGameid() == (gameSelection)) {
                gameSelected = g;
                break;
            }
        }

        if (gameSelected == null) {
            System.out.println("ID de jogo inválido.");
            return false;
        }

        if (gameSelected.isGameInProgress()) {
            System.out.println("Esse jogo já começou.");
            return false;
        }

        if (gameSelected.getNicknames().size() == 6) {
            System.out.println("Esse jogo já atingiu a sua capacidade máxima (6 jogadores).");
            return false;
        }

        return true;
    }

    public void run() {
        try {
            PrintWriter out = new PrintWriter(p.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            
            sendWelcomeMessage(out);
            out.println("Digite o seu nome/apelido:");
            String nickname = in.readLine();
            System.out.println("Nome recebido: " + nickname);
            
            out.println("O seu ID é: "+issueTicket(nickname));
            out.println(displayLeaderboard());
            out.println(showGames());
            
            int gameSelection = 0;
            
            try {
                String userGameSelection = in.readLine();
                if (userGameSelection.equalsIgnoreCase("criar")) {
                    out.println("Digite o nome do jogo: ");
                    String chosenName = in.readLine();
                    addNewGame();
                    games.get(games.size()-1).setGameName(chosenName);
                    games.get(games.size()-1).addPlayer(p, nickname);
                    gameSelection = games.get(games.size()-1).getGameid();
                }
                else {
                    boolean wrong = false;
                    do {
                        if(wrong) {
                            out.println("Digite um número de ID válido.");
                            userGameSelection = in.readLine();
                        }
                        gameSelection = Integer.parseInt(userGameSelection);
                        wrong = true;
                    } while (!handleGameSelection(gameSelection));
                    games.get(gameSelection-1).addPlayer(p, nickname);
                }
            } catch (NumberFormatException e) {
                out.println("ID inválido, tem que ser um número.");
                p.close();
            }
            
            Game gChosen = games.get(gameSelection-1);
            if (gChosen.getNicknames().size() < 1) {
                out.println("Esperando outros jogadores entrarem...");
            }
            
            while(gChosen.getNicknames().size() < 1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            
            out.println("Seja Bem-Vindo(a) à mesa de Blackjack!");
            String input = "";
            while(true) {
                out.println("Digite 'pronto' quando estiver pronto para começar a jogar.");
                input = in.readLine();
                if (input.equalsIgnoreCase("pronto")) {
                    games.get(gameSelection-1).setUserInputReady();
                    break;
                } else {
                    out.println("Resposta inválida. Digite 'pronto' para começar a jogar.");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            sem.release();
        }
    }
}