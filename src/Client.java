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
    	            		fromServer.startsWith("Escolha um ID de jogo ou digite 'criar' para criar um novo jogo.")
    	            		||fromServer.startsWith("Digite o seu nome/apelido:")
    	            		||fromServer.startsWith("Digite 'pronto' quando estiver pronto para começar a jogar.")
    	            		||fromServer.startsWith("Digite o nome do jogo: ")
                            ||fromServer.startsWith("Digite um número de ID válido.")
) {
    	                String userInput = stdIn.readLine();  // Only read from user when required
    	                out.println(userInput);
    	            }
    	            else if (fromServer.startsWith("Digite o seu número") || fromServer.startsWith("Echo")) {
                        handleInputWithTimeout(stdIn, out, 50000); // 10 seconds timeout for user input
                    } 

    	        }
    	    } catch (IOException e) {
    	        System.err.println("Não conseguiu se conectar ao localhost");
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
                System.err.println("Erro enquanto lia dados do cliente");
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
            System.err.println("Thread main foi interrompida enquanto esperava as outras.");
        }
    }

}