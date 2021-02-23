import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class ZeldaMain extends Panel implements Runnable {

  // Game options
  String serverhost;
  String startlevel = "level1.zelda";
  Font big = new Font("TimesRoman",Font.BOLD,36);
  Font chat = new Font("TimesRoman",Font.BOLD,20);
  double boardx = 8, boardy = 24, playerx = 22.5, playery = 36, power = 3, maxpower = 3;
  int glovepower = 1, rubins = 0, darts = 5;
  static final double runw = 0.5;
  static final int stepw = 2, scrollwh = 18, scrollwv = 14;

  // Internal game variables

  int scale = 2, bw, w, h;
  Image offscreen1, offscreen2;
  Image[] img, utils, sprite, letters;
  Image state, startimg;
  byte[] offscr;
  byte[][] maps;
  MemoryImageSource misoffscr;
  ColorModel offscrcm;
  int[] board = new int[64*64];
  int[] nowall, swamp, chestfields, spritespos;
  int[][] jumpstone, chestreplace, movestone, spritevalues, hurtsprites, compusprites,
    bumpedsprites, compexplosing, swampsoldier;
  int[][][] bushobj, bushobjreplace, liftobj, liftobjreplace, leapsfly;
  int freestone = 0x248;

  static final int[] spritenum = {0, 4,5,4,3,2,1,2,3, 6,7,8,9,10, 11,12,13,11,12, 
    14,15,16,17, 23, 18, 19,20,19,18,21,22,21,18,  24};
  static final double[] jumphare = {0,1.2,2.4,3.6,4.5,5.4,6.2,6.9,7.4,7.8,8};

  int cursprite = 0, curswamp = 0, hurtcounter = 0, bumpcounter = -1, anicount, timercount = 0,
    pushcounter = 0, jumpcounter = 0, pullcounter = 0, liftcounter = 0, carrysprite, signy, signnum,
    signline;
  double hurtdx, hurtdy, bumppx, bumppy, carryx, carryy, carrybasey, jumporgx, jumporgy;
  static public final int UP = 0, LEFT = 1, DOWN = 2, RIGHT = 3, SLAY = 4, LIFT = 5, SHOOT = 6,
    PAUSE = 7;
  int playerdir = DOWN, carrydir;

  Vector links = new Vector(), leaps = new Vector(), compus = new Vector(), carries = new Vector(),
    flyingobjs = new Vector(), signs = new Vector(), extras = new Vector(), 
    specialobjs = new Vector(), boardmodifies = new Vector(), deadcompus = new Vector();
  boolean animated = false, firedown = false, carrying = false, showsign = false,
    dartshooting = false, canshot = false, havemap = false, foundbrother = false,
    clientpaint = false, showownname = false, showcolors = true, multiplayer;
  boolean[] chestopened = {false,false,false,false};
  Thread mythread;
  static final int STARTING = 0, STARTLOADING = 1, STARTSCREEN = 2, GAME = 3, LEVELLOADING = 4, 
    ENDING = 5, GAMEEND = 6, PAUSED = 7;
  int gamestate = STARTING;
  String status = "", curlevel = startlevel, curchat = "", nickname = "unknown";

  ZeldaClient client;
  TextField textf = new TextField();
  ZeldaWindow mapwindow;
  MapPanel mappanel;
  Button but = new Button("Options");

  // Initializer

  ZeldaMain(boolean multiplayer) {
    this.multiplayer = multiplayer;
  }

  public void init() {
    setBackground(Color.lightGray);
    scale = 2;
    bw = 8*scale;
    w = 32*bw;
    h = 28*bw;

    // Load the start image
    startimg = getToolkit().getImage("start.gif");
    MediaTracker tracker = new MediaTracker(this);
    tracker.addImage(startimg, 0);
    try { tracker.waitForID(0); } catch (InterruptedException e) { }

    paint(getGraphics());
    if (System.getProperty("java.version").startsWith("1.0")) {
      status="This game can only be played with Netscape4, IE4 or higher!"; 
      repaint();
      return;
    }

    // Setup layout
    setLayout(null);
    add(textf);
    textf.reshape(2,h+2,w-79,30);
    add(but);
    but.reshape(w-77,h+2,75,30);

    // Setup game variables and start the thread
    offscr = new byte[64*64*bw*bw];
    maps = new byte[128*16][bw*bw];
    mythread = new Thread(this);
    mythread.setPriority(Thread.MIN_PRIORITY);
    mythread.start();
    if (multiplayer && client==null)
      client = new ZeldaClient(this, serverhost);
  }

  private void initGame() {
    // Read level informations and prepare graphics
    initArrays();
    loadImages();
    loadStartlevel();
    requestFocus();
    status="Press any key to start!"; repaint();
    gamestate = STARTSCREEN;
  }

  private void loadStartlevel() {
    loadFromFile(startlevel);
    playerx = 22.5; playery = 36;
    moveBoard();
    paintOffscreen();
    for (int i=0; i<keypressed.length; i++) keypressed[i] = false;
    power = maxpower = 3;
    anicount = 0;
    playerdir = DOWN;
  }

// --- File operations ---

  private void loadImages() {
    // Read images
    Image pics = getToolkit().getImage("pics1.gif");
    Image link = getToolkit().getImage("link.gif");
    Image stateorg = getToolkit().getImage("state.gif");
    Image letterorg = getToolkit().getImage("buch.gif");

    // Wait until its loading is done
    MediaTracker tracker = new MediaTracker(this);
    int imgcount = 0;
    tracker.addImage(pics,imgcount++);
    tracker.addImage(link,imgcount++);
    tracker.addImage(letterorg,imgcount++);
    tracker.addImage(stateorg,imgcount++);
    for (int i=0; i<imgcount; i++) {
      status="Images to load: "+(imgcount-i); repaint();
      try { tracker.waitForID(i); } catch (InterruptedException e) { }
    }

    cropBoardImages(pics);
    cropSprites(link);
    cropLetters(letterorg);
    cropStateImage(stateorg);
    System.gc();
  }

  private void cropBoardImages(Image img) {
    // Crop all 8x8 blocks
    status="Cropping board images..."; repaint();

    PixelGrabber pg=new PixelGrabber(img,0,0,64*8,32*8,false);
    try { pg.grabPixels(); } catch (InterruptedException ex) { };
    byte[] map = (byte[])pg.getPixels();
    offscrcm = pg.getColorModel();

    for (int x=0; x<16; x++) for (int y=0; y<128; y++)
      for (int sx=0; sx<bw; sx++) for (int sy=0; sy<bw; sy++)
        maps[x+y*16][sx+sy*bw] = map[(x*8)+(sx/scale)+(y/32*16*8)+(y%32*64*8*8)+(sy/scale*64*8)];
  }


  // Internal game variables that hold informations about
  // the 8bit-ColorModel (for colored sprites)
  IndexColorModel icm;
  int icmsize;
  byte[] reds, greens, blues, alphas;
  byte[][] spritearrays;
  MemoryImageSource[] spritemis;
  int[] spritew;

  private void cropSprites(Image img) {
    status="Cropping sprites..."; repaint();

    PixelGrabber pg = new PixelGrabber(img,0,0,64,768,false);
    try { pg.grabPixels(); } catch (InterruptedException ex) { };
    byte[] map = (byte[])pg.getPixels();
    icm = (IndexColorModel)pg.getColorModel();
    icmsize = icm.getMapSize();
    reds = new byte[icmsize]; greens = new byte[icmsize]; blues = new byte[icmsize];
    alphas = new byte[icmsize];
    icm.getReds(reds); icm.getGreens(greens); icm.getBlues(blues); icm.getAlphas(alphas);

    sprite = new Image[spritespos.length];
    spritemis = new MemoryImageSource[sprite.length];
    spritearrays = new byte[sprite.length][];
    spritew = new int[sprite.length];
    for (int i=0; i<spritespos.length; i++) {
      int s = spritespos[i], x = s>>24, y = (s>>12)&0xFFF, w = (s>>6)&0x3F, h = s&0x3F;
      if (h>0) {
        Object[] objarr = cropScaledImage2(w,h,icm,map,x+y*64,64);

        sprite[i] = (Image)objarr[0];
        spritemis[i] = (MemoryImageSource)objarr[1];
        spritemis[i].setAnimated(true);
        spritearrays[i] = (byte[])objarr[2];
        spritew[i] = w*scale;
      }
    }
  }

  private IndexColorModel replaceColors(int set, byte[] reds, byte[] greens, byte[] blues,
      byte[] alphas, int size) {
    int[][] cols = {{0xFFDE6363,0xFFB594FF,0xFF5273CE},{0xFFF7DE42,0xFF8CDE31,0xFF009C39},
      {0xFFF0D840,0xFFF87800,0xFFC01820}};
    int[] index = {56,43,17};

    for (int i=0; i<3; i++) {
      reds[index[i]] = (byte)((cols[set][i] >> 16) & 0xFF);
      greens[index[i]] = (byte)((cols[set][i] >> 8) & 0xFF);
      blues[index[i]] = (byte)(cols[set][i] & 0xFF);
    }
    return new IndexColorModel(8,size,reds,greens,blues,alphas);
  }

  private void cropLetters(Image img) {
    // Crop letters
    status="Cropping letter images..."; repaint();

    PixelGrabber pg=new PixelGrabber(img,0,0,128,112,false);
    try { pg.grabPixels(); } catch (InterruptedException ex) { };
    byte[] map = (byte[])pg.getPixels();
    ColorModel cm = pg.getColorModel();

    letters = new Image[104];
    for (int i=0; i<letters.length; i++)
      letters[i] = cropScaledImage(8,16,cm,map,i%16*8 + i/16*16*128,128);
  }

  private void cropStateImage(Image img) {
    int sw = img.getWidth(this), sh = img.getHeight(this);

    PixelGrabber pg = new PixelGrabber(img,0,0,sw,sh,false);
    try { pg.grabPixels(); } catch (InterruptedException ex) { };
    byte[] map = (byte[])pg.getPixels();
    ColorModel cm = pg.getColorModel();

    state = cropScaledImage(sw,sh,cm,map,0,sw);
  }


  private Image cropScaledImage(int w, int h, ColorModel cm, byte[] map, int offset, int scan) {
    return (Image)cropScaledImage2(w,h,cm,map,offset,scan)[0];
  }

  private Object[] cropScaledImage2(int w, int h, ColorModel cm, byte[] map, int offset, int scan) {
    byte[] mapnew = new byte[w*scale*h*scale]; 
    for (int i=0; i<w*scale; i++) for (int j=0; j<h*scale; j++)
      mapnew[i+j*w*scale] = map[offset+i/scale+j/scale*scan];

    MemoryImageSource mis = new MemoryImageSource(w*scale,h*scale,cm,mapnew,0,w*scale);
    return new Object[] {createImage(mis),mis,mapnew};
  }

  private static final String pcode = 
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz" +
    "0123456789!?-.,#>()#####\"####'####### <####|\n";
  private static final String ccode = "ABXYudlrhxyz1234."; 
  private static final int[][] ctab = {
    {91},{92},{93},{94},{77},{78},{79},{80},{74,75},{71},{72},{73},
    {82,83},{84,83},{85,86},{87,88},{67}};

  public void loadFromFile(String filename) {
    DataInputStream in = null;

    status="Loading level informations..."; repaint();
    try {
      in = new DataInputStream(new FileInputStream(filename));

      byte[] b = new byte[8];
      in.read(b);
      String id = new String(b,0);
      if (id.equals("Z3-V1.02") || id.equals("Z3-V1.03")) {
        // Read the level file using RLE decoding
        int bitsread = 0, buffer = 0, count = 1, code = 0, codecount = 0,
          codebits = (id.equals("Z3-V1.02")? 11:12), firstcode = -1;
        boolean twicerepeat = false;
        while (codecount<64*64) {
          while (bitsread<codebits) {
            buffer += (in.read() << bitsread);
            bitsread += 8;
          }
          code = buffer & ((1 << codebits) - 1);
          buffer >>= codebits;
          bitsread -= codebits;

          if ((code & (1 << (codebits-1))) > 0) {
            if ((code & 0x100)>0) twicerepeat = true;
            count = (code & 0xFF);
          } else if (count==1) { board[codecount++] = code;
          } else {
            if (twicerepeat) {
              if (firstcode < 0) { firstcode = code;
              } else {
                for (int i=0; i<count; i++) {
                  board[codecount++] = firstcode;
                  board[codecount++] = code;
                }
                firstcode = -1;
                twicerepeat = false;
                count = 1;
              }
            } else {
              for (int i=0; i<count; i++) board[codecount++] = code;
              count = 1;
            }
          }
        }
        // Read links & opponents
        clearAllVectors();
        for (String line; (line=in.readLine())!=null; )
          if (line.equals("#")) { break;
          } else links.addElement(new FileLink(line));
        int compcount = 0;
        for (b=new byte[4]; (in.read(b))>=0; ) {
          if (b[0]<0) break;
          compus.addElement(new Computer(this,b[0],b[1],b[2],compcount++));
        }
        for (String line; (line=in.readLine())!=null; ) {
          int signx = line.charAt(0)-32;
          int signy = line.charAt(1)-32;
          String codetext = line.substring(2);
          int[] signcode = new int[codetext.length()];
          for (int i=0; i<codetext.length(); i++)
            signcode[i] = codetext.charAt(i)-32;

          Vector signvec = new Vector();
          signvec.addElement(new Integer(signx));
          signvec.addElement(new Integer(signy));
          signvec.addElement(signcode);
          signs.addElement(signvec);
        }
        in.close();
        curlevel = filename; 
        specialStoryAction();
      } else System.out.println("Unsupported file type!");
      
    } catch (Exception e) { e.printStackTrace();
    } finally { 
      if (in != null) try { in.close();
      } catch (IOException e2) { }
    }
  }

  private void clearAllVectors() {
    links.removeAllElements();
    compus.removeAllElements();
    flyingobjs.removeAllElements();
    leaps.removeAllElements();
    signs.removeAllElements();
    extras.removeAllElements();
    specialobjs.removeAllElements();
    boardmodifies.removeAllElements();
    deadcompus.removeAllElements();
  }

  private void specialStoryAction() {
    int repx = -1, repy = -1;
    if (curlevel.startsWith("level13.")) {
      if (glovepower<2) setSpecialObject(146,31,44.5,false);
      if (foundbrother && glovepower<2) setSpecialObject(146,26,35.5,false);
    } else if (curlevel.startsWith("level28.")) {
      if (!foundbrother) setSpecialObject(146,49,40.5,false);
    } else if (curlevel.startsWith("level22.")) {
      if (!canshot) setSpecialObject(147,55,22,false);
    } else if (curlevel.startsWith("cave2.")) {
      setSpecialObject(148,23,26,false);
    } else if (curlevel.startsWith("village1.")) {
      setSpecialObject(158,44,50,true);
    } else if (curlevel.startsWith("level25.")) {
      setSpecialObject(158,33,54,true);
    } else if (curlevel.startsWith("level21.")) {
      if (canshot) { repx=12; repy=32; }
    }

    String[] chestlevs = {"level24","village3","level16","level29"};
    int[][] newrep = {{30,14},{13,8},{4,48},{42,21}};
    for (int i=0; i<4; i++) if (curlevel.startsWith(chestlevs[i]+".") && chestopened[i]) {
      repx = newrep[i][0]; repy = newrep[i][1];
    }

    if (repx>=0) {
      for (int x=0; x<2; x++) for (int y=0; y<2; y++) board[repx+x+(repy+y)*64] = chestreplace[x][y];
      paintOffscreen(repx,repy,2,2);
    }
  }

  private void initArrays() {
    try {
      DataInputStream in = new DataInputStream(new FileInputStream("arrays.dat"));

      nowall = (int[])getArray(in);
      swamp = (int[])getArray(in);
      jumpstone = (int[][])getArray(in);
      bushobj = (int[][][])getArray(in);
      bushobjreplace = (int[][][])getArray(in);
      liftobj = (int[][][])getArray(in);
      liftobjreplace = (int[][][])getArray(in);
      chestfields = (int[])getArray(in);
      chestreplace = (int[][])getArray(in);
      movestone = (int[][])getArray(in);
      spritevalues = (int[][])getArray(in);
      hurtsprites = (int[][])getArray(in);
      leapsfly = (int[][][])getArray(in);
      compusprites = (int[][])getArray(in);
      bumpedsprites = (int[][])getArray(in);
      compexplosing = (int[][])getArray(in);
      swampsoldier = (int[][])getArray(in);
      spritespos = (int[])getArray(in);

      in.close();
    } catch (Exception e) { e.printStackTrace(); }
  }

  private Object getArray(DataInputStream in) throws IOException {
    int header = in.readInt(), type = header>>16, count = header&0xFFFF;
    switch (type) {
      case 0: {
        int[] arr = new int[count];
        for (int i=0; i<count; i++) arr[i] = in.readInt();
        return arr;
      }
      case 1: {
        Object[] arr1 = new Object[count];
        for (int i=0; i<count; i++) arr1[i] = getArray(in);
        if (arr1[0] instanceof int[]) {
          int[][] arr = new int[count][];
          for (int i=0; i<count; i++) arr[i] = (int[])arr1[i];
          return arr;
        } else {
          int[][][] arr = new int[count][][];
          for (int i=0; i<count; i++) arr[i] = (int[][])arr1[i];
          return arr;
        } 
      }
    }
    return null;
  }


// --- Paint operations ---

  public void update(Graphics g) {
    if (offscreen2 == null) offscreen2 = createImage(w, h);
    Graphics og = offscreen2.getGraphics();
    paint(og);
    g.create(0,0,w,h).drawImage(offscreen2, 0, 0, null);
  }

  public synchronized void paint(Graphics g) {
    if (gamestate<3) { drawStartscreen(g); return; }
    if (gamestate==ENDING) {
      g.setColor(Color.black);
      g.fillRect(0,0,w,h);
      if (anicount>=28) {
        g.setColor(Color.white);
        g.drawString(status,60*scale,190*scale);
      }
    } else g.drawImage(offscreen1, (int)(-boardx*bw), (int)(-boardy*bw), null); // Copy the background into the double buffer
    drawExtras(g);
    drawSpecialObjects(g, false);
    drawLeaps(g);
    drawCompus(g);
    drawOtherClients(g);
    drawPlayer(g);
    drawThrownObjects(g);
    if (bumpcounter>=0) {
      int sp = bumpedsprites[playerdir][3-Math.min(3, bumpcounter)];
      g.drawImage(sprite[sp>>16&0xFFFF], (int)((bumppx-boardx)*bw)+(byte)(sp>>8&0xFF)*scale, 
        (int)((bumppy-boardy)*bw)+(byte)(sp&0xFF)*scale, null);
    }
    drawFlyingObjects(g);
    drawSpecialObjects(g,true);
    if (showsign) drawSign(g);
    if (gamestate==LEVELLOADING || gamestate==PAUSED) {
      String str = (gamestate==PAUSED? "paused" : "loading ...");
      g.setFont(big);
      int sw = getFontMetrics(big).stringWidth(str);
      g.setColor(Color.black);
      g.drawString(str,(w-sw)/2+5,h/2);
      g.setColor(Color.white);
      g.drawString(str,(w-sw)/2+3,h/2-2);
    }
    drawState(g);
  }

  private void drawStartscreen(Graphics g) {
    g.setColor(Color.black);
    g.fillRect(0,0,w,h);
    g.drawImage(startimg,50*scale,38*scale,startimg.getWidth(this)*scale,startimg.getHeight(this)*scale,this);
    g.setColor(Color.white);
    g.drawString(status,60*scale,190*scale);
  }

  private void drawExtras(Graphics g) {
    for (int i=0; i<extras.size(); i++) {
      Vector vec = (Vector)extras.elementAt(i);
      int esprite = ((Integer)vec.elementAt(0)).intValue();
      double ex = ((Double)vec.elementAt(1)).doubleValue();
      double ey = ((Double)vec.elementAt(2)).doubleValue();
      int ecounter = ((Integer)vec.elementAt(3)).intValue();

      if (ecounter<128 || ecounter%2==0) {
        int eix = (int)((ex-boardx)*bw);
        int eiy = (int)((ey-boardy)*bw);
        int imgw = sprite[esprite].getWidth(this);
        g.drawImage(sprite[esprite], eix+8*scale-imgw/2, eiy+1*scale, null);
      }
    }
  }

  private void drawSpecialObjects(Graphics g, boolean overplayer) {
    for (int i=0; i<specialobjs.size(); i++) {
      Vector vec = (Vector)specialobjs.elementAt(i);
      int esprite = ((Integer)vec.elementAt(0)).intValue();
      double ex = ((Double)vec.elementAt(1)).doubleValue();
      double ey = ((Double)vec.elementAt(2)).doubleValue();
      boolean eover = ((Boolean)vec.elementAt(3)).booleanValue();
      if (eover==overplayer) {
        int eix = (int)((ex-boardx)*bw);
        int eiy = (int)((ey-boardy)*bw);
        g.drawImage(sprite[esprite], eix, eiy, null);
      }
    }
  }

  private void drawLeaps(Graphics g) {
    // Drawing flying leaps
    for (int i=0; i<leaps.size(); i++) {
      Vector vec = (Vector)leaps.elementAt(i);
      int count = ((Integer)vec.elementAt(0)).intValue();
      int lx = ((Integer)vec.elementAt(1)).intValue();
      int ly = ((Integer)vec.elementAt(2)).intValue();
      int type = ((Integer)vec.elementAt(3)).intValue();
      int[] s = leapsfly[type][count];
      for (int j=0; j<s.length; j++)
        g.drawImage(sprite[s[j]>>16&0xFFFF], (int)((lx-boardx)*bw)+(byte)(s[j]>>8&0xFF)*scale, 
          (int)((ly-boardy)*bw)+(byte)(s[j]&0xFF)*scale, null);
    }
  }

  private void drawCompus(Graphics g) {
    // Drawing computer opponents
    for (int j=0; j<compus.size(); j++) {
      Computer c = (Computer)compus.elementAt(j);
      int cx = (int)((c.x-boardx)*bw);
      int cy = (int)((c.y-boardy)*bw);
      if (c.mode!=Computer.DIE || c.anicount>=4) {

        int cblock = c.getColorBlock();
        int[] s = null;

        if (c.type==Computer.SWAMPSOLDIER) {
          if (c.anicount<=10) {
          } else if (c.anicount<=16 || c.anicount>44) {
            s = swampsoldier[(c.anicount-11)%2];
          } else if (c.anicount<=21) {
            s = swampsoldier[2];
          } else if (c.anicount<=32) {
            int[] s2 = compusprites[4*c.spriteset+c.dir];
            s = new int[s2.length];
            for (int i=0; i<s.length-1; i++) s[i] = s2[i+1];
            s[s.length-1] = swampsoldier[0][0];
          } else if (c.anicount<=34) {
            s = swampsoldier[2];
          } else if (c.anicount<=36) {
            s = swampsoldier[3];
          }          
        } else if (c.type==Computer.JUMPINGHARE) {
          if (c.mode!=Computer.DIE) {
            if (c.anicount<=10) {
            } else if (c.anicount<=16 || c.anicount>37) {
              s = new int[] {((22+(c.anicount%2))<<16)+(0<<8)+8};
            } else {
              s = new int[] {(209<<16)+(0<<8)+((int)(-jumphare[10-Math.abs(c.anicount-27)]*8)&0xFF),
                (22<<16)+(0<<8)+8};
            }   
          } 
        } else {
          s = compusprites[4*c.spriteset+c.dir];
        }

        if (s!=null) for (int i=0; i<s.length; i++) {
          int spritenum = s[i]>>16&0xFFFF;
          if (spritenum>=48 && spritenum<52) spritenum = 48+c.headdir;
          if (spritenum>=48 && spritenum<64) {
            icm = replaceColors(cblock, reds, greens, blues, alphas, icmsize);
            spritemis[spritenum].newPixels(spritearrays[spritenum],icm,0,spritew[spritenum]);
          }
          if (c.shooting && spritenum>=107 && spritenum<111) continue;
          g.drawImage(sprite[spritenum], cx+(byte)(s[i]>>8&0xFF)*scale, 
            cy+(byte)(s[i]&0xFF)*scale, null);
        }
      }
      if (c.mode==Computer.DIE && c.anicount<10 && c.anicount>=0) {
        int[] s = compexplosing[4-c.anicount/2];
        for (int i=0; i<s.length; i++)
          g.drawImage(sprite[s[i]>>16&0xFFFF], cx+(byte)(s[i]>>8&0xFF)*scale, 
            cy+(byte)(s[i]&0xFF)*scale, null);
      }
    }
  }

  int[] colorindex = {26,20,10,15};
  int[] curcolor = {0,1,2,3};
  int[] allcolors = {0xFF7BBD29,0xFF5A9C18,0xFF399c6B,0xFF4ADE7B,0xFF000000,
    0xFFC8A8F8,0xFF9878D8,0xFFB81020,0xFFF05888,
    0xFFFFFFFF,0xFFC0C0C0,0xFF808080,0xFF8080FF,0xFF0000FF,
    0xFF00FFFF,0xFFFFFF00,0xFFFFC080,0xFF800000};

  public void setPlayerColors(int[] colors) {
    for (int i=0; i<colorindex.length; i++) {
      reds[colorindex[i]] = (byte)((allcolors[colors[i]] >> 16) & 0xFF);
      greens[colorindex[i]] = (byte)((allcolors[colors[i]] >> 8) & 0xFF);
      blues[colorindex[i]] = (byte)(allcolors[colors[i]] & 0xFF);
    }
    icm = new IndexColorModel(8,icmsize,reds,greens,blues,alphas);
  }

  private void drawPlayer(Graphics g) {
    if (showcolors) setPlayerColors(curcolor);

    if (liftcounter>0 && playerdir==UP) {
      int cx = (int)((carryx-boardx)*bw);
      int cy = (int)((carryy-boardy)*bw);
      int basey = (int)((carrybasey-boardy)*bw);
      g.drawImage(sprite[0], cx+2*scale, basey+10*scale, null);
      g.drawImage(sprite[carrysprite], cx, cy, null);
    }

    int px = (int)((playerx-boardx)*bw), oldpx = px;
    int py = (int)((playery-boardy)*bw), oldpy = py;
    boolean drawswamp = (jumpcounter>0 || gamestate==ENDING? false : isInSwamp()>=0);
    if (gamestate==ENDING && anicount>=28) {
      if (showcolors) spritemis[157].newPixels(spritearrays[157],icm,0,spritew[157]);
      g.drawImage(sprite[157], px, py+8*scale, null);
    } else if (hurtcounter<=30) {
      int[] s = spritevalues[playerdir+4*spritenum[cursprite]];
      int spritecount = ((hurtcounter&1)>0? 1 : s.length);
      for (int i=0; i<spritecount; i++) {
        int snum = s[i]>>16&0xFFFF;
        if (drawswamp && snum>=5 && snum<=21 && i>=3) {
          g.drawImage(sprite[curswamp+(isInSwamp()>=8? 150 : 22)], px+4*scale, py+16*scale, null);
          drawswamp = false;
        }
        if (jumpcounter>1 && snum==0) {
          snum = 42;
          double[] jumpbasedeltay = {1,1.5,2,2.5,3,3,3,3};
          switch (playerdir) {
            case UP: { py = (int)((jumporgy-boardy-(double)(9-jumpcounter)/8*5)*bw); break; }
            case DOWN: { py = (int)((jumporgy-boardy+7)*bw); break; }
            case LEFT:
            case RIGHT: { py = (int)((jumporgy-boardy+jumpbasedeltay[8-jumpcounter])*bw); break; }
          }
        }
        if (showcolors) spritemis[snum].newPixels(spritearrays[snum],icm,0,spritew[snum]);
        g.drawImage(sprite[snum], px+(byte)(s[i]>>8&0xFF)*scale, 
          py+(byte)(s[i]&0xFF)*scale, null);
        if (jumpcounter>1) { px = oldpx; py = oldpy; }
      }
    } else {
      int[] s = hurtsprites[playerdir];
      int spritecount = ((hurtcounter&1)>0 && hurtcounter<35? 1 : s.length);
      for (int i=0; i<spritecount; i++) {
        int snum = s[i]>>16&0xFFFF;
        if (showcolors) spritemis[snum].newPixels(spritearrays[snum],icm,0,spritew[snum]);
        g.drawImage(sprite[snum], px+(byte)(s[i]>>8&0xFF)*scale, 
          py+(byte)(s[i]&0xFF)*scale, null);
      }
    }
    if (drawswamp) g.drawImage(sprite[curswamp+(isInSwamp()>=8? 150 : 22)], px+4*scale, py+16*scale, null);

    if (carrying || (liftcounter>0 && playerdir!=UP)) {
      int cx = (int)((carryx-boardx)*bw);
      int cy = (int)((carryy-boardy)*bw);
      if (!carrying) {
        int basey = (int)((carrybasey-boardy)*bw);
        g.drawImage(sprite[0], cx+2*scale, basey+10*scale, null);
      }
      g.drawImage(sprite[carrysprite], cx, cy, null);
    }
    if (curchat.length()>0) drawChatString(g,curchat,px+12*scale,py);
    if (showownname && nickname.length()>0) drawChatString(g,nickname,px+12*scale,py+3*bw);
  }

  private void drawOtherClients(Graphics g) {
    if (client==null) return;
    for (int i=0; i<client.players.size(); i++) {
      Vector vec = (Vector)client.players.elementAt(i);
//      String clevel = (String)vec.elementAt(1);
//      if (clevel.equals(curlevel)) {
        double cx = ((Double)vec.elementAt(2)).doubleValue();
        double cy = ((Double)vec.elementAt(3)).doubleValue();
        int csprite = ((Integer)vec.elementAt(4)).intValue();
        if (csprite<0) csprite=0;
        String ctext = (String)vec.elementAt(5);
        String cnick = (String)vec.elementAt(6);
        int[] ccolors = (int[])vec.elementAt(7);
        if (showcolors) setPlayerColors(ccolors);
        int carrysprite = ((Integer)vec.elementAt(10)).intValue();

        int[] s = (csprite>=128? hurtsprites[csprite-128] : spritevalues[csprite]);
        int px = (int)((cx-boardx)*bw);
        int py = (int)((cy-boardy)*bw);
        for (int j=0; j<s.length; j++) {
          int snum = s[j]>>16&0xFFFF;
          if (showcolors) spritemis[snum].newPixels(spritearrays[snum],icm,0,spritew[snum]);
          g.drawImage(sprite[snum], px+(byte)(s[j]>>8&0xFF)*scale, 
            py+(byte)(s[j]&0xFF)*scale, null);
        }
        if (carrysprite>=0)
          g.drawImage(sprite[carrysprite],px+(int)(0.5*bw),py-(int)(1.375*bw),null);
        if (ctext.length()>0) drawChatString(g,ctext,px+12*scale,py);
        if (cnick.length()>0 && !cnick.equals("unknown"))
          drawChatString(g,cnick,px+12*scale,py+3*bw);
//      }
    }
  }

  private void drawChatString(Graphics g, String text, int mx, int y) {
    g.setFont(chat);
    FontMetrics fm = getFontMetrics(g.getFont());
    int strw = fm.stringWidth(text);
    g.setColor(Color.black);
    g.drawString(text,mx-strw/2,y); 
    g.setColor(Color.white);
    g.drawString(text,mx-strw/2+1,y+1); 
  }

  private void drawThrownObjects(Graphics g) {
    for (int i=0; i<carries.size(); i++) {
      Vector vec = (Vector)carries.elementAt(i);
      double cx = ((Double)vec.elementAt(0)).doubleValue();
      double cy = ((Double)vec.elementAt(1)).doubleValue();
      double basey = ((Double)vec.elementAt(2)).doubleValue();
      int csprite = ((Integer)vec.elementAt(3)).intValue();

      int cix = (int)((cx-boardx)*bw);
      int ciy = (int)((cy-boardy)*bw);
      int baseiy = (int)((basey-boardy)*bw);
      g.drawImage(sprite[0], cix+2*scale, baseiy+10*scale, null);
      g.drawImage(sprite[csprite], cix, ciy, null);
    }
  }

  private void drawFlyingObjects(Graphics g) {
    for (int i=0; i<flyingobjs.size(); i++) {
      Vector vec = (Vector)flyingobjs.elementAt(i);
      int csprite = ((Integer)vec.elementAt(0)).intValue();
      double cx = ((Double)vec.elementAt(1)).doubleValue();
      double cy = ((Double)vec.elementAt(2)).doubleValue();

      int cix = (int)((cx-boardx)*bw);
      int ciy = (int)((cy-boardy)*bw);
      g.drawImage(sprite[csprite], cix, ciy, null);
    }
  }

  private void drawSign(Graphics g) {
    int top = signy*bw, bottom = (signy+8)*bw, left = 4*bw, right = (32-4)*bw;
    g.drawImage(letters[96],left,top,null);
    g.drawImage(letters[97],right-bw,top,null);
    g.drawImage(letters[98],left,bottom-2*bw,null);
    g.drawImage(letters[99],right-bw,bottom-2*bw,null);
    for (int i=5; i<32-5; i++) g.drawImage(letters[100],i*bw,top,null);
    for (int i=5; i<32-5; i++) g.drawImage(letters[101],i*bw,bottom-2*bw,null);
    for (int i=1; i<3; i++) g.drawImage(letters[102],left,top+i*2*bw,null);
    for (int i=1; i<3; i++) g.drawImage(letters[103],right-bw,top+i*2*bw,null);

    int[] letterwidth = {
      6,6,6,6,6,6,6,6,3,6,6,6,7,6,6,6,6,6,6,7,6,7,7,7,7,6,6,6,6,6,6,6,6,6,3,5,6,
      3,7,6,6,6,6,5,6,6,6,7,7,7,7,6,6,4,6,6,6,6,6,6,6,6,3,7,6,4,4,6,6,6,6,6,6,6,
      8,8,5,7,7,7,7,4,8,8,8,8,8,8,8,4,6,8,8,8,8,6
      };
    Vector signvec = (Vector)signs.elementAt(signnum);
    int[] signcode = (int[])signvec.elementAt(2);
    int leftx = 5*bw, curx = leftx, cury = (signy+1)*bw, lines = 1;
    for (int i=0; i<signcode.length; i++) {
      if (signcode[i]==96) { 
        lines++;
        if (lines>=signline+3) { break;
        } else if (lines>signline) { 
          cury += 16*scale;
          curx = leftx;
        }
      } else if (lines>=signline) {

        g.drawImage(letters[signcode[i]],curx,cury,null);
        curx += letterwidth[signcode[i]]*scale;
      }
    }
  }

  private void drawState(Graphics g) {
    g.drawImage(state,20*scale,12*scale,null);
    for (int i=0; i<maxpower; i++) {
      int spritenum = (i>=power? 142 : (i<=power-1? 140 : 141));
      g.drawImage(sprite[spritenum],(161+i*8)*scale,24*scale,null);
    }
    int[] rub = {rubins/100,(rubins/10)%10,rubins%10};
    for (int i=0; i<3; i++) g.drawImage(sprite[268+rub[i]],(65+8*i)*scale,24*scale,null);
    int[] dar = {darts/10,darts%10};
    for (int i=0; i<2; i++) g.drawImage(sprite[268+dar[i]],(121+8*i)*scale,24*scale,null);
    if (canshot) g.drawImage(sprite[239],40*scale,23*scale,null);
  }


  public void paintOffscreen() { paintOffscreen(0,0,64,64); } 
  public void paintOffscreen(int px, int py, int pw, int ph) {
    for (int y=py; y<py+ph; y++) {
      for (int x=px; x<px+pw; x++) {
        byte[] map;
        int num = board[x+y*64], nx = num%16, ny = num/16;
//        if (ny>=64) {
//          byte[] map2 = maps[(15-nx)+((3-ny/32)*32+ny%32)*16];
//          map = new byte[bw*bw];
//          for (int i=0; i<bw*bw; i++) map[i] = map2[(bw-1-i%bw)+i/bw*bw];
//        } else
        map = maps[num];
        int tooff = x*bw+y*bw*64*bw, fromoff = 0, yadd = 64*bw; 
        for (int sy=0; sy<bw; sy++,fromoff+=bw,tooff+=yadd)
          System.arraycopy(map,fromoff,offscr,tooff,bw);
      }
    }
    if (misoffscr==null) {
      misoffscr = new MemoryImageSource(64*bw,64*bw,offscrcm,offscr,0,64*bw);
      misoffscr.setAnimated(true);
      offscreen1 = createImage(misoffscr);
    } else misoffscr.newPixels(px*bw,py*bw,pw*bw,ph*bw);
  }

// --- Object testing 

  public boolean isOnPerson(double xf, double yf) {
    for (int i=0; i<specialobjs.size(); i++) {
      Vector vec = (Vector)specialobjs.elementAt(i);
      int esprite = ((Integer)vec.elementAt(0)).intValue();
      double ex = ((Double)vec.elementAt(1)).doubleValue();
      double ey = ((Double)vec.elementAt(2)).doubleValue();
      boolean eover = ((Boolean)vec.elementAt(3)).booleanValue();
      if (!eover) {
        double imgw = (double)sprite[esprite].getWidth(this)/bw, 
          imgh = (double)sprite[esprite].getHeight(this)/bw;
        if (xf>=ex && xf<=ex+imgw && yf>=ey && yf<=ey+imgh) return true;
      }
    }
    return false;
  }

  public boolean isOnWall(double xf, double yf) {
    if (isOnPerson(xf,yf)) return true;

    int x = (int)Math.floor(xf), y = (int)Math.floor(yf);
    if (x<0 || x>= 64 || y<0 || y>=64) return true;
    int num = board[x+y*64];
    for (int i=0; i<nowall.length; i++)  if (num==nowall[i]) return false;
    return true;   
  }

  public int isInSwamp() {
    int x = (int)(playerx+1.5), y = (int)(playery+3);
    if (x<0 || x>= 64 || y<0 || y>=64) return -1;
    int num = board[x+y*64];
    for (int i=0; i<swamp.length; i++) if (num==swamp[i]) return i;
    return -1;   
  }

  public boolean canJump() {
    double[][] test = {{1.5,0.5},{0,2},{1.5,3.5},{3,2}};
    double[][] endpos = {{0,-5},{-6.5,3},{0,7},{6.5,3}};

    int x = (int)(playerx+test[playerdir][0]), y = (int)(playery+test[playerdir][1]);
    if (x<0 || x>= 64 || y<0 || y>=64) return false;
    int num = board[x+y*64];
    boolean canjump = false;
    for (int i=0; i<2 && !canjump; i++) if (num==jumpstone[playerdir][i]) canjump = true;
    if (canjump) canjump = !isOnWall(playerx+1.5+endpos[playerdir][0],playery+2+endpos[playerdir][1]);
    return canjump;
  }

  public boolean wallBefore() {
    double[][] test = {{1.5,0.1},{0.4,2},{1.5,3.1},{2.6,2}};

    double x = playerx+test[playerdir][0], y = playery+test[playerdir][1];
    return isOnWall(x,y);
  }

  public boolean canSee(double px, double py, double cx, double cy, int dir) {
    double dx = Math.abs(cx-px), dy = Math.abs(cy-py);
    switch (dir) {
      case UP: if (py>=cy || dx>dy) return false; break;
      case LEFT: if (px>=cx || dy>dx) return false; break;
      case DOWN: if (py<=cy || dx>dy) return false; break;
      case RIGHT: if (px<=cx || dy>dx) return false; break;
    }
    double steps = Math.max(dx,dy);
    if (steps==0) return true;
    if (steps>20) return false;
    for (int i=0; i<steps; i++)
      if (isOnWall(px+(cx-px)*i/steps, py+(cy-py)*i/steps)) return false;
    return true;
  }

// --- Global operations

  public void setSpecialObject(int spr, double x, double y, boolean overplayer) {
    Vector vec = new Vector();
    vec.addElement(new Integer(spr));
    vec.addElement(new Double(x));
    vec.addElement(new Double(y));
    vec.addElement(new Boolean(overplayer));
    specialobjs.addElement(vec);
  }

  public void moveBoard() {
    boardx = playerx+1.5-32/2;
    boardy = playery+2-28/2;
    if (boardx<0) boardx = 0;
    if (boardx>32) boardx = 32;
    if (boardy<0) boardy = 0;
    if (boardy>36) boardy = 36;
  }

  public void putExtra(double x, double y, int sprite, boolean send) {
    Vector vec = new Vector();
    vec.addElement(new Integer(sprite));
    vec.addElement(new Double(x));
    vec.addElement(new Double(y));
    vec.addElement(new Integer(0));
    extras.addElement(vec);
    if (send && client!=null) client.sendExtra(x,y,sprite); 
  }

  public void putLeaps(int index, int x, int y, int type) {
    Vector vec = new Vector();
    vec.addElement(new Integer(index));
    vec.addElement(new Integer(x));
    vec.addElement(new Integer(y));
    vec.addElement(new Integer(type));
    leaps.addElement(vec);
  }

  public void putFlyingObject(int sprite, double x, double y, int dir, double delta,
      int type, boolean send) {
    Vector vec = new Vector();
    vec.addElement(new Integer(sprite));
    vec.addElement(new Double(x));
    vec.addElement(new Double(y));
    vec.addElement(new Integer(dir));
    vec.addElement(new Double(delta));
    vec.addElement(new Integer(type));
    flyingobjs.addElement(vec);
    if (send && client!=null) client.sendShot(x,y,dir,sprite); 
  }


// --- Player actions

  public boolean moveStone() {
    double[][] test = {{1.5,0.5},{0,2},{1.5,3.5},{3,2}};
    int[][] moving = {{0,-1},{-1,0},{0,1},{1,0}};
    int[][][] movetest  = {{{0,-1},{1,-1}},{{-1,0},{-1,1}},{{0,2},{1,2}},{{2,0},{2,1}}};

    int x = (int)(playerx+test[playerdir][0]), y = (int)(playery+test[playerdir][1]);
    if (x<0 || x>= 64 || y<0 || y>=64) return false;
    int num = board[x+y*64];
    for (int i=0; i<2; i++) for (int j=0; j<2; j++)  if (num==movestone[i][j]) { 
      boolean canmove = true;
      for (int k=0; k<2; k++) 
        canmove = canmove && !isOnWall(x-i+movetest[playerdir][k][0],y-j+movetest[playerdir][k][1]);
      if (!canmove) return false;

      int[][] tempstone = new int[2][2];
      for (int rx=0; rx<2; rx++) for (int ry=0; ry<2; ry++) {
        tempstone[rx][ry] = board[x-i+rx+(y-j+ry)*64];
        board[x-i+rx+(y-j+ry)*64] = freestone;
      }
      for (int rx=0; rx<2; rx++) for (int ry=0; ry<2; ry++) 
        board[x-i+rx+moving[playerdir][0]+(y-j+ry+moving[playerdir][1])*64] = tempstone[rx][ry];
      paintOffscreen(x-i-1,y-j-1,4,4);
      playerx += moving[playerdir][0];
      playery += moving[playerdir][1];
      return true;
    }
    return false;
  }

  private boolean readSign() {
    int x = (int)(playerx+1.5), y = (int)(playery+0.5);
    if (x<0 || x>= 64 || y<0 || y>=64) return false;
    for (int i=0; i<signs.size(); i++) {
      Vector vec = (Vector)signs.elementAt(i);
      int sx = ((Integer)vec.elementAt(0)).intValue();
      int sy = ((Integer)vec.elementAt(1)).intValue();
      if ((x==sx || x==sx+1) && y==sy && isOnWall(x,y)) {    
        signy = (playery-boardy<=16? 18 : 9);
        signnum = i;
        if (isOnPerson(x,y)) {
          if (curlevel.equals("level28.zelda") && havemap) {
            signnum++; 
            foundbrother = true;
          }
          if (curlevel.equals("level13.zelda")) {
            if (foundbrother) {
              signnum++;
              for (int j=0; j<2; j++) for (int k=0; k<2; k++) board[25+j+(46+k)*64] = chestfields[j+k*2];
              paintOffscreen(25,46,2,2);
            } else havemap = true;
          }
          if (curlevel.equals("level22.zelda") && rubins>=50) {
            specialobjs.removeElementAt(0);
            rubins-=50;
            signnum++;
          }
        }
        signline = 1;
        showsign = true;
        return true;
      }
    }
    return false;   
  }

  private boolean openChest() {
    int x = (int)(playerx+1.5), y = (int)(playery+0.5);
    if (x<0 || x>= 64 || y<0 || y>=64) return false;

    int num = board[x+y*64];
    for (int i=0; i<2; i++) if (num==chestfields[2+i]) {
      for (int j=0; j<2; j++) for (int k=0; k<2; k++) board[x-i+j+(y-1+k)*64] = chestreplace[j][k];
      paintOffscreen(x-i,y-1,2,2);
      if (curlevel.equals("level13.zelda")) {
        putExtra(x-i,y-2,145,false);
        glovepower=2;
        signnum = 3;
      } else if (curlevel.equals("level21.zelda")) {
        putExtra(x-i,y-2,114,false);
        canshot = true;
        signnum = 0;
      } else {
        String[] lev = {"level24.zelda","village3.zelda","level16.zelda","level29.zelda"};
        if (curlevel.equals(lev[0]) || curlevel.equals(lev[2])) { 
          putExtra(x-i,y-2,144,false);
          darts+=10;
          if (darts>30) darts = 30;
        } else {
          putExtra(x-i,y-2,138,false);
          rubins+=30;
          if (rubins>999) rubins = 999;
        }
        for (int j=0; j<lev.length; j++) if (curlevel.equals(lev[j])) chestopened[j] = true;
        return true;
      }
      signy = (playery-boardy<=16? 18 : 9);
      signline = 1;
      showsign = true;
      return true;
    }
    return false;
  }

  public void throwObject() {
    Vector vec = new Vector();
    vec.addElement(new Double(carryx));
    vec.addElement(new Double(carryy));
    vec.addElement(new Double(playery+1));
    vec.addElement(new Integer(carrysprite));
    vec.addElement(new Integer(9));
    vec.addElement(new Integer(playerdir));
    carries.addElement(vec);
    carrying = false;
    cursprite = 0;
    if (client!=null) client.sendCarryThrow();
  }

  public void modifyBoard(int x, int y, int[][] replace, boolean own) {
    int w = replace.length, h = replace[0].length;
    Vector vec = new Vector();
    vec.addElement(new Integer(x));
    vec.addElement(new Integer(y));
    vec.addElement(new Integer(w));
    vec.addElement(new Integer(h));
    boolean modified = false;
    for (int i=0; i<w; i++) for (int j=0; j<h; j++) {
      if (board[x+y*64]!=replace[i][j]) modified = true;
      board[x+i+(y+j)*64] = replace[i][j];
      vec.addElement(new Integer(replace[i][j]));
    }
    if (modified) {
      boardmodifies.addElement(vec);
      paintOffscreen(x,y,w,h);
      if (client!=null && own) client.sendBoardModify(vec); 
    }
  }

  private void slaySword() {
    anicount = 0;
    animated = firedown = true;

    // Checking for bushes or swamp
    int px = (int)(playerx+(playerdir==LEFT? -0.5: (playerdir==RIGHT? 3.5: 1.5)));
    int py = (int)(playery+(playerdir==UP? 0: (playerdir==DOWN? 4: 2)));
    if (px>=0 && px<64 && py>=0 && py<64) {
      int num = board[px+py*64];
      for (int i=0; i<2; i++) for (int x=0; x<2; x++) for (int y=0; y<2; y++) 
        if (num==bushobj[i][x][y]) {
          modifyBoard(px-x,py-y,bushobjreplace[i],true);
          putLeaps(0,px-x,py-y,i);
          int p = (int)(Math.random()*100);
          if (p<25) { putExtra(px-x,py-y,136,true);
          } else if (p<30) { putExtra(px-x,py-y,139,true);
          } else if (p<32) putExtra(px-x,py-y,137,true);
          break;
      }
    }
//    try { app.play(new URL(app.getCodeBase(),"hit.au"));
//    } catch (MalformedURLException ex) { }
  }

  private boolean liftObjects() {
    int px = (int)(playerx+(playerdir==LEFT? -0.5: (playerdir==RIGHT? 3.5: 1.5)));
    int py = (int)(playery+(playerdir==UP? 0: (playerdir==DOWN? 4: 2)));
    if (px>=0 && px<64 && py>=0 && py<64) {
      int num = board[px+py*64];
      int[] liftsprites = {261,267,265,263};
      for (int i=0; i<(glovepower>1? 4 : 3); i++) for (int x=0; x<2; x++) for (int y=0; y<2; y++) 
        if (num==liftobj[i][x][y]) {
          modifyBoard(px-x,py-y,liftobjreplace[i],true);
          
          if (i==2) { putExtra(px-x,py-y,139,true);
          } else if (i==0) {
            int p = (int)(Math.random()*100);
            if (p<25) { putExtra(px-x,py-y,136,true);
            } else if (p<30) { putExtra(px-x,py-y,139,true);
            } else if (p<32) putExtra(px-x,py-y,137,true);
          }          
          carrysprite = liftsprites[i];
          return true;
      }
    }
    return false;
  }

  private void testForLinks(double linktestx, double linktesty) {
    for (int i=0; i<links.size(); i++) {
      FileLink link = (FileLink)links.elementAt(i);
      if (link.inside(playerx+linktestx, playery+linktesty)) {
        gamestate = LEVELLOADING;
        if (client!=null) client.leaveLevelSession();
        loadFromFile(link.filename);
        if (!link.constantx) playerx = link.newx;
        if (!link.constanty) playery = link.newy;
        if (carrying) {
          carryx = playerx+0.5;
          carryy = playery-1.375;
        }
        moveBoard();
        paintOffscreen();
        gamestate = GAME;
        status=""; repaint();
        if (client!=null) client.enterLevelSession();
        break;
      }
    }
  }

  public void changePower(double d) {
    power += d;
    if (power<=0) { 
      power = anicount = hurtcounter = 0;
      gamestate = ENDING;
      if (client!=null) client.leaveLevelSession();
    }
    if (power>maxpower) power = maxpower;
  }

// --- Key event handling

  int[] keys = {'i','j','k','l','s','a','d', 'I','J','K','L','S','A','D',
    Event.UP,Event.LEFT,Event.DOWN,Event.RIGHT,' ', '8','4','2','6','5','1', 'P','p'};
  int[] keyaction = {UP,LEFT,DOWN,RIGHT,SLAY,LIFT,SHOOT, UP,LEFT,DOWN,RIGHT,SLAY,LIFT,SHOOT,
    UP,LEFT,DOWN,RIGHT,SLAY, UP,LEFT,DOWN,RIGHT,SLAY,LIFT, PAUSE,PAUSE};
  boolean[] keypressed = new boolean[keys.length];
  int[] keydelay = new int[keys.length];

  private void keyEvent(int key, boolean pressed) {
    if (mapwindow!=null && mapwindow.isVisible()) mapwindow.hide();

    for (int i=0; i<keys.length; i++) if (key==keys[i]) {
      int oldstate = gamestate;
      if (!keypressed[i] && pressed) checkStart(keyaction[i]);
      if (!pressed || oldstate==GAME) {
        keypressed[i]=pressed;
        keydelay[i]=10;
      }
    }
  }

  protected boolean handleKeys() {
    boolean paintnew = false;
    for (int i=0; i<keys.length; i++)
      if (keypressed[i] && (keydelay[i]==10 || keydelay[i]==0)) {
        keyAction(keyaction[i]);
        paintnew = true;
    }
    return paintnew;
  }

  private void checkStart(int action) {
    if (gamestate==PAUSED) {
      gamestate = GAME;
      repaint();
      return;
    } else if (gamestate==GAME && action==PAUSE) {
      gamestate = PAUSED;
      repaint();
      return;
    }
    if (hurtcounter>=30 || jumpcounter>0 || liftcounter>0 || gamestate==GAMEEND) return;
    if (showsign) { 
      if (action==UP) return;
      int[] signcode = (int[])((Vector)signs.elementAt(signnum)).elementAt(2);
      int lines = 1;
      for (int i=0; i<signcode.length; i++) if (signcode[i]==96) lines++;
      if (lines>=signline+3) { signline+=3; repaint(); return; 
      } else { showsign = false; repaint(); }
    }
    if (gamestate==STARTSCREEN) { 
      gamestate = GAME;
      if (client!=null) client.enterLevelSession();
      repaint();
      return; 
    }
    if (gamestate==ENDING && anicount>=28) {
      if (client!=null) client.leaveLevelSession();
      loadStartlevel();
      gamestate = GAME;
      if (client!=null) client.enterLevelSession();
      repaint();
      return;
    }
  }

  private void keyAction(int action) {
    if (hurtcounter>=30 || jumpcounter>0 || liftcounter>0 || gamestate==GAMEEND 
      || showsign || gamestate==STARTSCREEN || gamestate==ENDING) return;

    boolean moved = false;
    double linktestx = 1.5, linktesty = 2;
    if (cursprite>=24 && cursprite<=32) cursprite -= 24;

    double[][] newd = {{0,-runw},{-runw,0},{0,runw},{runw,0}};
    double[][][] wd = {{{0.6,1.05},{1.4,1.05},{1.6,1.05},{2.4,1.05},{2.4+runw/4,1.05},{0.6-runw/4,1.05}},
      {{0.55,1.1},{0.55,1.9},{0.55,2.1},{0.55,2.9},{0.55,2.9+runw/4},{0.55,1.1-runw/4}},
      {{0.6,2.95},{1.4,2.95},{1.6,2.95},{2.4,2.95},{2.4+runw/4,2.95},{0.6-runw/4,2.95}},
      {{2.45,1.1},{2.45,1.9},{2.45,2.1},{2.45,2.9},{2.45,2.9+runw/4},{2.45,1.1-runw/4}}};
    double[][][] sidem = {{{runw/4,0},{-runw/4,0}},{{0,runw/4},{0,-runw/4}}, {{runw/4,0},{-runw/4,0}},
      {{0,runw/4},{0,-runw/4}},};
    double[][] testd = {{1.5,1},{0.5,2},{1.5,3},{2.5,2}};

    if (!animated && !dartshooting) for (int i=0; i<4; i++) if (i==action) {
      if (pullcounter==1 && playerdir==(i+2)%4) {
        cursprite = 20;
        pullcounter = 2;
      }
      if (pullcounter>0) break;
      moved = true;
      playerdir = i;
     
      double newx = playerx+newd[i][0], newy = playery+newd[i][1];
      boolean[] wall = new boolean[6];
      for (int k=0; k<6; k++) wall[k] = isOnWall(newx+wd[i][k][0],newy+wd[i][k][1]);

      boolean sidemoved = false;
      if (wall[0] && !wall[2] && !wall[3] && !wall[4]) {
        newx += sidem[i][0][0];
        newy += sidem[i][0][1];
        sidemoved = true;
      } else if (!wall[0] && !wall[1] && wall[3] && !wall[5]) {
        newx += sidem[i][1][0];
        newy += sidem[i][1][1];
        sidemoved = true;
      }
      boolean movedstone = false;
      if (wall[0] || wall[1] || wall[2] || wall[3]) {
        if (!sidemoved && pushcounter==28) { movedstone = moveStone();          
        }
        if (newd[i][0]!=0) newx -= newd[i][0];
        if (newd[i][1]!=0) newy -= newd[i][1];
        pushcounter = (sidemoved? 0 : pushcounter+1);
      } else pushcounter = 0;

      if (pushcounter>=38) pushcounter=18;
      if (!movedstone) {
        playerx = newx;
        playery = newy;
      }
      if (pushcounter==18) {
        if (canJump()) { 
          jumpcounter = 8;
          jumporgx = playerx;
          jumporgy = playery;
        }
      }
      cursprite = (pushcounter>=18 && !carrying? 14+(pushcounter-18)/4 : 
        (cursprite>=8? 1 : cursprite+1));
      linktestx = testd[i][0];
      linktesty = testd[i][1];
      if (i==UP) { readSign(); openChest(); }
    }

    switch (action) {
      case SLAY: {
        if (carrying) { throwObject();
        } else if (!firedown) slaySword();
        break;
      }
      case LIFT: {
        if (carrying) { throwObject();
        } else if (pullcounter==0 && liftcounter==0) {
          if (liftObjects()) {
            cursprite = 23;
            carryx = playerx+0.5+(playerdir==LEFT? -2 : playerdir==RIGHT? 2 : 0);
            carryy = carrybasey = playery+1+(playerdir==UP? -2 : playerdir==DOWN? 2 : 0);
            liftcounter = 5;
          } else if (wallBefore()) {
            cursprite = 19;
            pullcounter = 1;
          }
        }
        break;
      }
      case SHOOT: if (canshot && darts>0 && !dartshooting && !carrying) {
        dartshooting = true;
        cursprite = 33;
        anicount = 0;
        break;
      }
    }
    if (carrying) { 
      if (cursprite<=8) cursprite += 24;
      carryx = playerx+0.5;
      carryy = playery-1.375;
    }
    if (moved) {
      curswamp = 1 - curswamp;
      testForLinks(linktestx, linktesty);
    }
  }

  public boolean keyDown(Event e, int key) {
    if (e.target==textf) {
      if (key==Event.ENTER) {
        curchat = textf.getText();
        if (client!=null) client.sendChatText();
        textf.setText("");
      }
      return false;
    } 
    keyEvent(key, true);
    return true;
  } 

  public boolean keyUp(Event e, int key) {
    if (e.target==textf) return false;
    keyEvent(key, false);
    if (gamestate==LEVELLOADING) return true;
    firedown = false;
    curswamp = 0;
    pushcounter = pullcounter = 0;
    if (!animated && !dartshooting && liftcounter==0) {
      cursprite = (carrying? 24 : 0);
      repaint();
    }
    return true;
  }


// --- Animations ---

  public void run() {
    long lasttime = System.currentTimeMillis();
    for(;;) {
      if (gamestate==STARTING) {
        gamestate = STARTLOADING;
        initGame();
      }

      long difftime = System.currentTimeMillis() - lasttime;
      if (difftime<50)
        try { Thread.sleep(50-difftime); } catch (InterruptedException e) { }
      lasttime = System.currentTimeMillis();
      timercount++;
      if (gamestate==GAMEEND) {
        if (timercount==80) {
          signline = 4;
          repaint();
        } else if (timercount>200) {
          gamestate = STARTSCREEN;
          paint(getGraphics());
          loadStartlevel();
          status="Press any key to start!"; repaint();
        }
      }

      if (gamestate==PAUSED && client!=null && client.players.size()>0 && client.leader) {
        if (timercount%2==0) {
          String compmove = "";
          for (int i=0; i<compus.size(); i++)
            compmove += ((Computer)compus.elementAt(i)).move();
          client.sendCompMove(compmove);
        }
      }
      if (gamestate==LEVELLOADING || gamestate ==PAUSED ||
       (gamestate!=GAME && gamestate!=ENDING) || showsign) continue;
      if (gamestate==ENDING) {
        if (anicount<4) clearAllVectors();
        if (anicount<30) anicount++;
        if (anicount>=4 && anicount<28) {
          cursprite = 0;
          playerdir = (6-(((anicount-4)/2)%4))%4;
          repaint();
        }
        if (anicount==28) { 
          status="Press any key to restart!";
          repaint();
        }
        continue;
      }

      // Game animations
      double veryoldx = playerx, veryoldy = playery;
      boolean paintnew = false;
      if (handleKeys()) paintnew = true;

      if (animated) {
        cursprite = (anicount>=5? 0 : 9+anicount);
        if (anicount>=5) animated = false;
        paintnew = true;
        if (anicount>=1 && anicount<=3)
          for (int i=0; i<compus.size(); i++)
            ((Computer)compus.elementAt(i)).testForHits();
        if (client!=null) client.sendPos();
        anicount++;
      } 
      if (animateJumping()) paintnew = true;
      if (pullcounter>1) {
        pullcounter = (pullcounter>=7? 2 : pullcounter+1);
        cursprite = 20+(pullcounter-2)/2;
        paintnew = true;
      }
      if (animateLifting()) paintnew = true;
      if (animateCarries()) paintnew = true; 
      if (animateLeaps()) paintnew = true;
      if (animateFlyingObjects()) paintnew = true;
      if (animateShooting()) paintnew = true;
      if (timercount%4==0 && isInSwamp()>=8) {
        curswamp = 1 - curswamp;
        paintnew = true;
      }
      if (timercount%2==0) {
        if (compus.size()>0) paintnew = true;
        String compmove = "";
        for (int i=0; i<compus.size(); i++)
          compmove += ((Computer)compus.elementAt(i)).move();
        if (client!=null) client.sendCompMove(compmove);
      }
      if (hurtcounter>0) { 
        hurtFly();
        paintnew = true;
      }
      if (bumpcounter>=0) {
        bumpcounter--;
        paintnew = true;
      }
      if (animateExtras()) paintnew = true;
      if (clientpaint) { 
        clientpaint = false; 
        paintnew = true;
      }

      double oldx = boardx, oldy = boardy;
      moveBoard();
      if (oldx!=boardx || oldy!=boardy) paintnew=true;

      if (paintnew) repaint();
      if (client!=null && ((int)playerx!=(int)veryoldx || (int)playery!=(int)veryoldy))
        client.sendPos();
    }
  }

  private boolean animateJumping() {
    boolean paintnew = false;
    if (jumpcounter>0) {
      cursprite = (jumpcounter>1? 1 : 0);
      double[] jumpdeltax = {1,2,3,3.8,4.5,5.2,5.8,6.5};
      double[] jumpdeltay = {-0.2,-0.5,0,0.5,1.1,1.7,2.3,3};
      double[] jumpdeltaydown = {-0.2,0.2,0.5,1.5,2.8,4,5.4,7};
      double[] jumpdeltayup = {1.5,2.7,3.8,4.5,5,5.2,5.2,5};
      switch (playerdir) {
        case UP: {
          playerx = jumporgx;
          playery = jumporgy - jumpdeltayup[8-jumpcounter];
          break;
        }
        case LEFT: {
          playerx = jumporgx - jumpdeltax[8-jumpcounter];
          playery = jumporgy + jumpdeltay[8-jumpcounter];
          break;
        }
        case DOWN: {
          playerx = jumporgx;
          playery = jumporgy + jumpdeltaydown[8-jumpcounter];
          break;
        }
        case RIGHT: {
          playerx = jumporgx + jumpdeltax[8-jumpcounter];
          playery = jumporgy + jumpdeltay[8-jumpcounter];
          break;
        }
      }
      if (carrying) {
        if (cursprite<=8) cursprite += 24;
        carryx = playerx+0.5;
        carryy = playery-1.375;
      }
      paintnew = true;
      jumpcounter--;
    }
    return paintnew;
  }

  private boolean animateLifting() {
    boolean paintnew = false;
    if (liftcounter>0) {
      liftcounter--;
      if (liftcounter<=0) { 
        cursprite = 24;
        carryx = playerx+0.5;
        carryy = playery-1.375;
        carrying = true;
        if (client!=null) client.sendCarrySprite(carrysprite);
      } else {
        int c = liftcounter-1;
        carryx = playerx+0.5+(playerdir==LEFT? -2 : playerdir==RIGHT? 2 : 0);
        carryy = carrybasey = playery+1+(playerdir==UP? -2 : playerdir==DOWN? 2 : 0);
        double[] liftdelta = {0,0.9,1.5,1.8,2};
        switch (playerdir) {
          case UP: { carrybasey += liftdelta[4-c]; break; }
          case LEFT: {
            carryx += 2-liftdelta[c]; 
            carryy += -liftdelta[4-c]; 
            break;
          }
          case DOWN: {
            carryy += liftdelta[c]-2-liftdelta[4-c];
            carrybasey -= liftdelta[4-c];
            break;
          }
          case RIGHT: {
            carryx += liftdelta[c]-2; 
            carryy += -liftdelta[4-c]; 
            break;
          }
        }
      }
      paintnew = true;
    }
    return paintnew;
  }

  private boolean animateCarries() {
    boolean paintnew = false;
    for (int i=0; i<carries.size();) {
      Vector vec = (Vector)carries.elementAt(i);
      double cx = ((Double)vec.elementAt(0)).doubleValue();
      double cy = ((Double)vec.elementAt(1)).doubleValue();
      double basey = ((Double)vec.elementAt(2)).doubleValue();
      int csprite = ((Integer)vec.elementAt(3)).intValue();
      int cflycounter = ((Integer)vec.elementAt(4)).intValue();
      int cdir = ((Integer)vec.elementAt(5)).intValue();

      cflycounter--;
      cx += (cdir==LEFT? -1: (cdir==RIGHT? 1 : 0));
      cy += (cdir==UP? -0.8: (cdir==DOWN? 1.2: 0.2));
      basey += (cdir==UP? -1: (cdir==DOWN? 1: 0));

      boolean onwall = isOnWall(cx+1,basey+1);
      int power = (csprite==261? 1 : 10);
      for (int j=0; j<compus.size(); j++)
        onwall = onwall || ((Computer)compus.elementAt(j)).testObjectHits(cx+1,cy+1,cdir,power);
      
      if (!onwall){
        vec.setElementAt(new Double(cx),0);
        vec.setElementAt(new Double(cy),1);
        vec.setElementAt(new Double(basey),2);
        vec.setElementAt(new Integer(cflycounter),4);
      }
      if (cflycounter<=0 || onwall) {
        int type = (csprite==261? 0 : (csprite==267? 3 : 2));
        putLeaps(-1,(int)cx,(int)basey,type);
        carries.removeElementAt(i);
      } else i++;
      paintnew = true;
    }
    return paintnew;
  }

  private boolean animateLeaps() {
    boolean paintnew = false;
    for (int i=0; i<leaps.size();) {
      Vector vec = (Vector)leaps.elementAt(i);
      int count = ((Integer)vec.elementAt(0)).intValue()+1;
      int lx = ((Integer)vec.elementAt(1)).intValue();
      int ly = ((Integer)vec.elementAt(2)).intValue();
      int type = ((Integer)vec.elementAt(3)).intValue();
      if (count>=leapsfly[type].length) { leaps.removeElementAt(i);
      } else {
        vec.setElementAt(new Integer(count),0);
        i++;
      }
      paintnew = true;
    }
    return paintnew;
  }

  private boolean animateFlyingObjects() {
    boolean paintnew = false;
    for (int i=0; i<flyingobjs.size(); i++) {
      boolean removed = false;
      Vector vec = (Vector)flyingobjs.elementAt(i);
      int csprite = ((Integer)vec.elementAt(0)).intValue();
      double cx = ((Double)vec.elementAt(1)).doubleValue();
      double cy = ((Double)vec.elementAt(2)).doubleValue();
      int cdir = ((Integer)vec.elementAt(3)).intValue();
      double delta = ((Double)vec.elementAt(4)).doubleValue();
      int shooter = ((Integer)vec.elementAt(5)).intValue();
      int spritew = sprite[csprite].getWidth(this);
      int spriteh = sprite[csprite].getHeight(this);

      cx = cx + (cdir==LEFT? -delta : (cdir==RIGHT? delta : 0));
      cy = cy + (cdir==UP? -delta : (cdir==DOWN? delta : 0));
      if (cx<-2 || cy<-2 || cx>=64 || cy>=64) { if (!removed) { flyingobjs.removeElementAt(i); removed = true; }
      } else {
        vec.setElementAt(new Double(cx),1);
        vec.setElementAt(new Double(cy),2);
        if (delta<0) {
          csprite = 107 + (csprite%4);
          if (csprite == 107+cdir) { if (!removed) { flyingobjs.removeElementAt(i); removed = true; }
          } else vec.setElementAt(new Integer(csprite),0);
        }
      }

      spritew = sprite[csprite].getWidth(this);
      spriteh = sprite[csprite].getHeight(this);
      Rectangle newrect = new Rectangle((int)((cx-boardx)*bw),
        (int)((cy-boardy)*bw),spritew,spriteh);
      Rectangle playerrect = new Rectangle((int)((playerx+0.5-boardx)*bw),
        (int)((playery+1-boardy)*bw),2*bw,2*bw);

      if (shooter==0 && playerrect.intersects(newrect) && hurtcounter==0) {
        if ((cdir+2)%4!=playerdir || spritenum[cursprite]>=6) {
          if (carrying) throwObject();
          hurtcounter = 40;
          changePower(-0.5);
          hurtdx = (cdir==LEFT? -delta : (cdir==RIGHT? delta : 0));
          hurtdy = (cdir==UP? -delta : (cdir==DOWN? delta : 0));
          if (!removed) { flyingobjs.removeElementAt(i); removed = true; }
          if (csprite==131) putLeaps(0,(int)cx,(int)cy,4);
        } else {
          bumpcounter = 4;
          bumppx = playerx + (playerdir==LEFT? 1 : (playerdir==RIGHT? -1 : 0));
          bumppy = playery + (playerdir==UP? 1 : (playerdir==DOWN? -1 : 0));
          if (csprite>=107 && csprite<111) {
            delta = -1;
            vec.setElementAt(new Double(delta),4);
          } else if (csprite==131) {
            if (!removed) { flyingobjs.removeElementAt(i); removed = true; }
            putLeaps(0,(int)cx,(int)cy,4);
          } else if (!removed) { flyingobjs.removeElementAt(i); removed = true; }
        }
      } 
      if (shooter==1) {
        boolean hit = false;
        for (int j=0; j<compus.size(); j++)
          hit = hit || ((Computer)compus.elementAt(j)).testObjectHits(cx+1,cy+1,cdir,3);
        if (hit && !removed) { flyingobjs.removeElementAt(i); removed = true; }
      }
      if (removed) i--;
      paintnew = true;
    }
    return paintnew;
  }

  private boolean animateShooting() {
    boolean paintnew = false;
    if (dartshooting) {
      anicount++;
      if (anicount==14 || anicount==16)  paintnew = true;
      if (anicount==14) {
        double[][] diff = {{6,-8},{-5,13},{11,16},{13,13}};
        Vector vec = new Vector();
        putFlyingObject(107+playerdir,playerx+diff[playerdir][0]/8,
          playery+diff[playerdir][1]/8,playerdir,1,1,true);
        darts--;
      }
      if (anicount==16) {
        dartshooting = false;
        anicount = cursprite = 0;
      }
    }
    return paintnew;
  }

  private boolean animateExtras() {
    boolean paintnew = false;
    for (int i=0; i<extras.size(); i++) {
      Vector vec = (Vector)extras.elementAt(i);
      int esprite = ((Integer)vec.elementAt(0)).intValue();
      double ex = ((Double)vec.elementAt(1)).doubleValue();
      double ey = ((Double)vec.elementAt(2)).doubleValue();
      int ecounter = ((Integer)vec.elementAt(3)).intValue()+1;

      int spritew = sprite[esprite].getWidth(this);
      int spriteh = sprite[esprite].getHeight(this);
      Rectangle paintrect = new Rectangle((int)((ex+1-boardx)*bw-spritew/2),
        (int)((ey-boardy)*bw+1*scale),spritew,spriteh);
      Rectangle playerrect = new Rectangle((int)((playerx+0.5-boardx)*bw),
        (int)((playery+1-boardy)*bw),2*bw,2*bw);
      if (playerrect.intersects(paintrect) && esprite!=114) {
        switch (esprite) {
          case 136: { rubins++; break; }
          case 137: { rubins+=5; break; }
          case 139: { changePower(1); break; }
          case 143: { darts+=5; break; }
        }
        if (rubins>999) rubins = 999;
        if (darts>30) darts = 30; 
        extras.removeElementAt(i); i--;
        ecounter=0;
      }
      boolean superextra = (esprite==114 || esprite==145 || esprite==138 || esprite==144);
      if (ecounter>=164 || (superextra && ecounter>24)) { extras.removeElementAt(i); i--;
      } else vec.setElementAt(new Integer(ecounter),3);

      if (ecounter<=2 || ecounter>=128 || (superextra && ecounter>24)) paintnew = true;
    }
    return paintnew;
  }

  private void hurtFly() {
    hurtcounter--;

    if (hurtcounter>=35) {
      double[][][] test = {{{0.6,1.1},{1.5,1.1},{2.4,1.1}},{{0.6,1.1},{0.6,2},{0.6,2.9}},
        {{0.6,2.9},{1.5,2.9},{2.4,2.9}},{{2.4,1.1},{2.4,2},{2.4,2.9}}};
      playerx += hurtdx;
      playery += hurtdy;
      boolean onwall = false;
      for (int dir=0; dir<4; dir++) for (int i=0; i<3; i++)
        onwall = onwall || isOnWall(playerx+test[dir][i][0],playery+test[dir][i][1]);
      if (onwall) {
        playerx -= hurtdx;
        playery -= hurtdy;
      }
    }
  }

// --- Client methods

  private boolean wasstopped = false;

  public void stop() { 
    if (client!=null) client.Deaktivate(); 
    wasstopped = true;
    gamestate = PAUSE;
  }
  public void start() {
    if (client!=null && wasstopped) client.Aktivate();
    wasstopped = false;
  }
  public void updateClient() { 
    clientpaint = true;
    if (gamestate==GAME) testForOppsHits();
  }

  private void testForOppsHits() {
    if (hurtcounter>0) return;
    for (int i=0; i<client.players.size(); i++) {
      Vector vec = (Vector)client.players.elementAt(i);
//      String clevel = (String)vec.elementAt(1);
//      if (clevel.equals(curlevel)) {
        double cx = ((Double)vec.elementAt(2)).doubleValue();
        double cy = ((Double)vec.elementAt(3)).doubleValue();
        int csprite = ((Integer)vec.elementAt(4)).intValue();
        if (csprite<0 || csprite>=spritevalues.length) csprite=0;

        int cdir = csprite%4, cset = csprite/4;
        if (cset>=10 && cset<=12) {
          double ox = cx+1.5, oy = cy+2, px = playerx+1.5, py = playery+2;
          double dx = px-ox, dy = py-oy;
          double ax = Math.abs(dx), ay = Math.abs(dy);
          boolean hits = false;
          switch (cdir) {
            case UP: hits = (oy>=py && oy<=py+4 && ax<=2); break;
            case LEFT: hits = (ox>=px && ox<=px+4 && ay<=2); break;
            case DOWN: hits = (oy<=py && oy>=py-4 && ax<=2); break;
            case RIGHT: hits = (ox<=px && ox>=px-4 && ay<=2); break;
          }
          if (hits) {
            if (carrying) throwObject();
            hurtcounter = 40;
            changePower(-0.5);
            hurtdx = dx;
            hurtdy = dy;
          }
        }
//      }
    }
  }


// --- Action methods
 
  public boolean handleEvent(Event e) {
    if (e.target instanceof Button && (e.id == Event.ACTION_EVENT || e.id==Event.MOUSE_DOWN) &&
      ((Button)e.target).getLabel().equals("Options")) {
      if (gamestate==GAME) { gamestate = PAUSED; repaint(); }
      if (mapwindow==null) { 
        mapwindow = new ZeldaWindow("Zelda Map",MapPanel.winw,MapPanel.winh);
        mapwindow.show();
        mappanel = new MapPanel(this);
        mapwindow.add("Center",mappanel);
        mapwindow.mappanel = mappanel;
      } else mapwindow.show();
      mappanel.calcCode();
    }
    return super.handleEvent(e);
  }

  public boolean mouseDown(Event e, int x, int y) {
    if (e.target!=textf) {
      but.requestFocus();
      requestFocus();
    }
    return true;
  }


}




