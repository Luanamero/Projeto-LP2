
import java.util.concurrent.locks.ReentrantLock;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class Game extends Thread {

	private ArrayList<Player> players = new ArrayList<>();
	private int gameid =1;
	private int userInputReady =  0;
	private String gameName = "";
	private Map<String, Integer> playerScores;
	private Player winner = null;


	private boolean isOver = false;
	private Semaphore sem;

	final int TIMEOUT = 50000;
	boolean gameInProgress=false;

	int roundCounter=0;

	boolean roundInProgress=false;

	int maxRounds=0;

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
			notifyPlayers("Game is starting...");

			while (!isOver) {
				collectPlayerInputs();
				roundCounter++;
			}
		} catch (IOException e) {
			System.err.println("IO error in game thread: " + e.getMessage());
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
			notifyPlayers(player.getNickname() + " has joined the game.");
		} catch (IOException e) {
			// TODO Auto-generated catch block
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
	            out.println("Enter your guess (between 0 and 100):");
	            player.getSocket().setSoTimeout(20000); // 20 seconds timeout

	            String inputStr = in.readLine();
	            if (inputStr == null || inputStr.isEmpty()) {
	                // Treat no input or empty input as invalid or timed out
	                out.println("Invalid input or no input received.");
	                inputs.add(-1); // You may choose to handle this differently
	            } else {
	                int input = Integer.parseInt(inputStr);
	                if (input >= 0 && input <= 100) {
	                    inputs.add(input);
	                } else {
	                    out.println("Invalid input. Please enter a number between 0 and 100.");
	                    inputs.add(-1);
	                }
	            }
	        } catch (SocketTimeoutException e) {
	            out.println("Time's up! You didn't enter a guess in time.");
	            inputs.add(-1);
	        } catch (NumberFormatException e) {
	            out.println("Invalid format. Please enter a valid number.");
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
	
	private void sendEchoMessage() throws IOException {
	    ArrayList<Player> activePlayers = new ArrayList<>();

	    for (Player player : players) {
	        if (!player.isActive()) {
	            continue;  // Skip inactive players
	        }

	        PrintWriter out = new PrintWriter(player.getSocket().getOutputStream(), true);
	        BufferedReader in = new BufferedReader(new InputStreamReader(player.getSocket().getInputStream()));

	        try {
	            out.println("Echo msg: Type 'yes' to confirm your presence.");
	            player.getSocket().setSoTimeout(10000);  // Wait for 10 seconds

	            String response = in.readLine();
	            // Consider both no response and an empty string as inactivity signs
	            if (response != null && !response.trim().isEmpty()) {
	                activePlayers.add(player);  // The player responded appropriately
	            } else {
	                out.println("No or invalid response. You have been marked as inactive.");
	                eliminatePlayer(player);
	            }
	        } catch (SocketTimeoutException e) {
	            out.println("You did not respond in time and have been marked as inactive.");
	            eliminatePlayer(player);
	        } catch (IOException e) {
	            System.err.println("IO Exception for player " + player.getNickname() + ": " + e.getMessage());
	            eliminatePlayer(player);
	        }
	    }

	    // Log current active players for debugging
	    System.out.println("Active players after echo check: " + activePlayers.size());

	    // Check if the game should end
	    if (activePlayers.size() < 2) {
	        if (activePlayers.size() == 1) {
	            winner = activePlayers.get(0);  // Assign the last standing player as winner
	        }
	        gameOver();  // End the game if fewer than 2 players are active
	    }
	}

//	private void announceRoundWinner(ArrayList<Integer> inputs) throws IOException {
//		double average = inputs.stream().mapToInt(Integer::intValue).average().orElse(0);
//		double target = (2.0 / 3.0) * average;
//		int winnerIndex = -1;
//		double smallestDifference = Double.MAX_VALUE;
//
//		for (int i = 0; i < inputs.size(); i++) {
//			if (!players.get(i).isActive()) {
//				continue; // Skip inactive players in the winner calculation
//			}
//			double difference = Math.abs(inputs.get(i) - target);
//			if (difference < smallestDifference) {
//				smallestDifference = difference;
//				winnerIndex = i;
//			}
//		}
//
//		for (Player player : players) {
//			PrintWriter out = new PrintWriter(player.getSocket().getOutputStream(), true);
//			int playerIndex = players.indexOf(player);
//			if (playerIndex == winnerIndex) {
//				//				player.incrementPoints(); // Use the increment method in Player class
//				out.println("You win this round! Your guess was closest to two-thirds of the average (" + target + ").");
//
//			} else {
//				out.println("You lose this round. Your guess was " + (playerIndex < inputs.size() ? inputs.get(playerIndex) : "N/A") + ".");
//				player.decrementPoints(); // Use the decrement method in Player class
//			}
//		}
//	}


	private void eliminatePlayers() throws IOException {
		for (Player player : players) {
			if (player.getPoints() == 0 && player.isActive()) { // Check if the player should be eliminated
				player.setActive(false); // Mark as eliminated
				for (Player p : players) { // Notify all players
					PrintWriter out = new PrintWriter(p.getSocket().getOutputStream(), true);
					out.println("Client: " + player.getNickname() + " has been eliminated!");
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
				out.println("You win this round! Your guess was closest to two-thirds of the average (" + target + ").");
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
				out.println("You win this round! Your guess was closest to two-thirds of the average (" + target + ").");

			} else {
				out.println("You lose this round. Your guess was " + (playerIndex < inputs.size() ? inputs.get(playerIndex) : "N/A") + ".");
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

		//0 Discouragement
		if (count == 2) { 
			int firstInput = inputs.get(players.indexOf(activePlayers.get(0)));
			int secondInput = inputs.get(players.indexOf(activePlayers.get(1)));

			if ((firstInput == 0 && secondInput > 0) || (secondInput == 0 && firstInput > 0)) {
				if(firstInput > 0) {
					winner=activePlayers.get(0); 
					notifyPlayers("Player "+activePlayers.get(1).getNickname()+" has been eliminated.");
				}
				else {
					winner=activePlayers.get(1);
					notifyPlayers("Player "+activePlayers.get(0).getNickname()+" has been eliminated.");
				}  
				gameOver();
				//	            return;
			}
		}
		else if(count==1) {
			winner =activePlayers.get(0);
			gameOver();}
		else if(count==0) {
			gameOver();
		}
	}

	private void eliminatePlayer(Player p) throws IOException {
		p.setActive(false);
		notifyPlayers("Client: " + p.getNickname() + " has been eliminated!");
	}

	private void gameOver() {
		System.out.println("Inside gameOver()");
		if(winner!=null) {
			winner.incrementGlobalPoints();
			Server.updateLeaderboard(winner);
		}
		try {
			notifyPlayers(
					"  ____    _    __  __ _____    _____     _______ ____  \n" +
							" / ___|  / \\  |  \\/  | ____|  / _ \\ \\   / / ____|  _ \\ \n" +
							"| |  _  / _ \\ | |\\/| |  _|   | | | \\ \\ / /|  _| | |_) |\n" +
							"| |_| |/ ___ \\| |  | | |___  | |_| |\\ V / | |___|  _ < \n" +
							" \\____/_/   \\_\\_|  |_|_____|  \\___/  \\_/  |_____|_| \\_\\\n" +
					"                                                       \n");
			String border = new String(new char[50]).replace("\0", "*");
			if(winner!=null) {
				notifyPlayers("\n" + border + 
						"\n* Congratulations! The final winner is: " + winner.getNickname() + " *" +
						"\n" + border + "\n");
			}else {
				notifyPlayers("\n" + border + 
						"\n* There is no final winner. All players lost. *" +
						"\n" + border + "\n");
			}
			notifyPlayers(Server.displayLeaderboard());
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
				System.err.println("Could not close connection: " + e.getMessage());
			}
		}
	}
}