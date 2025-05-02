import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class Client {

    public static void main(String[] args) {
    	
    	 try (Socket socket = new Socket("localhost", 1505);
    	         PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
    	         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    	         BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in))) {

    	        String fromServer;
    	        while ((fromServer = in.readLine()) != null) {
//    	            System.out.println("Server: " + fromServer);
    	        	System.out.println(fromServer);

    	            if (
    	            		fromServer.startsWith("Please pick a game ID or type 'create' to start a new game.")
    	            		||fromServer.startsWith("Enter your nickname:")
    	            		||fromServer.startsWith("Type 'ready' when you are ready to start")
    	            		||fromServer.startsWith("Enter game name: ")
                            ||fromServer.startsWith("Please provide a valid game id.")
) {
    	                String userInput = stdIn.readLine();  // Only read from user when required
    	                out.println(userInput);
    	            }
    	            else if (fromServer.startsWith("Enter your guess") || fromServer.startsWith("Echo msg")) {
                        handleInputWithTimeout(stdIn, out, 30000); // 10 seconds timeout for user input
                    } 

    	        }
    	    } catch (IOException e) {
    	        System.err.println("Couldn't get I/O for the connection to localhost");
    	        e.printStackTrace();
    	    }
    	}
       

    private static void handleInputWithTimeout(BufferedReader stdIn, PrintWriter out, int timeoutMillis) {
        // Create a new thread to handle user input
        Thread inputThread = new Thread(() -> {
            try {
                String userInput = stdIn.readLine();  // This blocks until input is available
                if (userInput != null) {
                    out.println(userInput);  // Send the input immediately if available
                } else {
                    out.println("");  // Send empty string if no input (EOF)
                }
            } catch (IOException e) {
                System.err.println("Error while reading from user input");
                out.println("");  // Consider sending an empty string or handling the error
            }
        });

        // Start the input thread
        inputThread.start();

        // Create a timer to handle the timeout
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (inputThread.isAlive()) {
                    inputThread.interrupt();  // Attempt to interrupt the blocking read
                    out.println("");  // Send empty string as fallback
                }
                timer.cancel();  // Ensure the timer is cancelled
            }
        }, timeoutMillis);

        try {
            inputThread.join(timeoutMillis);  // Wait for the thread to finish or timeout
            timer.cancel();  // Cancel the timer if the input thread finishes on time
        } catch (InterruptedException e) {
            System.err.println("Main thread was interrupted while waiting for input thread to finish.");
        }
    }

}