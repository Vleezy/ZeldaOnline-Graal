/* Zelda Client for GServer (C) Stefan Knorr 1998
 * Based on JccVsd (C) Stephan Kulow
 */

import java.net.*;
import java.io.*;
import java.util.*;


class ZeldaClient extends Thread {	// normal (playing) Client
  public final int port=14801;
  private Socket socket;
  private DataOutputStream out;
  private DataInputStream in;  
  private boolean busy=false;

  public Vector players = new Vector();
  private ZeldaMain Applet;
  private String id;
  private long logintime = -1;
  boolean leader = true;
//  TestThread thread = new TestThread();

  ZeldaClient(ZeldaMain app, String host) {
    Applet = app;
    try {
      socket = new Socket(host, port);
      out = new DataOutputStream(socket.getOutputStream()); 
      in = new DataInputStream(socket.getInputStream());
      start();
    } catch (IOException e) {
      System.out.println(e);
      if (socket!=null) try { socket.close();
      } catch (IOException e2) { System.out.println(e2); }
      socket = null;
    }
  }

  public void run() {
    try {
      for (String line; (line=in.readLine()) != null; ) {
//        System.out.println("Client "+id+" got: "+line);
//        thread.counter+=line.length();
        interprete(line);
      }
    } catch(IOException e) { System.out.println(e);
    } finally  {
      try { socket.close();
      } catch(IOException e2) { System.out.println(e2); }
      socket=null;
    }
  }

  private void sendString(String data) {
    while (busy)
      try { Thread.sleep(5);
    } catch (InterruptedException e) { }

    busy=true;
//    System.out.println("Client "+id+" sends: " + data);
    if (out != null) try { out.writeBytes(data+"\n") ; 
    } catch (IOException e) {System.out.println(e);}
    busy=false;
  }

// ************************************************************************

  private void interprete(String line) {
    if (line.length()<2) return;
    String kind=line.substring(0,1);
    String data=line.substring(1);
    if (kind.equals("D")) { gotData(data); return; }

    if (data.length()<8) return;
    String Pid=data.substring(0,8);
    if (kind.equals("I")) { id=Pid; return; }
    if (kind.equals("P")) { NewEnemy(Pid); return; }
    if (kind.equals("C")) { GoneEnemy(Pid); return; }
    if (kind.equals("-")) { delPlayer(Pid); return; }

    if (data.length()<9) return;
    String name=data.substring(8); 
    if (kind.equals("+")) { addPlayer(Pid, name); return; }
  }

// private methods ***********************************************************

  private void NewEnemy(String Pid) {
    int i = getPlayerIndex(Pid);
    if (i<0) return;
    Vector vec = (Vector)players.elementAt(i); 
    boolean known = ((Boolean)vec.elementAt(8)).booleanValue(); 
    if (known) return;
    vec.setElementAt(new Boolean(true),8);

    sendLoginTime();
//    sendLevel();
    sendPos();
    sendChatText();
    sendNickName();
    sendSpriteColors();
    if (Applet.carrying) sendCarrySprite(Applet.carrysprite);
    Applet.updateClient();
  }
  private void GoneEnemy(String Pid) { 
    Applet.updateClient();
  }

  private void delPlayer(String Pid) {
    for (int i=0; i<players.size(); ) {
      Vector vec = (Vector)players.elementAt(i);
      String id = (String)vec.elementAt(0);
      if (id.equals(Pid)) { players.removeElementAt(i);
      } else i++;
    }
    calculateLeader();
  }

  private int getPlayerIndex(String Pid) {
    for (int i=0; i<players.size(); i++) {
      Vector vec = (Vector)players.elementAt(i);
      String id = (String)vec.elementAt(0);
      if (id.equals(Pid)) return i;
    }
    return -1;
  }
  
  private void addPlayer(String Pid, String name) {
    if (getPlayerIndex(Pid)>=0) return;
    Vector vec = new Vector();
    vec.addElement(Pid);                 // id
    vec.addElement("");                  // level filename
    vec.addElement(new Double(-1));      // x
    vec.addElement(new Double(-1));      // y
    vec.addElement(new Integer(-1));     // spritenum
    vec.addElement("");                  // chat text
    vec.addElement("");                  // nick name
    vec.addElement(new int[] {0,1,2,3}); // sprite color index array
    vec.addElement(new Boolean(false));	// already registered
    vec.addElement(new Long(-1));	// login time
    vec.addElement(new Integer(-1));	// carry sprite
    players.addElement(vec);
    if (players.size() == 1) playWith(Pid);
  }

