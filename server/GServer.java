/**
 * GServ Standalone (C) Stefan Knorr 1997
 * Based on JccVsd (C) Stephan Kulow
 */

import java.io.*;
import java.net.*;
import java.util.Date;


/**
 * GServer is the main class of the server application.
 * It checks for new clients that want to play.
 */
public class GServer extends Thread {
  public final int port = 14801; // local port number that is used to build connections
  private ServerSocket listen_socket; // server side socket
  private DataOutputStream out; // output stream for the protocoll file
  PlayerList PL; // list that holds all informations about currently connected players
  private int freeID=1; // a free id for the next connecting client

  /**
   * The application start method that is executed if "java GServer" is
   * called. It just creates an instance of GServer.
   */
  public static void main(String argv[]) {
    Object GS=new GServer();
  }  

  /**
   * Constructor of class GServer. It creates a ServerSocket and starts
   * the server thread that waits for new clients.
   */
  GServer() {
    // Open the protocoll file
    try {
      out=new DataOutputStream(new FileOutputStream("GServProto.txt"));
    } catch (IOException e) { } 

    try { 
      // Create the server side socket
      listen_socket = new ServerSocket(port);
      System.out.println("Server started. Port: "+port+"\n");
      toLogFile("Server starts");
      // Create an instance of ChessSync for chess timer synchronization
      Object chesssync=new ChessSync(this);
    } catch (IOException e) { System.out.println(e); }

    // Start the server thread
    start();
  }
 
  /**
   * The server main method. It's an endless loop that waits for
   * new clients and then creates instances of Player.
   * This method is automatically called when the server thread is started.
   */
  public void run() {
    if (listen_socket!=null) try {
      for (;;) {
        // Waiting for new players: the method accept() only returns, if a new
        // client wants to connect; it then returns the client socket, so that
        // we can communcate with the client via the socket instance 
        Socket client_socket = listen_socket.accept();

        // Build a string that holds the new player id (4 chars)
        String IDStr = "" + freeID;
        freeID = (freeID>=9999? 1 : freeID+1);
        while (IDStr.length()<4)  IDStr = "0"+IDStr;

        // Construct a new Player instance; the id gets an "0000" for compatibility
        // with my multi-server application
        // The Player instance now communicates with the client (the player applet).
        // See at Player.java to get more informations about that.
        Player P = new Player("0000"+IDStr, client_socket, this);

        // Apppend the new Player instance to the player list
        PlayerList PLNew = new PlayerList(P);
        if (PL==null) { PL=PLNew; } else PL.append(PLNew);

        // Wait 5 milli seconds so that the application doesn't block the CPU
        // Afterwards go to the first step and repeat all
        try { Thread.sleep(5);
        } catch (InterruptedException e) {}
      }
    } catch(IOException e2) { 
      toLogFile("Server error: "+e2);
    }
  }

  /**
   * Appends the given string to the protocoll file.
   * @param log the string that has to be saved
   */
  public void toLogFile(String log) {
    // Get the current date and delete the year from it.
    String D=""+new Date();
    D=D.substring(0, D.length()-8)+D.substring(D.length()-4, D.length());

    // Save the date and the log string to the protocoll file
    if (out!=null) try { out.writeBytes(D + " - " + log+"\n");
    } catch (IOException e) { }
  }

  /**
   * Deletes the Player instance with the given id from the player list.
   * @param id the id of the player that must be deleted
   */
  public void delPlayer(String id) {
    // If the first player has the id, then set the player list start to
    // his successor
    if (PL!=null) if (id.equals(PL.P.id)) PL=PL.next;

    // Search the player list for a player with the given id;
    // his successor becomes the successor of his predecessor;
    // the rest of the Player instance will automatically deleted
    // because there is no more pointer that points to that instance 
    for (PlayerList Pl = PL; Pl != null; Pl = Pl.next) 
      if (Pl.next!=null) if (id.equals(Pl.next.P.id))  Pl.next=Pl.next.next;
  }
}

/**
 * The player list just holds all Player instances. It could
 * be replaced by a Vector, but the player list can easily be converted
 * to C++ so I anyway used this class.
 */
class PlayerList {
  Player P; // the Player instance that belongs to this list entry
  PlayerList next; // the successor

  /**
   * The constructor of the PlayerList class. Creates an instance that
   * holds the given Player instance.
   * @param player that should be holded by this list entry
   */
  PlayerList(Player player) {
    P=player;
  }

  /**
   * Appends another player list so that the two lists are connected.
   * @param PLNew the player list that has to be appended to this list
   */
  void append(PlayerList PLNew) {
    if (next==null) { next=PLNew; } else next.append(PLNew);
  }

  /**
   * Searchs for a player with the given id.
   * @param id the id of the player that has to be searched for
   */
  Player getPlayer(String id) {
    // Returns the Player instance of the list entry if it's the right;
    // otherwise return the search result of the list that is appended to this entry
    if (id.equals(P.id)) { return P; } else if (next!=null) return next.getPlayer(id);
    return null;
  }
}

/**
 * ChessSync is a thread that sends every second a timer message to
 * all connected chess players.
 */
class ChessSync extends Thread {
  GServer GS; // the GServer instance that belongs to this class

  /**
   * The constructor of the ChessSync class. Starts the synchronization
   * thread.
   */
  ChessSync(GServer gs) {
    GS=gs;
    start();
  }

  /**
   * The main method of ChessSync. Every second it sends a timer
   * message to all connected players. This method is automatically
   * called if the thread is started.
   */
  public void run() {
    for (;;) {
      // Go through the player list and sends all chess players a
      // timer message; add 32 to '#' because it's row game data
      // that is normally coded and decoded in Client.java
      for (PlayerList PL = GS.PL; PL != null; PL = PL.next) 
        if ("Chess".equals(PL.P.Game)) PL.P.sendString("D"+(char)((int)'#'+32));

      // Wait a second and repeat it
      try{ Thread.sleep(1000);
      } catch (InterruptedException e) {}
    }
  }

}



