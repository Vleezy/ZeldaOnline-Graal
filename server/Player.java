import java.io.*;
import java.net.*;
import java.lang.*;

/**
 * The Player class holds all informations about a single client; it
 * also communcates with the client and delivers transmissions between 
 * clients (e.g. between two chess players).
 * The communciation normally is similar to that:
 *   server                                          client
 *          <-- name + game 
 *              player id -->
 *              opponents names + ids -->
 *          <-- wished opponent id
 *   +->        transmissions from the opponents -->
 *   +--    <-- tranmissions to the opponents
 */
class Player extends Thread {
  // Constants for the current player state
  final int DONT=0; // the player cannot play yet
  final int WAITS=1; // the player waits for opponents
  final int MAX=2; // the player currently plays and cannot get more opponents
  final int OBSERV=3; // the player can observe other games
  final int SUPER=4; // the client is not a player himself, he only gets
                     // informations about other players

  String id, Game="", Name=""; // the id, game and name of the player
  int status=DONT; // current player state
  PlayerList enemies; // opponents list
  GServer GS; // the server instance that belongs to this player

  private Socket client; // the communcation socket to the client
  private DataInputStream in; // an input stream from the client
  private DataOutputStream out; // an output stream to the client
  private boolean busy=false; // tells if the socket is currently busy


  /**
   * The constructor of the player class. It creates input and output
   * streams to the client and starts the player thread.
   * @param id the player id
   * @param client_socket the socket to the client
   * @param GS the server instance that belongs to this player
   */
  public Player(String id, Socket client_socket, GServer GS) {
    this.id=id;
    this.GS=GS;
    client = client_socket;

    try {
      // Create input and output streams
      in = new DataInputStream(client.getInputStream());
      out = new DataOutputStream(client.getOutputStream());
      // Start the player thread
      setPriority(Thread.MAX_PRIORITY);
      start();

    } catch (IOException e) {
      // If an exception occured while creating streams, 
      // try to close the socket and delete this Player instance
      try { client.close();
      } catch (IOException e2) { }
      GS.delPlayer(id);
    }
  }

  /** 
   * The main method of the Player class. It tries to get datas from
   * the connected client and interpret them.
   */
  public void run() {
    try { 
      // Read datas from the input stream.
      // It stops when readLine returns null. This normally happens when
      // the client is disconnecting.
      for (String line; (line=in.readLine()) != null; ) {
//        System.out.println("Player "+id+" got: "+line);
        interpret(line);
      }
    } catch(IOException e) { 
    } finally  {
      // When the client is disconnected, tell all opponents that I'm gone
      closeConnections();
      for (PlayerList PL = GS.PL; PL != null; PL = PL.next)  PL.P.sendGonePlayer(id);

      // Write the disconnection to the protocoll file
      String G=Game;
      if (G.length()>4) G=G.substring(0,3)+".";
      GS.toLogFile(G+": "+id+": "+Name+" is gone"); 

      // Close the socket and delete this Player instance
      try { client.close();
      } catch(IOException e2) { }
      GS.delPlayer(id);
    }
  }

  /**
   * Sends datas to the connected client. 
   * @param data the string that holds the transmission data
   */
  public void sendString(String data) {
    // Wait until the socket is ready for transmissions
    // I'm using a boolean busy to avoid errors while sending;
    // this could also be done with the command 'synchronized',
    // but that sometimes produce errors...
    while (busy) try { Thread.sleep(5);
    } catch (InterruptedException e) { }

    // Send the data
    busy=true;
//    System.out.println("Player "+id+" sends: "+data);
    if (out != null) try { out.writeBytes(data + "\n") ; 
    } catch (IOException e) { }
    busy=false;
  }

// ************************************************************************

