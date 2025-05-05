import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
    static ReentrantLock lockId = new ReentrantLock(), lockGame = new ReentrantLock();
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
        lockGame.lock();
        int id = nextGameId++;
        lockGame.unlock();
        Game newGame = new Game(id);
        newGame.setSemaphore(sem);
        games.add(newGame);
    }

    public static void sendWelcomeMessage(DataOutputStream out) throws IOException {
        final String ANSI_CLEAR_SCREEN = "\033[H\033[2J";
        
        String mensagem = ANSI_CLEAR_SCREEN +
                "Bem-vindo ao Blackjack Online!\n" +
                "------------------------------------------------\n\n" +
                "Você está prestes a encarar o dealer em uma partida clássica de Blackjack.\n" +
                "O seu objetivo? Chegar o mais próximo de 21 pontos sem ultrapassar esse limite.\n\n" +
                "# Regras básicas:\n" +
                "- Ás vale 1 ou 11 pontos.\n" +
                "- Cartas com figura (J, Q, K) valem 10.\n" +
                "- Se passar de 21, você estoura e perde a rodada.\n\n" +
                "# Comandos disponíveis:\n" +
                "- hit   -> pedir outra carta\n" +
                "- stand -> parar de receber cartas\n\n" +
                "Boa sorte e que as cartas estejam a seu favor!\n";
    
        out.writeUTF(mensagem);
        out.flush();
    }

    public static String issueTicket(String nickname) {
        int playerId;
        lockId.lock();
        playerId = playerIdCounter++;
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

        gamesList.append("\nEscolha um ID de jogo ou digite 'criar' para criar um novo jogo.");
        return gamesList.toString();
    }

    public static boolean handleGameSelection(int gameSelection) {
        for (Game g : games) {
            if (g.getGameid() == gameSelection) {
                if (g.isGameInProgress()) return false;
                if (g.getNicknames().size() >= 6) return false;
                return true;
            }
        }
        return false;
    }

    public void run() {
        try {
            DataInputStream in = new DataInputStream(p.getInputStream());
            DataOutputStream out = new DataOutputStream(p.getOutputStream());

            sendWelcomeMessage(out);

            out.writeUTF("Digite o seu nome/apelido:");
            out.flush();
            String nickname = in.readUTF();
            System.out.println("\n\nNome recebido: " + nickname);

            out.writeUTF("O seu ID é: " + issueTicket(nickname));
            out.flush();

            // Aguarda 5 segundos
            try {
                out.writeUTF("\nCarregando leaderboard...");
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            final String ANSI_CLEAR_SCREEN = "\033[H\033[2J";
            out.writeUTF(ANSI_CLEAR_SCREEN);
            out.flush();

            out.writeUTF(displayLeaderboard());
            out.writeUTF(showGames());
            out.flush();

            int gameSelection = 0;

            try {
                String userGameSelection = in.readUTF();

                if (userGameSelection.equalsIgnoreCase("criar")) {
                    out.writeUTF("Digite o nome do jogo: ");
                    out.flush();
                    String chosenName = in.readUTF();
                    addNewGame();
                    Game g = games.get(games.size() - 1);
                    g.setGameName(chosenName);
                    g.addPlayer(p, nickname);
                    gameSelection = g.getGameid();
                } else {
                    // loop robusto de validação
                    while (true) {
                        try {
                            gameSelection = Integer.parseInt(userGameSelection);
                            if (!handleGameSelection(gameSelection)) {
                                out.writeUTF("\nID inválido ou mesa cheia. Tente novamente:");
                                out.flush();
                                userGameSelection = in.readUTF();
                                continue;
                            }
                            break;
                        } catch (NumberFormatException e) {
                            out.writeUTF("\nID inválido, digite um número:");
                            out.flush();
                            userGameSelection = in.readUTF();
                        }
                    }
                    games.get(gameSelection - 1).addPlayer(p, nickname);
                }
            } catch (IOException e) {
                out.writeUTF("Erro ao ler entrada.");
                p.close();
                return;
            }

            Game gChosen = games.get(gameSelection - 1);

            synchronized (gChosen.getJoinLock()) {
                while (gChosen.getNicknames().size() < 1) {
                    out.writeUTF("Esperando outros jogadores entrarem...");
                    out.flush();
                    gChosen.getJoinLock().wait();
                }
            }

            //out.writeUTF("\n\nSeja Bem-Vindo(a) à mesa de Blackjack!");
            //out.flush();

            while (true) {
                out.writeUTF("\nDigite 'pronto' quando estiver pronto para começar a jogar.");
                out.flush();
                String input = in.readUTF();
                if (input.equalsIgnoreCase("pronto")) {
                    gChosen.setUserInputReady();

                    out.writeUTF(ANSI_CLEAR_SCREEN);
                    out.flush();

                    out.writeUTF("Seja Bem-Vindo(a) à mesa de Blackjack!");
                    out.flush();
                    break;
                } else {
                    out.writeUTF("Resposta inválida. Digite 'pronto' para começar a jogar.");
                    out.flush();
                }
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            sem.release();
        }
    }
}
