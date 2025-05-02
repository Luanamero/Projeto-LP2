import java.io.IOException;
import java.net.Socket;

public class Player {
    private Socket socket;
    private String nickname;
    private int points;
    private int globalPoints;
    private boolean isActive;

    public Player(Socket socket, String nickname) {
        this.socket = socket;
        this.nickname = nickname;
        this.points = 5;  // Start with 5 points
        this.isActive = true;  // Player is active initially
    }

    public Socket getSocket() {
        return socket;
    }
    
    public void closeSocket() {
    	try {
			socket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    public String getNickname() {
        return nickname;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public void incrementPoints() {
        this.points++;
    }
    
    public void incrementGlobalPoints() {
        this.globalPoints++;
    }
    
    public int getGlobalPoints() {
    	return this.globalPoints;
    }

    public void decrementPoints() {
        this.points = Math.max(0, this.points - 1);
        if (this.points == 0) {
            this.isActive = false;  // Automatically deactivate if points are zero
        }
    }
}
