import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

public class Server {

	static Semaphore sem = new Semaphore(6); // Maximum of 6 players
	static ArrayList<Socket> waitingPlayers = new ArrayList<>(); // Players waiting for a game
	static ArrayList<String> nicknames = new ArrayList<>(); // Client nicknames
	static ArrayList<Game> games = new ArrayList<>(); // List of all games
	static Map<String, String> tickets = new HashMap<>(); // Client tickets
	static ArrayList<String> ticketsOnly = new ArrayList<>();
	static ArrayList<Player> leaderboard = new ArrayList<>();

	static int playerIdCounter = 1;
	private static int nextGameId = 1;

	public static void addNewGame() {
		Game newGame = new Game(nextGameId++);
		newGame.setSemaphore(sem);
		games.add(newGame);
	}
	
	public static void sendWelcomeMessage(PrintWriter out) {
	    String welcomeArt = 
	        "  _______      ___      .___  ___.  _______ \n" +
	        " /  _____|    /   \\     |   \\/   | |   ____|\n" +
	        "|  |  __     /  ^  \\    |  \\  /  | |  |__   \n" +
	        "|  | |_ |   /  /_\\  \\   |  |\\/|  | |   __|  \n" +
	        "|  |__| |  /  _____  \\  |  |  |  | |  |____ \n" +
	        " \\______| /__/     \\__\\ |__|  |__| |_______|\n";

	    String welcomeMessage = "Welcome to Guess 2/3 of The Average Game!\n" +
	                            "-----------------------------------------\n" +
	                            "Enter your nickname to start playing. Each player will guess numbers to accumulate points. Last player standing wins!\n" ;

	    out.println(welcomeArt);
	    out.println(welcomeMessage);
	}


	public static String issueTicket(String nickname) {
		String playerTicket = "";
		if ((!ticketsOnly.contains(nickname))) {
			String ticket = nickname + playerIdCounter++;
			tickets.put(nickname, ticket);
			nicknames.add(nickname);
			ticketsOnly.add(ticket);
			playerTicket = ticket;
		}
		else {
			if (ticketsOnly.contains(nickname)) {
				playerTicket = nickname;
			}
		}
		return playerTicket;
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
			System.out.println("No games available. A new game will be created automatically.");
	        addNewGame();
	    }

	    // Header
	    gamesList.append(String.format("%-10s %-20s %-10s %-15s\n", "GAME ID", "GAME NAME", "STATUS", "PLAYERS"));
	    gamesList.append("----------------------------------------------------------\n");

	    for (Game g : games) {
	        if (g.getPlayers().size() < 6 && !g.getGameFinishs()) {
	            String status = g.isGameInProgress() ? "In Progress" : "Waiting";
	            gamesList.append(String.format("%-10d %-20s %-10s %-15s\n",
	                g.getGameid(),
	                g.getGameName().isEmpty() ? "N/A" : g.getGameName(),
	                status,
	                g.getNicknames().toString()
	            ));
	        }
	    }

	    gamesList.append("Please pick a game ID or type 'create' to start a new game.");

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
			System.out.println("Invalid game ID.");
			return false;
		}

		if (gameSelected.isGameInProgress()) {
			System.out.println("This game has already started.");
			return false;
		}

		if (gameSelected.getNicknames().size() == 6) {
			System.out.println("This game has reached the maximum number of players.");
			return false;
		}

		return true; // Valid selection
	}
	
	public static synchronized void updateLeaderboard(Player winner) {
        winner.incrementGlobalPoints();
        leaderboard.removeIf(player -> player.getNickname().equals(winner.getNickname()));
        leaderboard.add(winner);
        leaderboard.sort((p1, p2) -> p2.getGlobalPoints() - p1.getGlobalPoints());
        
        // Keep only the top 5 players
        while (leaderboard.size() > 5) {
            leaderboard.remove(5);
        }
    }


	public static String displayLeaderboard() {
	    StringBuilder leaderboardString = new StringBuilder("Leaderboard:\n");
	    for (Player player : leaderboard) {
	        leaderboardString.append(player.getNickname())
	                         .append(": ")
	                         .append(player.getGlobalPoints())
	                         .append("\n");
	    }
	    return leaderboardString.toString();
	}

	
	public static void handlePlayerConnection(Socket p) {
		try {
			PrintWriter out = new PrintWriter(p.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream())); // Read from client input
			
			sendWelcomeMessage(out);
			out.println("Enter your nickname:");
			String nickname = in.readLine(); // Read nickname from client
			System.out.println("Received nickname: " + nickname);
			
			out.println("Your ticket: "+issueTicket(nickname));
			out.println(displayLeaderboard());
			out.println(showGames()); // Show available games
			
			int gameSelection = 0;
			
			try {

				String userGameSelection = in.readLine();
				if (userGameSelection.equalsIgnoreCase("create")) {
					out.println("Enter game name: ");
					String chosenName = in.readLine();
					addNewGame();
					games.get(games.size()-1).setGameName(chosenName);
					games.get(games.size()-1).addPlayer(p, nickname);
					gameSelection = games.get(games.size()-1).getGameid();
				}
				else {
					boolean wrong = false;
					do {
						if(wrong == true) {
							out.println("Please provide a valid game id.");
							userGameSelection = in.readLine();
						}
						gameSelection = Integer.parseInt(userGameSelection); // Client's game choice
						wrong = true;
					} while (handleGameSelection(gameSelection) == false);
					games.get(gameSelection-1).addPlayer(p, nickname);
				}

			} catch (NumberFormatException e) {
				out.println("Invalid input. Please enter a valid game ID.");
				p.close(); // Close invalid client connection to avoid resource leaks
			}
			Game gChosen = games.get(gameSelection-1);
			if (gChosen.getNicknames().size()<2) {
				out.println("Wait until more players join");
			}
			
			while(gChosen.getNicknames().size()<2) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			out.println("Welcome to the game.");
			String input = "";
			while(true) {
				out.println( "Type 'ready' when you are ready to start.");
				input = in.readLine();
				if (input.equalsIgnoreCase("ready")) {
					games.get(gameSelection-1).setUserInputReady();
					break;
				}

				else {
	                out.println("Invalid input. Type 'ready' to start.");
	            }
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally {
			sem.release();
		}
	}

	public static void main(String[] args) {
		int port = 1505;
		try (ServerSocket serverSocket = new ServerSocket(port)) {
			System.out.println("Server waiting for players on port " + port);

			while (true) {
				sem.acquire(); // Ensure there's space for new players
				Socket playerSocket = serverSocket.accept(); // Accept a new player

				 Thread clientThread = new Thread(() -> {handlePlayerConnection(playerSocket);});
		         clientThread.start(); // Start the thread
			}
		} catch (IOException | InterruptedException e) {
			System.out.println("Server exception: " + e.getMessage());
		}
	}



}