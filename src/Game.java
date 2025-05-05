// Game.java
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
        // incrementa o contador de quantos jogadores estão prontos para jogar
        // verifica se já pode começar o jogo
        // syncronized, pois está incrementando um contador (pode dar conflito em concorrência)
        this.userInputReady++;
        checkToStart();
    }

    public void run() {
        try {
            // o jogo está em progresso
            gameInProgress = true;
            notifyPlayers("Bem-vindo à mesa de Blackjack " + gameName + "!");
            // enquanto o jogo ainda está ativo, joga uma rodada
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
        // reinicia a mão do dealer
        dealer.clearHand();
        // reinicia a mão dos jogadores, se eles ainda estão jogando (não foram eliminados)
        for (Player p : players) if (p.isActive()) p.clearHand();
        collectBets();
        dealInitialCards();
    
        // cria um pool de threads fixo, com o tamanho do número de jogadores, para que os jogadores consigam jogar sua rodada concorrentemente
        ExecutorService executor = Executors.newFixedThreadPool(players.size());
        List<Future<?>> futures = new ArrayList<>();

        // para cada jogador, submete uma thread para ele começar a jogar
        for (Player player : players) {
            if (player.isActive() && player.getCurrentBet() > 0) {
                Future<?> future = executor.submit(() -> {
                    try {
                        playerTurn(player);
                    } catch (IOException e) {
                        System.err.println("Erro durante o turno de " + player.getNickname() + ": " + e.getMessage());
                        player.setActive(false);
                    }
                });
                futures.add(future);
            }
        }

        // Aguarda todos os jogadores terminarem seus turnos
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();

        // vez do dealer
        dealerTurn();
        // verifica os resultados
        determineResults();
    
        try {
            Thread.sleep(8000); 
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    
        // verifica a viabilidade dos jogadores irem para a próxima partida
        checkPlayersCanContinue();
    
        Iterator<Player> iter = players.iterator();
        while (iter.hasNext()) {
            Player p = iter.next();
            // se o jogador não for um bot e não estiver mais ativo
            if (!p.isBot() && !p.isActive()) {
                Socket sock = p.getSocket();
                // só envia se o socket ainda estiver aberto
                // avisa ao jogador que ele saiu da partida
                if (sock != null && !sock.isClosed()) {
                    try (DataOutputStream out = new DataOutputStream(sock.getOutputStream())) {
                        out.writeUTF("Você saiu da partida.");
                        out.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                // fecha e remove da lista uma única vez
                p.closeSocket();
                iter.remove();
            }
        }

        // verifica se ainda há humanos ativos na partida
        boolean anyHumanLeft = players.stream().anyMatch(p -> !p.isBot());
        // se não há jogadores humanos na partida, o jogo é finalizado
        if (!anyHumanLeft) {
            isOver = true;
            System.out.println("Mesa " + gameid +
                " foi encerrada porque todos os jogadores saíram ou ficaram sem fichas.");
        }
    }

   private void collectBets() throws IOException {
    // cria uma thread pool para que cada jogador consiga apostar de forma concorrente
    ExecutorService executor = Executors.newFixedThreadPool(players.size());
    List<Future<?>> futures = new ArrayList<>();

    for (Player player : players) {
        // se o jogador ainda estiver ativo, submete a tarefa no executor
        if (player.isActive()) {
            Future<?> future = executor.submit(() -> {
                try {
                    // se o jogador for o bot: gera um número aleatório no intervalo das apostas mínimas e máximas da mesa,
                    // verifica se ele pode apostar esse número (pega o mínimo entre o que ele tem e o quanto ele quer apostar)
                    // notifica todos os jogadores o quanto ele quer apostar
                    if (player.isBot()) {
                        int bet = new Random().nextInt(maxBet - minBet + 1) + minBet;
                        player.placeBet(Math.min(bet, player.getChips()));
                        notifyPlayers(player.getNickname() + " apostou " + player.getCurrentBet());
                    } else {
                        // se for um jogador humano
                        DataOutputStream out = new DataOutputStream(player.getSocket().getOutputStream());
                        DataInputStream in = new DataInputStream(player.getSocket().getInputStream());

                        boolean validBet = false;
                        String erroMensagem = "";
                        
                        // enquanto a aposta for inválida e o jogador estiver ativo
                        while (!validBet && player.isActive()) {

                            // se houver mensagem de erro, mostra para o jogador (segunda tentativa em diante)
                            if (!erroMensagem.isEmpty()) {
                                out.writeUTF(erroMensagem);
                                out.flush();
                                try {
                                    Thread.sleep(8000);
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                    return;
                                }
                                erroMensagem = "";
                            }

                            final String ANSI_CLEAR_SCREEN = "\033[H\033[2J";
                            String message = ANSI_CLEAR_SCREEN + "============== Apostas ==============";
                            out.writeUTF(message);

                            out.writeUTF("Você tem " + player.getChips() + " fichas. Quanto deseja apostar? (min " + minBet + ", max " + maxBet + ")");

                            try {
                                // coloca um tempo limite para o jogador apostar
                                player.getSocket().setSoTimeout(TIMEOUT);
                                String betStr = in.readUTF();
                                
                                // lê a aposta, e se ela for inválida (NULL), elimina o jogador automaticamente
                                if (betStr == null) {
                                    player.setActive(false);
                                    break;
                                }

                                try {
                                    int bet = Integer.parseInt(betStr.trim());

                                    // se o valor da aposta for inválido, coloca uma mensagem de erro
                                    if (bet < minBet) erroMensagem = "Aposta muito baixa. O mínimo é " + minBet + ".";
                                    else if (bet > maxBet) erroMensagem = "Aposta muito alta. O máximo é " + maxBet + ".";
                                    else if (bet > player.getChips()) erroMensagem = "Você não tem fichas suficientes.";
                                    else {
                                        // se for válido, associa a aposta ao jogador, indica que a aposta é válida, e avisa a todos os jogadores
                                        player.placeBet(bet);
                                        notifyPlayers("\n\n" + player.getNickname() + " apostou " + player.getCurrentBet());
                                        validBet = true;
                                    }
                                } catch (NumberFormatException e) {
                                    erroMensagem = "Por favor, digite um número válido.";
                                }

                            } catch (SocketTimeoutException e) {
                                // se não responder a tempo, a aposta é automaticamente a mínima
                                out.writeUTF("Tempo esgotado. Apostando o mínimo.");
                                player.placeBet(minBet);
                                notifyPlayers(player.getNickname() + " apostou " + player.getCurrentBet());
                                validBet = true;
                            }
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Erro ao coletar aposta de " + player.getNickname() + ": " + e.getMessage());
                    player.setActive(false);
                }
            });

            futures.add(future);
        }
    }

    // Aguarda todos os jogadores finalizarem as apostas
    for (Future<?> future : futures) {
        try {
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    executor.shutdown();
}
    private void dealInitialCards() throws IOException {
        // cada jogador e o dealer começam com duas cartas
        for (int i = 0; i < 2; i++) {
            for (Player player : players)
                if (player.isActive() && player.getCurrentBet() > 0)
                    player.addCard(deck.dealCard());
            dealer.addCard(deck.dealCard());
        }

        notifyPlayers("\n============== Mão Inicial ==============");
        // a primeira carta do dealer é oculta, a segunda e o seu valor são mostrados
        notifyPlayers("Dealer: [Carta Oculta], " + dealer.getHand().get(1) + " (Total: " + dealer.getHand().get(1).getValue() + ")");
        // mostra as cartas e o valor total da mão dos jogadores para todos os jogadores
        for (Player player : players) {
            if (player.isActive() && player.getCurrentBet() > 0)
                notifyPlayers(player.getNickname() + ": " + player.getHandAsString(false) + " (Total: " + player.getHandValue() + ")");
        }
    }

    private void playerTurn(Player player) throws IOException {
        // se o jogador tiver um blackjack, avisa a todos os jogadores e retorna
        if (player.hasBlackjack()) {
            notifyPlayers(player.getNickname() + " tem Blackjack!");
            return;
        }

        // enquanto a pontuação das cartas do jogador for menor que 21
        while (!player.isBusted()) {
            String action;
            // se o jogador for um bot, ele decide sua ação (dependendo da estratégia) e notifica todos os jogadores
            if (player.isBot()) {
                action = player.decideAction(dealer.getHand().get(1).getValue());
                notifyPlayers(player.getNickname() + " escolheu: " + action);
            } else {
                // se for um jogador humano, avisa a ele como está a sua mão e a pontuação total, assim como a mão visível do dealer
                // pede para o jogador se vai adicionar mais uma carta ou se vai deixar passar
                DataOutputStream out = new DataOutputStream(player.getSocket().getOutputStream());
                DataInputStream in = new DataInputStream(player.getSocket().getInputStream());

                out.writeUTF("\n\nSua mão: " + player.getHandAsString(false) + " (Total: " + player.getHandValue() + ")");
                out.writeUTF("Carta visível do dealer: " + dealer.getHand().get(1) + " (Total: " + dealer.getHand().get(1).getValue() + ")");
                out.writeUTF("\nEscolha uma ação (hit/stand):");

                    try {
                        // coloca um tempo limite para o jogador responder
                        player.getSocket().setSoTimeout(TIMEOUT);
                        action = in.readUTF().toLowerCase();
                        if (!action.equals("hit") && !action.equals("stand")) {
                            out.writeUTF("Ação inválida. Escolha 'hit' ou 'stand'.");
                            continue;
                        }
                    } catch (SocketTimeoutException e) {
                        // se o jogador não responder a tempo, stand é automático
                        out.writeUTF("Tempo esgotado. Stand automático.");
                        action = "stand";
                    }
            }

            // se o jogador escolher hit
            if (action.equals("hit")) {
                // pega uma nova carta do deck e adiciona na mão do jogador
                Card newCard = deck.dealCard();
                player.addCard(newCard);
                // avisa a todos os jogadores a nova carta e a sua pontuação total
                notifyPlayers(player.getNickname() + " recebeu: " + newCard + " (Total: " + player.getHandValue() + ")");
                // se o somatório das cartas for maior que 21, avisa a todos que o jogador estourou e finaliza a rodada
                if (player.isBusted()) {
                    notifyPlayers(player.getNickname() + " estourou!");
                    break;
                }
            // se o jogador escolher stand, apenas finaliza a rodada
            } else if (action.equals("stand")) break;
        }
    }

    private void dealerTurn() throws IOException {
        // mostra a mão completa do dealer
        notifyPlayers("\n--- Vez do Dealer ---");
        notifyPlayers("Mão do Dealer: " + dealer.getHandAsString(false) + " (Total: " + dealer.getHandValue() + ")");
        // verifica se a mão do dealer é menor que 17
        // ou se é igual a 17 e ele tem pelo menos um As
        while (dealer.getHandValue() < 17 || (dealer.getHandValue() == 17 && hasSoft17())) {
            // adiciona uma nova carta para o dealer e notifica todos os jogadores
            Card newCard = deck.dealCard();
            dealer.addCard(newCard);
            notifyPlayers("Dealer recebeu: " + newCard + " (Total: " + dealer.getHandValue() + ")");
        }

        // se o dealer estourou, notifica todos os jogadores
        if (dealer.isBusted()) notifyPlayers("Dealer estourou!");
        // caso contrário, notfica todos os jogadores para o valor da mão do dealer
        else notifyPlayers("Dealer para com " + dealer.getHandValue());
    }

    private boolean hasSoft17() {
        // se a mão do dealer for diferente que 17, retorna falso
        if (dealer.getHandValue() != 17) return false;
        // se for igual a 17 e ele tiver pelo menos um As, retorna true
        // caso contrário, retorna false
        return dealer.getHand().stream().anyMatch(c -> c.getRank() == Card.Rank.ACE);
    }

    private void determineResults() throws IOException {
        notifyPlayers("\n--- Resultados ---");
        int dealerValue = dealer.getHandValue();
        boolean dealerBusted = dealer.isBusted();

        for (Player player : players) {
            // se o jogador estiver ativo e tiver uma aposta válida
            if (player.isActive() && player.getCurrentBet() > 0) {
                // pega o valor da mão do jogador
                int playerValue = player.getHandValue();
                // verifica se ele estourou (> 21)
                boolean playerBusted = player.isBusted();
                // verifica se ele tem um blackjacl (2 cartas e valor igual a 21)
                boolean playerBlackjack = player.hasBlackjack();

                // se o jogador estourou, avisa a todos
                if (playerBusted) {
                    notifyPlayers(player.getNickname() + " perdeu (estourou)");
                // se o dealer estourou, avisa a todos (todos ganham)
                // o jogador ganha a aposta (dobra as fichas, ou 2.5x as fichas se tiver tido blackjack)
                } else if (dealerBusted) {
                    notifyPlayers(player.getNickname() + " ganhou (dealer estourou)");
                    player.winBet(playerBlackjack);
                // se o jogador ganhou do dealer, avisa a todos e ganha a partida
                } else if (playerValue > dealerValue) {
                    notifyPlayers(player.getNickname() + " ganhou (" + playerValue + " vs " + dealerValue + ")");
                    player.winBet(playerBlackjack);
                // se o jogador empatou com o dealer, avisa a todos e ganha o valor da aposta
                } else if (playerValue == dealerValue) {
                    notifyPlayers(player.getNickname() + " empate (" + playerValue + ")");
                    player.push();
                // caso contrário, avisa a todos que perdeu a partida
                } else {
                    notifyPlayers(player.getNickname() + " perdeu (" + playerValue + " vs " + dealerValue + ")");
                }

                // avisa a todos quantas fichas o jogador tem
                notifyPlayers(player.getNickname() + " agora tem " + player.getChips() + " fichas");
            }
        }
    }

    private void checkPlayersCanContinue() {
        for (Player player : players) {
            // se o jogador for ativo, mas não tiver o número mínimo de fichas para aposta, ele é marcado comp inativo
            if (player.isActive() && player.getChips() < minBet) {
                player.setActive(false);
                try {
                    // notifica a todos que o jogador foi eliminado
                    notifyPlayers(player.getNickname() + " está fora do jogo (fichas insuficientes)");
                    if (!player.isBot()) {
                        // se não for um bot, avisa no terminal do jogador que ele foi eliminado
                        DataOutputStream out = new DataOutputStream(player.getSocket().getOutputStream());
                        out.writeUTF("Você está fora do jogo (fichas insuficientes)");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // se os jogadores ativos forem zero, finaliza o jogo
        if (getActivePlayers().size() == 0) isOver = true;
    }

    private List<Player> getActivePlayers() {
        // cria uma lista apenas com os jogadores ativos da partida
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
        // adiciona um novo jogador a partida
        Player player = new Player(playerSocket, nickname);
        players.add(player);
    
        // avisa a todos os jogadores que um novo jogador entrou
        final String ANSI_CLEAR_SCREEN = "\033[H\033[2J";
        String nomeMesa = gameName.isEmpty() ? "Mesa " + gameid : gameName;
        String mensagem = ANSI_CLEAR_SCREEN +
            "\n\n" + player.getNickname() +
            " entrou para o jogo na mesa \"" + nomeMesa + "\".";
    
        try {
            notifyPlayers(mensagem);
        } catch (IOException e) {
            e.printStackTrace();
        }
    
        // avisa a todos os jogadores que estão esperando o jogo começar
        synchronized (joinLock) {
            joinLock.notifyAll();
        }
    }

    public void addBotPlayer() {
        String[] botNames = {"Bot1", "Bot2", "Bot3", "Bot4", "Bot5"};
        // cria um nome meio aleatório para o bot
        String botName = botNames[new Random().nextInt(botNames.length)] + "-" + gameid;
        // escolhe aleatoriamente uma estratégia para o bot (conservador, básico ou agressivo)
        BotStrategy strategy = switch (new Random().nextInt(3)) {
            case 0 -> new ConservativeStrategy();
            case 1 -> new BasicStrategy();
            default -> new AggressiveStrategy();
        };
        // adiciona o bot como um novo player
        Player bot = new Player(null, botName, true, strategy);
        players.add(bot);
        try {
            // notifica todos os jogadores
            notifyPlayers(bot.getNickname() + " (bot) entrou para o jogo.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void notifyPlayers(String message) throws IOException {
        for (Player player : players) {
            // manda a mensagem para todos os jogadores que não são bot e que têm um socket ativo
            if (!player.isBot() && player.getSocket() != null && !player.getSocket().isClosed()) {
                DataOutputStream out = new DataOutputStream(player.getSocket().getOutputStream());
                out.writeUTF(message);
            }
        }
        System.out.println("Mesa " + gameid + ": " + message);
    }

    public void checkToStart() {
        // condições para começar o jogo: ter um ou mais jogadores humanos na mesa,
        // e todos os jogadores que entraram na mesa estão prontos
        // quando essas condições forem satisfeitas, inicia a thread
        if (this.players.size() >= 1 && this.userInputReady == this.players.size()) {
            if (getHumanPlayers().size() == 1) addBotPlayer();
            this.start();
        }
    }

    private List<Player> getHumanPlayers() {
        // cria um array apenas com os jogadores humanos da partida
        List<Player> humans = new ArrayList<>();
        for (Player p : players) if (!p.isBot()) humans.add(p);
        return humans;
    }

    private void cleanUpConnections() {
        for (Player player : players) {
            // se o jogador não for um bot
            if (!player.isBot()) {
                try {
                    // se o socket ainda não foi fechado, fecha o socket
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