  private void gotData(String data) {
    String idstr = ""+(int)(((data.charAt(0)-32)<<7)+(data.charAt(1)-32));
    while (idstr.length()<4) idstr = "0"+idstr;
    int index = getPlayerIndex("0000"+idstr);

    if (index<0) return;
    Vector vec = (Vector)players.elementAt(index);

    char kind = data.charAt(2);
    data = data.substring(3);
    switch (kind) {
//      case 'L': { vec.setElementAt(data,1); break; } // Level name
      case 'P': { // Pos
        try {
          vec.setElementAt(new Double(data.charAt(0)-32),2);
          vec.setElementAt(new Double(data.charAt(1)-32),3);
          vec.setElementAt(new Integer(data.charAt(2)-32),4);
        } catch (Exception e) { }
        break;
      }
      case 'C': { vec.setElementAt(data,5); break; } // Chat text
      case 'N': { vec.setElementAt(data,6); break; } // Name
      case 'S': { // Sprite colors
        int[] array = new int[Applet.colorindex.length];
        for (int i=0; i<array.length; i++) array[i] = data.charAt(i)-32;
        vec.setElementAt(array,7);
        break;
      }
      case 'M': { // Move computer opponent
        while (data.length()>=6) {
          int id = data.charAt(0)-32;
          double cx = ((double)data.charAt(1)-32)/2;
          double cy = ((double)data.charAt(2)-32)/2;
          int anicount = data.charAt(3)-32;
          int dir = (data.charAt(4)-32)>>2;
          int headdir = (data.charAt(4)-32)&0x3;
          int mode = data.charAt(5)-32;
          for (int j=0; j<Applet.compus.size(); j++) {
            Computer c = (Computer)Applet.compus.elementAt(j);
            if (c.id==id) { c.remoteMove(cx,cy,anicount,dir,headdir,mode); break; }
          }
          data = data.substring(6);
        }
        break;
      }
      case 'H': { // Hurt computer opponent
        int id = data.charAt(0)-32;
        double dx = ((double)data.charAt(1)-32-64)/16;
        double dy = ((double)data.charAt(2)-32-64)/16;
        int decpower = data.charAt(3)-32;
        for (int j=0; j<Applet.compus.size(); j++) {
          Computer c = (Computer)Applet.compus.elementAt(j);
          if (c.id==id) { c.hurt(true,false,0,0,c.y,dx,dy,decpower); break; }
        }
        break;
      }
      case 'B': { // Board modify
        int x = data.charAt(0)-32;
        int y = data.charAt(1)-32;
        int w = (data.charAt(2)-32)>>3;
        int h = (data.charAt(2)-32)&0x7;
        int[][] replace = new int[w][h];
        for (int i=0; i<w; i++) for (int j=0; j<h; j++) {
          int arrpos = 3+(j+i*h)*2;
          int arrfield = ((data.charAt(arrpos)-32)<<7)+(data.charAt(arrpos+1)-32);
          replace[i][j] = arrfield;
        }
        Applet.modifyBoard(x,y,replace,false);
        break;
      }
      case 'T': { // Login time
        long time = 0;
        for (int i=0; i<10; i++) {
          time<<=7;
          int test = (data.charAt(9-i)-32);
          time = time+(long)test;
        }
        vec.setElementAt(new Long(time),9);
        calculateLeader();
        if (leader) {
          sendAllBoardModifies();
          sendDeadCompus();
        }
        break;
      }
      case 'D': { // Dead computer opponent
        for (int i=0; i<data.length(); i++) {
          int id = data.charAt(i)-32;
          for (int j=0; j<Applet.compus.size(); j++) {
            Computer c = (Computer)Applet.compus.elementAt(j);
            if (c.id==id) { c.die(); break; }
          }
        }
        break;
      }
      case 'E': { // Extra
        double sx = ((double)data.charAt(0)-32)/2;
        double sy = ((double)data.charAt(1)-32)/2;
        int sprite = data.charAt(2)-32;
        Applet.putExtra(sx,sy,sprite,false);
        break;
      }
      case 'O': { 
        int oldspritenum = ((Integer)vec.elementAt(4)).intValue();
        vec.setElementAt(new Integer(18*4+oldspritenum%4),4);
        vec.setElementAt(new Integer(data.charAt(0)-32+261),10); break;
      } // Carry object
      case 'W': { // The carry sprite has been thrown
        int dir = data.charAt(0)-32;

        double x = ((Double)vec.elementAt(2)).doubleValue();
        double y = ((Double)vec.elementAt(3)).doubleValue();
        int carrysprite = ((Integer)vec.elementAt(10)).intValue();
        vec.setElementAt(new Integer(dir),4);
        if (carrysprite>=0) {
          Vector vec2 = new Vector();
          vec2.addElement(new Double(x+0.5));
          vec2.addElement(new Double(y-1.375));
          vec2.addElement(new Double(y+1));
          vec2.addElement(new Integer(carrysprite));
          vec2.addElement(new Integer(9));
          vec2.addElement(new Integer(dir));
          Applet.carries.addElement(vec2);
        }
        vec.setElementAt(new Integer(-1),10);
        break;
      }
      case 'A': { // Arrows
        double sx = ((double)data.charAt(0)-32)/2;
        double sy = ((double)data.charAt(1)-32)/2;
        int dir = data.charAt(2)-32;
        int sprite = data.charAt(3)-32;
        Applet.putFlyingObject(sprite,sx,sy,dir,1,0,false);
        break;
      }
    }
    Applet.updateClient();
  }

