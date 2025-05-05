// Game.java
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;

public class Game extends Thread {
    private ArrayList<Player> players = new ArrayList<>();
    private int gameid = 1;
    private int userInputReady = 0;
    private String gameName = "";
    private Player dealer = new Player(null, "Dealer", true, null);
    private boolean isOver = false;
    private Semaphore sem;
    private boolean gameInProgress = false;
    private Deck deck = new Deck();
    private int minBet = 10;
    private int maxBet = 500;
    private final int TIMEOUT = 50000;
    private final Object joinLock = new Object();

    public Game(int id) {
        this.gameid = id;
    }

    public ArrayList<Player> getPlayers() { return players; }
    public boolean getGameFinishs() { return this.isOver; }
    public void setGameName(String name) { this.gameName = name; }
    public String getGameName() { return this.gameName; }
    public int getGameid() { return gameid; }
    public boolean isGameInProgress() { return gameInProgress; }
    public int getTimeout() { return TIMEOUT; }
    public void setSemaphore(Semaphore semaphore) { sem = semaphore; }
    public Object getJoinLock() { return joinLock; }

    public synchronized void setUserInputReady() {
        this.userInputReady++;
        checkToStart();
    }

    public void run() {
        try {
            gameInProgress = true;
            notifyPlayers("Bem-vindo à mesa de Blackjack " + gameName + "!");
            while (!isOver) {
                playRound();
                Thread.sleep(2000);
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("IO exception no jogo: " + e.getMessage());
        } finally {
            cleanUpConnections();
            if (sem != null) sem.release(players.size());
        }
    }

    private void playRound() throws IOException {
        if (getActivePlayers().size() < 2) {
            isOver = true;
            return;
        }

        dealer.clearHand();
        for (Player p : players) if (p.isActive()) p.clearHand();
        collectBets();
        dealInitialCards();

        for (Player player : players) {
            if (player.isActive() && player.getCurrentBet() > 0) playerTurn(player);
        }

        dealerTurn();
        determineResults();

        try {
            Thread.sleep(5000); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        checkPlayersCanContinue();

        for (Player player : players) {
            if (!player.isBot() && !player.isActive()) {
                long outrosAtivos = players.stream()
                    .filter(p -> p != player && p.isActive())
                    .count();
        
                if (outrosAtivos > 0) {
                    try {
                        DataOutputStream out = new DataOutputStream(player.getSocket().getOutputStream());
                        DataInputStream in = new DataInputStream(player.getSocket().getInputStream());
        
                        out.writeUTF("Você está fora da partida (sem fichas). Deseja continuar assistindo as próximas rodadas? (sim/nao)");
                        out.flush();
        
                        String resposta = in.readUTF();
                        if (resposta.equalsIgnoreCase("nao")) {
                            out.writeUTF("Você saiu da partida.");
                            out.flush();
                            player.closeSocket();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        player.closeSocket();
                    }
                }
            }
        }
    }

    private void collectBets() throws IOException {
        for (Player player : players) {
            if (player.isActive()) {
                if (player.isBot()) {
                    int bet = new Random().nextInt(maxBet - minBet + 1) + minBet;
                    player.placeBet(Math.min(bet, player.getChips()));
                    notifyPlayers(player.getNickname() + " apostou " + player.getCurrentBet());
                } else {
                    DataOutputStream out = new DataOutputStream(player.getSocket().getOutputStream());
                    DataInputStream in = new DataInputStream(player.getSocket().getInputStream());

                    boolean validBet = false;
                    String erroMensagem = "";

                    while (!validBet && player.isActive()) {
                    
                        if (!erroMensagem.isEmpty()) {
                            out.writeUTF(erroMensagem); // mostra o erro
                            out.flush();
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt(); 
                            }
                            erroMensagem = ""; // limpa pra próxima iteração
                        }

                        final String ANSI_CLEAR_SCREEN = "\033[H\033[2J";
                        String message = ANSI_CLEAR_SCREEN + "============== Apostas ==============";
                        out.writeUTF(message);

                        out.writeUTF("Você tem " + player.getChips() + " fichas. Quanto deseja apostar? (min " + minBet + ", max " + maxBet + ")");

                        try {
                            player.getSocket().setSoTimeout(TIMEOUT);
                            String betStr = in.readUTF();

                            if (betStr == null) {
                                player.setActive(false);
                                break;
                            }

                            try {
                                int bet = Integer.parseInt(betStr.trim());

                                if (bet < minBet) erroMensagem = "Aposta muito baixa. O mínimo é " + minBet + ".";
                                else if (bet > maxBet) erroMensagem = "Aposta muito alta. O máximo é " + maxBet + ".";
                                else if (bet > player.getChips()) erroMensagem = "Você não tem fichas suficientes.";
                                else {
                                    player.placeBet(bet);
                                    notifyPlayers("\n\n" + player.getNickname() + " apostou " + player.getCurrentBet());
                                    validBet = true;
                                }
                            } catch (NumberFormatException e) {
                                erroMensagem = "⚠ Por favor, digite um número válido.";
                            }

                        } catch (SocketTimeoutException e) {
                            out.writeUTF("⏰ Tempo esgotado. Apostando o mínimo.");
                            player.placeBet(minBet);
                            notifyPlayers(player.getNickname() + " apostou " + player.getCurrentBet());
                            validBet = true;
                        }
                    }
                }
            }
        }
    }

    private void dealInitialCards() throws IOException {
        for (int i = 0; i < 2; i++) {
            for (Player player : players)
                if (player.isActive() && player.getCurrentBet() > 0)
                    player.addCard(deck.dealCard());
            dealer.addCard(deck.dealCard());
        }

        notifyPlayers("\n============== Mão Inicial ==============");
        notifyPlayers("Dealer: [Carta Oculta], " + dealer.getHand().get(1) + " (Total: " + dealer.getHand().get(1).getValue() + ")");
        for (Player player : players) {
            if (player.isActive() && player.getCurrentBet() > 0)
                notifyPlayers(player.getNickname() + ": " + player.getHandAsString(false) + " (Total: " + player.getHandValue() + ")");
        }
    }

    private void playerTurn(Player player) throws IOException {
        if (player.hasBlackjack()) {
            notifyPlayers(player.getNickname() + " tem Blackjack!");
            return;
        }

        while (!player.isBusted()) {
            String action;
            if (player.isBot()) {
                action = player.decideAction(dealer.getHand().get(1).getValue());
                notifyPlayers(player.getNickname() + " escolheu: " + action);
            } else {
                DataOutputStream out = new DataOutputStream(player.getSocket().getOutputStream());
                DataInputStream in = new DataInputStream(player.getSocket().getInputStream());

                out.writeUTF("\n\nSua mão: " + player.getHandAsString(false) + " (Total: " + player.getHandValue() + ")");
                out.writeUTF("Carta visível do dealer: " + dealer.getHand().get(1) + " (Total: " + dealer.getHand().get(1).getValue() + ")");
                out.writeUTF("\nEscolha uma ação (hit/stand):");

                try {
                    player.getSocket().setSoTimeout(TIMEOUT);
                    action = in.readUTF().toLowerCase();
                    if (!action.equals("hit") && !action.equals("stand")) {
                        out.writeUTF("Ação inválida. Escolha 'hit' ou 'stand'.");
                        continue;
                    }
                } catch (SocketTimeoutException e) {
                    out.writeUTF("Tempo esgotado. Stand automático.");
                    action = "stand";
                }
            }

            if (action.equals("hit")) {
                Card newCard = deck.dealCard();
                player.addCard(newCard);
                notifyPlayers(player.getNickname() + " recebeu: " + newCard + " (Total: " + player.getHandValue() + ")");
                if (player.isBusted()) {
                    notifyPlayers(player.getNickname() + " estourou!");
                    break;
                }
            } else if (action.equals("stand")) break;
        }
    }

    private void dealerTurn() throws IOException {
        notifyPlayers("\n--- Vez do Dealer ---");
        notifyPlayers("Mão do Dealer: " + dealer.getHandAsString(false) + " (Total: " + dealer.getHandValue() + ")");
        while (dealer.getHandValue() < 17 || (dealer.getHandValue() == 17 && hasSoft17())) {
            Card newCard = deck.dealCard();
            dealer.addCard(newCard);
            notifyPlayers("Dealer recebeu: " + newCard + " (Total: " + dealer.getHandValue() + ")");
        }

        if (dealer.isBusted()) notifyPlayers("Dealer estourou!");
        else notifyPlayers("Dealer para com " + dealer.getHandValue());
    }

    private boolean hasSoft17() {
        if (dealer.getHandValue() != 17) return false;
        return dealer.getHand().stream().anyMatch(c -> c.getRank() == Card.Rank.ACE);
    }

    private void determineResults() throws IOException {
        notifyPlayers("\n--- Resultados ---");
        int dealerValue = dealer.getHandValue();
        boolean dealerBusted = dealer.isBusted();

        for (Player player : players) {
            if (player.isActive() && player.getCurrentBet() > 0) {
                int playerValue = player.getHandValue();
                boolean playerBusted = player.isBusted();
                boolean playerBlackjack = player.hasBlackjack();

                if (playerBusted) {
                    notifyPlayers(player.getNickname() + " perdeu (estourou)");
                } else if (dealerBusted) {
                    notifyPlayers(player.getNickname() + " ganhou (dealer estourou)");
                    player.winBet(playerBlackjack);
                } else if (playerValue > dealerValue) {
                    notifyPlayers(player.getNickname() + " ganhou (" + playerValue + " vs " + dealerValue + ")");
                    player.winBet(playerBlackjack);
                } else if (playerValue == dealerValue) {
                    notifyPlayers(player.getNickname() + " empate (" + playerValue + ")");
                    player.push();
                } else {
                    notifyPlayers(player.getNickname() + " perdeu (" + playerValue + " vs " + dealerValue + ")");
                }

                notifyPlayers(player.getNickname() + " agora tem " + player.getChips() + " fichas");
            }
        }
    }

    private void checkPlayersCanContinue() {
        for (Player player : players) {
            if (player.isActive() && player.getChips() < minBet) {
                player.setActive(false);
                try {
                    notifyPlayers(player.getNickname() + " está fora do jogo (fichas insuficientes)");
                    if (!player.isBot()) {
                        DataOutputStream out = new DataOutputStream(player.getSocket().getOutputStream());
                        out.writeUTF("Você está fora do jogo (fichas insuficientes)");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (getActivePlayers().size() == 0) isOver = true;
    }

    private List<Player> getActivePlayers() {
        List<Player> activePlayers = new ArrayList<>();
        for (Player p : players) if (p.isActive()) activePlayers.add(p);
        return activePlayers;
    }

    public ArrayList<String> getNicknames() {
        ArrayList<String> nicknames = new ArrayList<>();
        for (Player p : players) nicknames.add(p.getNickname());
        return nicknames;
    }

    public void addPlayer(Socket playerSocket, String nickname) {
        Player player = new Player(playerSocket, nickname);
        players.add(player);
    
        final String ANSI_CLEAR_SCREEN = "\033[H\033[2J";
        String mensagem = ANSI_CLEAR_SCREEN +
                          "\n\n" + player.getNickname() +
                          " entrou para o jogo na mesa \"" + gameName + "\".";
    
        try {
            notifyPlayers(mensagem);
        } catch (IOException e) {
            e.printStackTrace();
        }
    
        synchronized (joinLock) {
            joinLock.notifyAll();
        }
    }

    public void addBotPlayer() {
        String[] botNames = {"Bot1", "Bot2", "Bot3", "Bot4", "Bot5"};
        String botName = botNames[new Random().nextInt(botNames.length)] + "-" + gameid;
        BotStrategy strategy = switch (new Random().nextInt(3)) {
            case 0 -> new ConservativeStrategy();
            case 1 -> new BasicStrategy();
            default -> new AggressiveStrategy();
        };
        Player bot = new Player(null, botName, true, strategy);
        players.add(bot);
        try {
            notifyPlayers(bot.getNickname() + " (bot) entrou para o jogo.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void notifyPlayers(String message) throws IOException {
        for (Player player : players) {
            if (!player.isBot() && player.getSocket() != null && !player.getSocket().isClosed()) {
                DataOutputStream out = new DataOutputStream(player.getSocket().getOutputStream());
                out.writeUTF(message);
            }
        }
        System.out.println("Mesa " + gameid + ": " + message);
    }

    public void checkToStart() {
        if (this.players.size() >= 1 && this.userInputReady == this.players.size()) {
            if (getHumanPlayers().size() == 1) addBotPlayer();
            this.start();
        }
    }

    private List<Player> getHumanPlayers() {
        List<Player> humans = new ArrayList<>();
        for (Player p : players) if (!p.isBot()) humans.add(p);
        return humans;
    }

    private void cleanUpConnections() {
        for (Player player : players) {
            if (!player.isBot()) {
                try {
                    if (player.getSocket() != null && !player.getSocket().isClosed()) {
                        player.getSocket().close();
                    }
                } catch (IOException e) {
                    System.err.println("Não conseguiu fechar a conexão: " + e.getMessage());
                }
            }
        }
    }
}