  /**
   * Interprets the current transmission. The transmission have an
   * own protocoll: the first char indicates the transmission type
   * (e.g. 'N' for game name + player name),
   * the chars that follow are the transmission data
   * (e.g. 'Chess#Stefan')
   * @param line the transmission that has to be interpreted
   */
  private void interpret(String line) {
    if (line.length()<1) return;

    String kind=line.substring(0,1);
    if (kind.equals("+")) {	
      // The player wants to be known as a free player who
      // is waiting for opponents; add the player to all opponent
      // lists
      toLists();
      return;
    }
    if (kind.equals("-")) {
      // The player wants to 'sleep' and doesn't want to get new enemies;
      // this command is normally sent if the player minimizes the game window; 
      // delete the player from all opponent lists
      delFromLists();
      return;
    }
    if (kind.equals("C")) {	
      // The player wants to be disconnected from his current game party;
      // close all connections to the opponents
      closeConnections(); 
      return;
    }
 
    if (line.length()<2) return;
    String data=line.substring(1);
    if (kind.equals("D")) { 	
      // Row game datas for the applets; send it to all opponents
      sendData(data);
      return;
    }

    if (kind.equals("N") || kind.equals("O") || kind.equals("S")) {
      // The player sent his game name and player name.
      // First give him his player id.
      sendID();
 
      // If the player wants to be a super user, just assign his name
      // and initialize his opponents list
      if (kind.equals("S")) {
        Name=data;
        status=SUPER;
        initialList();
        return;
      }

      // If the player is a normal player or an observer, assign his
      // game name and player name and initialize his opponents list.
      int cut=data.indexOf("#"); // game name and player name are divided by a '#'
      if (cut>0) {
        Game=data.substring(0, cut);
        if ((cut+1)>=data.length()) { Name="unknown"; } else Name=data.substring(cut+1);

        if (kind.equals("N")) { // the player is an normal user and here is his name
          status=WAITS;
          initialList();
        }else {                 // the player is now an "observer" and doesn't want to play
          status=OBSERV;
          initialList();
        }
      }
      return;
    }

    if (data.length()<8) return;
    String Pid=data.substring(0,8);
    if (kind.equals("V")) {	
      // The player wants to observe someone; the id of the player that
      // has to be observed was appended to this tranmission
      for (PlayerList PL = GS.PL; PL != null; PL = PL.next)  
        PL.P.wantToView(Pid, this);
      return;
    }
    if (kind.equals("P")) {	
      // The player wants to play against someone; the opponent id
      // was appended to this transmission
      playAgainst(Pid);
      return;
    }   
  }

// private methods *************************************************************

  /** 
   * Sends data to all opponents holded in the opponents list of this player.
   * @param data the data string that has to be sent
   */
  private void sendData(String data) {
    // Game data gets the id 'D'
    data = "D" + data;
    // Send the data to all currently opponents
    for (PlayerList PL = enemies; PL != null; PL = PL.next) PL.P.sendString(data);
  }

  /**
   * Sends the id to the  client.
   */
  private void sendID() {
    sendString("I" + id);
  }

  /**
   * Disconnects the player from the current game party.
   */
  private void closeConnections() {
    // Tell all opponents that I don't play anymore
    for (PlayerList PL = enemies; PL != null; PL = PL.next) PL.P.sendGoneEnemy(id);
    enemies=null;
    // Tell all players that I want get out of their output lists; tell the
    // client that the opponents are no longer our opponents
    for (PlayerList PL = GS.PL; PL != null; PL = PL.next) if (!id.equals(PL.P.id)) {
      PL.P.delOutput(id);
      sendGoneEnemy(PL.P.id);
    }
    // Now I'm ready for new game parties
    if (status==MAX) status=WAITS;
  }

  /**
   * Tells all players that a new player gets into the player list;
   * so that they can play against us.
   */
  private void initialList() {
    // Write it into the protocoll file
    String G=Game;
    if (G.length()>4) G=G.substring(0,3)+".";
    GS.toLogFile(G+": "+id+": "+Name+"("+client.getInetAddress()+") is connected");

    // Tell all players with the same game name, that we are waiting for 
    // opponents; also tell 'our' client all players names
    for (PlayerList PL = GS.PL; PL != null; PL = PL.next) if (!id.equals(PL.P.id)) {
      switch (status) {
        case WAITS:  if (Game.equals(PL.P.Game)) {
                       if (PL.P.status==WAITS) sendNewPlayer(PL.P.id, PL.P.Name);
                       if (PL.P.status==WAITS || PL.P.status==MAX);
                       PL.P.sendNewPlayer(id, Name);
                     }
                     break;
        case OBSERV: if (Game.equals(PL.P.Game) && PL.P.enemies!=null) 
                       sendNewPlayer(PL.P.id, PL.P.Name);
        case SUPER:    sendNewPlayer(PL.P.id, PL.P.Name + " (" + PL.P.Game + ")");	// get all names
      }
      if (PL.P.status==SUPER) PL.P.sendNewPlayer(id, Name + " (" + Game + ")");
    } 
  }