  String getCodedID() {
    try {
      String idstr = id;
      while (idstr.length()>1 && idstr.charAt(0)=='0') 
        idstr = idstr.substring(1);
      int i = Integer.parseInt(idstr);
      return ""+(char)((i>>7&0x7F)+32)+(char)((i&0x7F)+32);

    } catch (Exception e) { }
    return "  ";
  }

// public methods ************************************************************

  public void leaveLevelSession() {
    players.removeAllElements();
    disconnect();
    sendString("-");
  }
  public void enterLevelSession() {
    logintime = System.currentTimeMillis();
    leader = true;
    String lev = "Z"+Applet.curlevel.substring(0,Applet.curlevel.indexOf("."));
    sendString("N"+lev+"1.6#def");
  }
/*  public void sendLevel() { 
    if (players.size()==0) return; 
    sendString("D"+getCodedID()+"L"+Applet.curlevel); 
  }*/
  public void sendPos() {
    if (players.size()==0) return; 
    int px = (int)Applet.playerx, py = (int)Applet.playery;

    sendString("D"+getCodedID()+"P"+
      (char)(px+32)+(char)(py+32)+
      (char)((Applet.hurtcounter>0? 128 : Applet.spritenum[Applet.cursprite]*4)+
        Applet.playerdir+32)); 
  }
  public void sendChatText() {
    if (players.size()==0) return; 
    sendString("D"+getCodedID()+"C"+Applet.curchat); 
  }
  public void sendNickName() {
    if (players.size()==0) return; 
    sendString("D"+getCodedID()+"N"+Applet.nickname); 
  }
  public void sendSpriteColors() {
    if (players.size()==0) return; 
    String data = "D"+getCodedID()+"S";
    for (int i=0; i<Applet.curcolor.length; i++)
      data += (char)(Applet.curcolor[i]+32);
    sendString(data);
  }
  public void sendCompMove(String move) {
    if (players.size()==0) return;
    if (leader) sendString("D"+getCodedID()+"M"+move);
  }  
  public String getCompMoveString(int id, double x, double y, int anicount, int dir,
      int headdir, int mode) {
    int cx = (int)(x*2);
    int cy = (int)(y*2);
    anicount = Math.max(0,Math.min(anicount,200));
    dir = (dir<<2)+headdir;
    return ""+(char)(id+32)+(char)(cx+32)+(char)(cy+32)+(char)(anicount+32)+
      (char)(dir+32)+(char)(mode+32);
  }
  public void sendCompuHurt(int id, double dx, double dy, int decpower) {
    if (players.size()==0) return; 
    if (dx<-4) dx = -4;
    if (dy>4) dx = 4;
    if (dy<-4) dy = -4;
    if (dy>4) dy = 4;
    int x = (int)((dx+4)*16);
    int y = (int)((dy+4)*16);
    sendString("D"+getCodedID()+"H"+(char)(id+32)+(char)(x+32)+(char)(y+32)+
      (char)(decpower+32));
  }
  public void sendBoardModify(Vector vec) {
    if (players.size()==0) return; 
    int x = ((Integer)vec.elementAt(0)).intValue();
    int y = ((Integer)vec.elementAt(1)).intValue();
    int w = ((Integer)vec.elementAt(2)).intValue();
    int h = ((Integer)vec.elementAt(3)).intValue();
    String data = ""+(char)(x+32)+(char)(y+32)+(char)((w<<3)+h+32);
    for (int i=0; i<w*h; i++) {
      int index = ((Integer)vec.elementAt(4+i)).intValue();
      data = data + (char)((index>>7&0x7F)+32)+(char)((index&0x7F)+32);
    }
    sendString("D"+getCodedID()+"B"+data);
  }
  public void sendAllBoardModifies() {
    if (players.size()==0) return; 
    for (int i=0; i<Applet.boardmodifies.size(); i++)
      sendBoardModify((Vector)Applet.boardmodifies.elementAt(i));
  }
  public void sendLoginTime() {
    if (players.size()==0) return; 
    String data = "D"+getCodedID()+"T";
    long time = logintime;
    for (int i=0; i<10; i++, time=time>>7) {
      int test = (char)((time&0x7F)+32);
      data += (char)test;
    }
    sendString(data);
  }
  public void sendDeadCompus() {
    if (players.size()==0) return; 
    String data = "D"+getCodedID()+"D";
    for (int i=0; i<Applet.deadcompus.size(); i++)
      data += (char)(((Integer)Applet.deadcompus.elementAt(i)).intValue()+32);
    sendString(data);
  }
  public void sendExtra(double x, double y, int sprite) {
    if (players.size()==0) return; 
    int sx = (int)(x*2);
    int sy = (int)(y*2);
    sendString("D"+getCodedID()+"E"+(char)(sx+32)+(char)(sy+32)+(char)(sprite+32));
  }
  public void sendCarrySprite(int carrysprite) {
    if (players.size()==0) return; 
    sendString("D"+getCodedID()+"O"+(char)(carrysprite-261+32));
  }
  public void sendCarryThrow() {
    if (players.size()==0) return; 
    sendString("D"+getCodedID()+"W"+(char)(Applet.playerdir+32));
  }
  public void sendShot(double x, double y, int dir, int sprite) {
    if (players.size()==0) return; 
    int sx = (int)(x*2);
    int sy = (int)(y*2);
    sendString("D"+getCodedID()+"A"+(char)(sx+32)+(char)(sy+32)+(char)(dir+32)+(char)(sprite+32));        
  }
  public void calculateLeader() {
    long besttime = logintime;
    String bestid = id;
    for (int i=0; i<players.size(); i++) {
      Vector vec = (Vector)players.elementAt(i);
      String Pid = (String)vec.elementAt(0);
      long time = ((Long)vec.elementAt(9)).longValue();

      boolean better = false;
      if (time>=0) { 
        if (time<logintime || (time==logintime && Pid.compareTo(bestid)<0)) better = true;
      } else if (besttime<0 && Pid.compareTo(bestid)<0) better = true; 
      if (better) {
        besttime = time;
        bestid = Pid;
      }
    }
    leader = bestid.equals(id);
  }

  public void playWith(String Pid) { sendString("P" + Pid); }
  public void HighScoreEntry(int score) { sendString("H" + score); }
  public void disconnect() { sendString("C"); }
  public void Aktivate() { 
    sendString("+");
    enterLevelSession();
  } 
  public void Deaktivate() { 
    leaveLevelSession();
  }

}


/*class TestThread extends Thread {
  int counter = 0;

  TestThread() { start(); }

  public void run() {
    for (;;) {
      System.out.println(counter);
      counter = 0;
      try { sleep(1000); } catch (Exception e) { }
    }
  }

}*/
