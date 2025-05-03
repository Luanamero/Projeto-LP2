import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Game extends Thread {
    private ArrayList<Player> players = new ArrayList<>();
    private int gameid = 1;
    private int userInputReady = 0;
    private String gameName = "";
    private Player winner = null;
    private boolean isOver = false;
    private Semaphore sem;
    private boolean gameInProgress = false;
    private Deck deck;
    private Player dealer;
    private int minBet = 10;
    private int maxBet = 500;
    private final int TIMEOUT = 50000;

    public Game(int id) {
        this.gameid = id;
        this.deck = new Deck();
        this.dealer = new Player(null, "Dealer", true, null);
    }

    public ArrayList<Player> getPlayers() {
        return players;
    }

    public boolean getGameFinishs() {
        return this.isOver;
    }

    public void setGameName(String name) {
        this.gameName = name;
    }

    public String getGameName() {
        return this.gameName;
    }

    public void setUserInputReady() {
        this.userInputReady++;
        checkToStart();
    }

    public int getGameid() {
        return gameid;
    }

    public boolean isGameInProgress() {
        return gameInProgress;
    }

    public int getTimeout() {
        return TIMEOUT;
    }

    public void setSemaphore(Semaphore semaphore) {
        sem = semaphore;
    }

    public void run() {
        try {
            gameInProgress = true;
            notifyPlayers("Bem-vindo à mesa de Blackjack " + gameName + "!");

            while (!isOver) {
                playRound();
                Thread.sleep(2000); // Brief pause between rounds
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("IO exception no jogo: " + e.getMessage());
        } finally {
            cleanUpConnections();
            if (sem != null) {
                sem.release(players.size());
            }
        }
    }

    private void playRound() throws IOException {
        // Check if we have enough active players
        if (getActivePlayers().size() < 1) {
            isOver = true;
            return;
        }

        // Reset hands
        dealer.clearHand();
        for (Player p : players) {
            if (p.isActive()) {
                p.clearHand();
            }
        }

        // Collect bets
        collectBets();

        // Deal initial cards
        dealInitialCards();

        // Player turns
        for (Player player : players) {
            if (player.isActive() && player.getCurrentBet() > 0) {
                playerTurn(player);
            }
        }

        // Dealer turn
        dealerTurn();

        // Determine results
        determineResults();

        // Check if players can continue
        checkPlayersCanContinue();
    }

    private void collectBets() throws IOException {
		for (Player player : players) {
			if (player.isActive()) {
				if (player.isBot()) {
					// Bots bet randomly between min and max bet
					int bet = new Random().nextInt(maxBet - minBet + 1) + minBet;
					player.placeBet(Math.min(bet, player.getChips()));
					notifyPlayers(player.getNickname() + " apostou " + player.getCurrentBet());
				} else {
					PrintWriter out = new PrintWriter(player.getSocket().getOutputStream(), true);
					BufferedReader in = new BufferedReader(new InputStreamReader(player.getSocket().getInputStream()));
	
					boolean validBet = false;
					while (!validBet && player.isActive()) {
						// Envia uma mensagem simples e única que o cliente pode reconhecer
						// Envia o comando especial oculto primeiro
						out.println("##Apostas##");
						// Depois envia a mensagem amigável para o usuário
						out.println("Você tem " + player.getChips() + " fichas. Quanto deseja apostar? (min " + minBet + ", max " + maxBet + ")");
						
						try {
							player.getSocket().setSoTimeout(TIMEOUT);
							String betStr = in.readLine();
							
							if (betStr == null) {
								player.setActive(false);
								break;
							}
							
							try {
								int bet = Integer.parseInt(betStr.trim());
								
								if (bet < minBet) {
									out.println("Aposta muito baixa. O mínimo é " + minBet + ".");
								} else if (bet > maxBet) {
									out.println("Aposta muito alta. O máximo é " + maxBet + ".");
								} else if (bet > player.getChips()) {
									out.println("Você não tem fichas suficientes.");
								} else {
									player.placeBet(bet);
									notifyPlayers(player.getNickname() + " apostou " + player.getCurrentBet());
									validBet = true;
								}
							} catch (NumberFormatException e) {
								out.println("Por favor, digite um número válido.");
							}
						} catch (SocketTimeoutException e) {
							out.println("Tempo esgotado. Apostando o mínimo.");
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
		// Deal two cards to each player and dealer
		for (int i = 0; i < 2; i++) {
			for (Player player : players) {
				if (player.isActive() && player.getCurrentBet() > 0) {
					player.addCard(deck.dealCard());
				}
			}
			dealer.addCard(deck.dealCard());
		}
	
		// Show initial hands
		notifyPlayers("\n--- Mão Inicial ---");
		// Mostra apenas a carta visível do dealer com seu valor
		notifyPlayers("Dealer: [Carta Oculta], " + dealer.getHand().get(1) + " (Total: " + dealer.getHand().get(1).getValue() + ")");
		
		for (Player player : players) {
			if (player.isActive() && player.getCurrentBet() > 0) {
				notifyPlayers(player.getNickname() + ": " + player.getHandAsString(false) + 
					" (Total: " + player.getHandValue() + ")");
			}
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
				action = player.decideAction(dealer.getHand().get(1).getValue()); // Usar a carta visível (índice 1)
				notifyPlayers(player.getNickname() + " escolheu: " + action);
			} else {
				PrintWriter out = new PrintWriter(player.getSocket().getOutputStream(), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(player.getSocket().getInputStream()));
	
				out.println("Sua mão: " + player.getHandAsString(false) + " (Total: " + player.getHandValue() + ")");
				// Mostrar a mesma carta visível que foi mostrada inicialmente (índice 1)
				out.println("Carta visível do dealer: " + dealer.getHand().get(1) + " (Total: " + dealer.getHand().get(1).getValue() + ")");
				out.println("Escolha uma ação (hit/stand):");
	
				try {
					player.getSocket().setSoTimeout(TIMEOUT);
					action = in.readLine().toLowerCase();
					if (!action.equals("hit") && !action.equals("stand")) {
						out.println("Ação inválida. Escolha 'hit' ou 'stand'.");
						continue;
					}
				} catch (SocketTimeoutException e) {
					out.println("Tempo esgotado. Stand automático.");
					action = "stand";
				}
			}
	
			if (action.equals("hit")) {
				Card newCard = deck.dealCard();
				player.addCard(newCard);
				notifyPlayers(player.getNickname() + " recebeu: " + newCard + 
					" (Total: " + player.getHandValue() + ")");
	
				if (player.isBusted()) {
					notifyPlayers(player.getNickname() + " estourou!");
					break;
				}
			} else if (action.equals("stand")) {
				break;
			}
		}
	}

    private void dealerTurn() throws IOException {
		notifyPlayers("\n--- Vez do Dealer ---");
		notifyPlayers("Mão do Dealer: " + dealer.getHandAsString(false) + " (Total: " + dealer.getHandValue() + ")");
	
		// Dealer hits on soft 17
		while (dealer.getHandValue() < 17 || (dealer.getHandValue() == 17 && hasSoft17())) {
			Card newCard = deck.dealCard();
			dealer.addCard(newCard);
			notifyPlayers("Dealer recebeu: " + newCard + " (Total: " + dealer.getHandValue() + ")");
		}
	
		if (dealer.isBusted()) {
			notifyPlayers("Dealer estourou!");
		} else {
			notifyPlayers("Dealer para com " + dealer.getHandValue());
		}
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
                        PrintWriter out = new PrintWriter(player.getSocket().getOutputStream(), true);
                        out.println("Você está fora do jogo (fichas insuficientes)");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (getActivePlayers().size() == 0) {
            isOver = true;
        }
    }

    private List<Player> getActivePlayers() {
        List<Player> activePlayers = new ArrayList<>();
        for (Player p : players) {
            if (p.isActive()) {
                activePlayers.add(p);
            }
        }
        return activePlayers;
    }

    public ArrayList<String> getNicknames() {
        ArrayList<String> nicknames = new ArrayList<String>();
        for (Player p : players) {
            nicknames.add(p.getNickname());
        }
        return nicknames;
    }

    public void addPlayer(Socket playerSocket, String nickname) {
        Player player = new Player(playerSocket, nickname);
        players.add(player);
        try {
            notifyPlayers(player.getNickname() + " entrou para o jogo.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addBotPlayer() {
        String[] botNames = {"Bot1", "Bot2", "Bot3", "Bot4", "Bot5"};
        String botName = botNames[new Random().nextInt(botNames.length)] + "-" + gameid;
        
        // Randomly select a strategy
        BotStrategy strategy;
        int strategyChoice = new Random().nextInt(3);
        switch (strategyChoice) {
            case 0: strategy = new ConservativeStrategy(); break;
            case 1: strategy = new BasicStrategy(); break;
            default: strategy = new AggressiveStrategy(); break;
        }
        
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
                PrintWriter out = new PrintWriter(player.getSocket().getOutputStream(), true);
                out.println(message);
            }
        }
        // Also print to server console for logging
        System.out.println("Mesa " + gameid + ": " + message);
    }

    public void checkToStart() {
        if (this.players.size() >= 1 && this.userInputReady == this.players.size()) {
            // Add a bot if there's only one human player
            if (getHumanPlayers().size() == 1) {
                addBotPlayer();
            }
            this.start();
        }
    }

    private List<Player> getHumanPlayers() {
        List<Player> humans = new ArrayList<>();
        for (Player p : players) {
            if (!p.isBot()) {
                humans.add(p);
            }
        }
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