  /**
   * Deletes our name from all client player lists.
   */
  private void delFromLists() {
    if (status==WAITS) {
      status=MAX;
      for (PlayerList PL = GS.PL; PL != null; PL = PL.next) if (!id.equals(PL.P.id))
        switch (PL.P.status) {
          case WAITS:  
          case MAX:     if (Game.equals(PL.P.Game)) PL.P.sendGonePlayer(id); break;
          case OBSERV:if (PL!=null) { PL.P.sendNewPlayer(id, Name);
                                    } else PL.P.sendGonePlayer(id);
        }
    }
  }

  /**
   * Tells all players that we are back and want to get into their player lists.
   */
  private void toLists() {
    if (status==MAX || status==WAITS) {
      status=WAITS;
      for (PlayerList PL = GS.PL; PL != null; PL = PL.next) if (!id.equals(PL.P.id)) 
        switch (PL.P.status) {
          case WAITS:  
          case MAX:     if (Game.equals(PL.P.Game)) PL.P.sendNewPlayer(id, Name); break;
          case OBSERV:if (PL!=null) { PL.P.sendNewPlayer(id, Name);
                                    } else PL.P.sendGonePlayer(id);
        }
    }
  }

  /**
   * The player joins the game party of the given opponent.
   * @param Pid the id of the opponent we want to play against
   */
  private void playAgainst(String Pid) {
    // Fist disconnect from all current opponents
    closeConnections();

    // Find the player with an id = Pid; if he is waiting for
    // opponents, lets join his game party: connect to him and all
    // his opponents 
    if (status==WAITS && GS.PL!=null) {
      Player Enemy=GS.PL.getPlayer(Pid);
      if (Enemy!=null) if (Enemy.status==WAITS) {
        // Write it to the protocoll file
        String G=Game;
        if (G.length()>4) G=G.substring(0,3)+".";
        String log=G+": "+id+": "+Enemy.Name+"+";

        for (PlayerList PL = Enemy.enemies; PL != null; PL = PL.next) if (!id.equals(PL.P.id)) {
          if (PL.P.status!=OBSERV) PL.P.toOutput(this);
          sendNewEnemy(PL.P.id);
          toOutput(PL.P);
          PL.P.sendNewEnemy(id);
          log+=PL.P.Name+"+";
        }
        Enemy.toOutput(this);
        sendNewEnemy(Pid); 
        toOutput(Enemy); 
        Enemy.sendNewEnemy(id);
        GS.toLogFile(log+Name);
      }
    }
  }

// public methods ***********************************************************

  /**
   * Tells the client that there is a new player who wants to play.
   * @param id the id of the new player
   * @param Name the name of the new player
   */
  public void sendNewPlayer(String id, String Name) {
    sendString("+" + id + Name);
  }

  /**
   * Tells the client that the given player doesn't want to get
   * opponents anymore.
   * @param id the id of the player that wants to get out of the client player list
   */
  public void sendGonePlayer(String id) {
    sendString("-" + id);
  }

  /**
   * Append the given Player instance to out opponents list.
   * @param P the player that becomes our opponent
   */
  public void toOutput(Player P) {
    PlayerList PLNew = new PlayerList(P);
    if (enemies==null)  { enemies=PLNew; } else enemies.append(PLNew);
  }

  /**
   * Deletes the given opponent from our opponents list.
   * @param id the id of the player that has to be deleted
   */
  public void delOutput(String id) {
    if (enemies!=null) if (id.equals(enemies.P.id)) enemies=enemies.next;

    for (PlayerList PL = enemies; PL != null; PL = PL.next) 
      if (PL.next!=null) if (id.equals(PL.next.P.id))  PL.next=PL.next.next;
  }

  /**
   * Tells the client that a new player has joined the current game party.
   * @param id the id of the player that connected to us
   */
  public void sendNewEnemy(String id) {
    sendString("P" + id);
  }

  /**
   * Tells the client that a player was disconnected from the current
   * game party.
   * @param id the id of the player that was disconnected
   */
  public void sendGoneEnemy(String id) {
    sendString("C"+ id);
  }
 
  /**
   * Puts the given Player instance to our opponents list; that
   * player is not really our opponent, but he gets our output so that
   * he can view what we make
   * @param at the id of the player that has to be observed
   * @param who the player that wants to get the output
   */
  public void wantToView(String at, Player who) {
    // Put 'who' to our opponents list and to the opponents lists from all
    // our oppoents to that 'who' can view our moves and all moves from
    // our opponents.
    if (id.equals(at)) { toOutput(who);
    } else if (enemies!=null) if (enemies.getPlayer(at)!=null) toOutput(who);
  }
}


