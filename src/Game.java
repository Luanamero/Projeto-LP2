// import java.util.concurrent.locks.ReentrantLock;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
// import java.util.HashMap;
// import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Game extends Thread {

	private ArrayList<Player> players = new ArrayList<>();
	private int gameid =1;
	private int userInputReady =  0;
	private String gameName = "";
	// private Map<String, Integer> playerScores;
	private Player winner = null;
	private boolean isOver = false;
	private Semaphore sem;
	boolean gameInProgress=false;
	int roundCounter=0;
	boolean roundInProgress=false;
	int maxRounds=0;

	final int TIMEOUT = 50000;

	public Game(int id) {
		this.gameid = id;
	}

	public ArrayList<Player> getPlayers(){
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

	public int getUserInputReady() {
		return userInputReady;
	}

	public int getGameid() {
		return gameid;
	}

	public void setGameid(int gameid) {
		this.gameid = gameid;
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
			notifyPlayers("O jogo está começando...");

			while (!isOver) {
				collectPlayerInputs();
				roundCounter++;
			}
		} catch (IOException e) {
			System.err.println("IO exception no jogo: " + e.getMessage());
		} finally {
			cleanUpConnections();
			if (sem != null) {
				sem.release(players.size());
			}
		}
	}

	public ArrayList<String> getNicknames(){
		ArrayList<String> nicknames = new ArrayList<String>();
		for(Player p: players) {
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

	private void notifyPlayers(String message) throws IOException {
		for (Player player : players) {
			PrintWriter out = new PrintWriter(player.getSocket().getOutputStream(), true);
			out.println(message);
		}
	}

	public void checkToStart() {
		// Check that all players are ready and that the number of ready signals matches the number of players
		if (this.players.size() >= 2 && this.userInputReady == this.players.size()) {
			this.start();  // Start the game only if all players are ready
		}
	}

	private void collectPlayerInputs() throws IOException {
	    ArrayList<Integer> inputs = new ArrayList<>();

	    for (Player player : players) {
	        if (!player.isActive()) {
	            continue;  // Skip inactive players
	        }

	        PrintWriter out = new PrintWriter(player.getSocket().getOutputStream(), true);
	        BufferedReader in = new BufferedReader(new InputStreamReader(player.getSocket().getInputStream()));

	        try {
	            out.println("Digite o seu número (entre 0 e 100):");
	            player.getSocket().setSoTimeout(TIMEOUT);

	            String inputStr = in.readLine();
	            if (inputStr == null || inputStr.isEmpty()) {
	                // Treat no input or empty input as invalid or timed out
	                out.println("Input inválido ou não enviado.");
	                inputs.add(-1); // You may choose to handle this differently
	            } else {
	                int input = Integer.parseInt(inputStr);
	                if (input >= 0 && input <= 100) {
	                    inputs.add(input);
	                } else {
	                    out.println("Input inválido. Digite um número entre 0 e 100.");
	                    inputs.add(-1);
	                }
	            }
	        } catch (SocketTimeoutException e) {
	            out.println("Timeout: Você não adicionou um número a tempo.");
	            inputs.add(-1);
	        } catch (NumberFormatException e) {
	            out.println("Input inválido. Digite um número entre 0 e 100.");
	            inputs.add(-1);
	        }
	    }
	 // After collecting all inputs
		announceRoundWinner(inputs);
		eliminatePlayers();
		notifyRoundSummary(inputs);
		checkEndOfGame(inputs);
		sendEchoMessage();
	}

	private void sendEchoMessagePlayer(Player player, List<Player> activePlayers) throws IOException {
		PrintWriter out = new PrintWriter(player.getSocket().getOutputStream(), true);
	    BufferedReader in = new BufferedReader(new InputStreamReader(player.getSocket().getInputStream()));

	    try {
				out.println("Echo: digite 'sim' para confirmar a sua presença");
				player.getSocket().setSoTimeout(30000);  // Wait for 10 seconds

				String response = in.readLine();
				// Consider both no response and an empty string as inactivity signs
				if (response != null && !response.trim().isEmpty()) {
					activePlayers.add(player);  // The player responded appropriately
				} else {
					out.println("Sem resposta ou resposta inválida. Você foi desativado (eliminado).");
					eliminatePlayer(player);
				}
	        } catch (SocketTimeoutException e) {
	            out.println("Você não respondeu a tempo e foi desativado (eliminado).");
	            eliminatePlayer(player);
	        } catch (IOException e) {
	            System.err.println("IO Exception para o jogador " + player.getNickname() + ": " + e.getMessage());
	            eliminatePlayer(player);
	        }

	}
	
		private void sendEchoMessage() throws IOException {
		ExecutorService executor = Executors.newFixedThreadPool(players.size());
		List<Player> activePlayers = Collections.synchronizedList(new ArrayList<>());

		for (Player player : players) {
			if (!player.isActive()) continue;

			executor.submit(() -> {
				try {
					sendEchoMessagePlayer(player, activePlayers);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
		}

		executor.shutdown();
		try {
			executor.awaitTermination(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	    // Log current active players for debugging
	    System.out.println("Jogadores ativos depois do echo: " + activePlayers.size());

	    // Check if the game should end
	    if (activePlayers.size() < 2) {
	        if (activePlayers.size() == 1) {
	            winner = activePlayers.get(0);  // Assign the last standing player as winner
	        }
	        gameOver();  // End the game if fewer than 2 players are active
	    }
	}

	private void eliminatePlayers() throws IOException {
		for (Player player : players) {
			if (player.getPoints() == 0 && player.isActive()) { // Check if the player should be eliminated
				player.setActive(false); // Mark as eliminated
				for (Player p : players) { // Notify all players
					PrintWriter out = new PrintWriter(p.getSocket().getOutputStream(), true);
					out.println("Jogador: " + player.getNickname() + " foi eliminado!");
				}
			}
		}
	}

	private void announceRoundWinner(ArrayList<Integer> inputs) throws IOException {
		double average = inputs.stream().mapToInt(Integer::intValue).average().orElse(0);
		double target = (2.0 / 3.0) * average;
		int winnerIndex = -1;
		double smallestDifference = Double.MAX_VALUE;

		// Check if both players entered the same number
		if (inputs.size() == 2 && inputs.get(0).equals(inputs.get(1))) {
			// Both players are winners
			for (Player player : players) {
				PrintWriter out = new PrintWriter(player.getSocket().getOutputStream(), true);
				out.println("Você ganhou essa rodada! Seu número foi o mais próximo de 2/3 da média (\" + target + \").");
			}
			return;
		}

		for (int i = 0; i < inputs.size(); i++) {
			if (!players.get(i).isActive()) {
				continue; // Skip inactive players in the winner calculation
			}
			double difference = Math.abs(inputs.get(i) - target);
			if (difference < smallestDifference) {
				smallestDifference = difference;
				winnerIndex = i;
			}
		}

		for (Player player : players) {
			PrintWriter out = new PrintWriter(player.getSocket().getOutputStream(), true);
			int playerIndex = players.indexOf(player);
			if (playerIndex == winnerIndex) {
				out.println("Você ganhou essa rodada! Seu número foi o mais próximo de 2/3 da média (" + target + ").");

			} else {
				out.println("Você perdeu essa rodada. Seu número foi " + (playerIndex < inputs.size() ? inputs.get(playerIndex) : "N/A") + ".");
				player.decrementPoints(); // Use the decrement method in Player class
			}
		}
	}


	private void notifyRoundSummary(ArrayList<Integer> inputs) throws IOException {
		StringBuilder summary = new StringBuilder();
		summary.append("End of Round ").append(roundCounter + 1).append(":\n");
		for (int i = 0; i < inputs.size(); i++) {
			Player player = players.get(i);
			summary.append(player.getNickname()).append(": ")
			.append(inputs.get(i) != null ? inputs.get(i) : "N/A")
			.append(", Points left: ").append(player.getPoints())
			.append(", Status: ").append(player.isActive() ? "Active" : "Eliminated").append("\n");
		}
		notifyPlayers(summary.toString());
	}


	private void checkEndOfGame(ArrayList<Integer> inputs) throws IOException {
		ArrayList<Player> activePlayers = new ArrayList<>();
		int count =0;
		for(int i=0; i<players.size(); i++) {
			if(players.get(i).isActive()) {
				count++;
				activePlayers.add(players.get(i));
			}
		}

		if(count==1) {
			winner =activePlayers.get(0);
			gameOver();}
		else if(count==0) {
			gameOver();
		}
	}

	private void eliminatePlayer(Player p) throws IOException {
		p.setActive(false);
		notifyPlayers("Jogador: " + p.getNickname() + " foi eliminado!");
	}

	private void gameOver() {
		System.out.println("Dentro de gameOver()");
		if(winner!=null) {
			winner.incrementGlobalPoints();
			ProtocolServer.updateLeaderboard(winner);
		}
		try {
			String border = new String(new char[50]).replace("\0", "*");
			notifyPlayers("Fim de jogo!!!");
			if(winner!=null) {
				notifyPlayers("\n" + border + 
						"\n* Parabéns! O vencedor final é: " + winner.getNickname() + " *" +
						"\n" + border + "\n");
			}else {
				notifyPlayers("\n" + border + 
						"\n* Não há um vencedor final. Todos os jogadores perderam. *" +
						"\n" + border + "\n");
			}
			notifyPlayers(ProtocolServer.displayLeaderboard());
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.isOver=true;
	}

	private void cleanUpConnections() {
		for (Player player : players) {
			try {
				player.getSocket().close();
			} catch (IOException e) {
				System.err.println("Não conseguiu fechar a conexão: " + e.getMessage());
			}
		}
	}
}