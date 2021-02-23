import java.awt.*;
import java.util.*;


class MapPanel extends Panel {
  ZeldaMain zelda;
  TextField nick = new TextField(), passfield = new TextField();
  Checkbox checkname = new Checkbox("Display your own name"),
    checkcolors = new Checkbox("Enable colored sprites");
  Choice choicewear = new Choice();
  static int winw = 620, winh = 420;
  Image img, dbuf;

  int x = 20, y = 25, w = 310, h = 345, colcellw = 17, colx = 440, coly = 165,
    colindex = 0;
//  int mx = -1, my = -1;
//  Point[] poso;
//  String oppname;
  
  MapPanel(ZeldaMain zelda) {
    this.zelda = zelda;
    setBackground(Color.lightGray);

    setLayout(null);
//    Button update = new Button("Update");
    Button close = new Button("Close");
    Label nicklabel = new Label("Nick name:");
    Label passlabel1 = new Label("Enter that password next time your're");
    Label passlabel2 = new Label("playing to get back to the current state.");
    nick.setText(zelda.nickname);
    checkname.setState(zelda.showownname);
    checkcolors.setState(zelda.showcolors);
    colindex = zelda.curcolor[0];
    choicewear.addItem("Cap");
    choicewear.addItem("Cap Edge");
    choicewear.addItem("Belt");
    choicewear.addItem("Coat");
//    add(update);
    add(nicklabel);
    add(nick);
    add(checkname);
    add(choicewear);
    add(checkcolors);
    add(passfield);
    add(passlabel1);
    add(passlabel2);
    add(close);

//    update.reshape(250,375,80,25);
    nicklabel.reshape(360,25,80,30);
    nick.reshape(450,25,150,30);
    checkname.reshape(370,60,230,30);
    choicewear.reshape(440,130,160,30);
    checkcolors.reshape(370,205,230,30);
    passfield.reshape(360,275,240,30);
    passlabel1.reshape(360,315,240,25);
    passlabel2.reshape(360,340,240,30);
    close.reshape(550,385,60,25);

    img = getToolkit().getImage("map.gif");
    MediaTracker tracker = new MediaTracker(this);
    tracker.addImage(img, 0);
    try { tracker.waitForID(0); } catch (InterruptedException e) { }
  }

  char[] chars = new char[64];
  { 
    for (int i=0; i<26; i++) { chars[i]=(char)('a'+i); chars[26+i]=(char)('A'+i); }
    for (int i=0; i<10; i++) { chars[52+i]=(char)('0'+i); }
    char[] specchars = {'!','?','>','<','#','+','='};
    int[] specpos = {11,34,40,52,53,62,63};
    for (int i=0; i<specchars.length; i++) chars[specpos[i]] = specchars[i];
  }
  long xorcode = 0x4D294F57A1B6CEL;

  public void calcCode() {
    int posx = Math.max(0,Math.min((int)zelda.playerx,63));
    int posy = Math.max(0,Math.min((int)zelda.playery,63));
    int power = (int)(zelda.power*2);

    boolean[] attr = {(zelda.glovepower>1),zelda.canshot,zelda.havemap,zelda.foundbrother};
    long code = ((long)posx<<42)+((long)posy<<36)+((long)getPos(zelda.curlevel)<<27)+
      (zelda.rubins<<17)+(zelda.darts<<11)+(power<<6);
    for (int i=0; i<4; i++) code |= (attr[i]? ((long)1)<<(52+i):0);
    for (int i=0; i<4; i++) code |= (zelda.chestopened[i]? ((long)1)<<(48+i):0);
    for (long i=0, tmp=code; i<60; i++, tmp>>=1) code+=(tmp&1); // add checksum
    
    code ^= xorcode;
    long ncode = 0; // change bit positions
    for (int i=0; i<60; i++) ncode |= ((code>>i)&1)<<((i%10)*6+i/10);
    code = ncode;

    String codestr = "";
    for (long i=0, tmp=code; i<10; i++, tmp>>=6) codestr+=chars[(int)(tmp&0x3F)];
    passfield.setText(codestr);
  }

  private boolean setCode() {
    String codestr = passfield.getText(); 
    if (codestr==null || codestr.length()!=10) return false;
    long code = 0;
    for (int i=0; i<10; i++) {
      code<<=6;
      int charcode = -1;
      for (int j=0; j<64; j++)
        if (codestr.charAt(9-i)==chars[j]) { charcode=j; break; }
      if (charcode>=0) { code+=(long)charcode;
      } else return false;
    }

    long ncode = 0; // change bit positions
    for (int i=0; i<60; i++) ncode |= ((code>>((i%10)*6+i/10))&1)<<i;
    code = ncode;
    code ^= xorcode;

    int checksum = 0;
    for (long i=6, tmp=code>>6; i<60; i++, tmp>>=1) checksum+=(tmp&1);
    if (checksum!=(code&0x3F)) return false;

    String level = getLevel((int)((code>>27)&0x1FF));
    if (level!=null) {
      zelda.glovepower = (((code>>52)&1)>0? 2 : 1);
      zelda.canshot = (((code>>53)&1)>0);
      zelda.havemap = (((code>>54)&1)>0);
      zelda.foundbrother = (((code>>55)&1)>0);
      zelda.chestopened = new boolean[] {((code>>48)&1)>0,((code>>49)&1)>0,((code>>50)&1)>0,
        ((code>>51)&1)>0};
      zelda.playerx = (code>>42)&0x3F;
      zelda.playery = (code>>36)&0x3F;
      zelda.rubins = (int)((code>>17)&0x3FF);
      zelda.darts = (int)((code>>11)&0x3F);
      zelda.power = (double)((code>>6)&0x1F)/2;
      zelda.showsign = false;
      zelda.carrying = false;

      zelda.gamestate = ZeldaMain.LEVELLOADING;
      zelda.loadFromFile(level);
      zelda.moveBoard();
      zelda.paintOffscreen();
      zelda.gamestate = ZeldaMain.PAUSE;
      zelda.status=""; zelda.repaint();
      repaint();
      if (zelda.client!=null) zelda.client.sendPos();
      return true;
    }
    return false;
  }

  
  public void paintBorder(Graphics g, int x, int y, int w, int h, String s) {
    g.setColor(Color.white);
    g.drawRect(x+1, y+1, w, h);
    g.setColor(Color.gray);
    g.drawRect(x, y, w, h);
    g.setColor(Color.lightGray);
    g.fillRect(x+7, y-10, g.getFontMetrics().stringWidth(s)+4, 16);
    g.setColor(Color.black);
    g.drawString(s, x+9, y+5);
  }
  
  byte[] levelpos = {44,43,51,52,45,53,46,50,42,36, 35,41,40,60,59,58,37,29,21,20,
    19,18,10,2,17,34,26,22,54, 33,25,24,32};
 
  public int getPos(String level) {
    try {
      if (level.startsWith("level"))
        return levelpos[Integer.parseInt(level.substring(5,level.indexOf(".")))-1];
      if (level.startsWith("village"))
        return levelpos[Integer.parseInt(level.substring(7,level.indexOf(".")))+28];
      if (level.startsWith("house"))
        return Integer.parseInt(level.substring(5,level.indexOf(".")))-1+100;
      if (level.startsWith("cave"))
        return Integer.parseInt(level.substring(4,level.indexOf(".")))-1+150;
    } catch (Exception e) { }
    return -1;
  }

  public String getLevel(int pos) {
    for (int i=0; i<29; i++) if (pos==levelpos[i]) return "level"+(i+1)+".zelda";
    for (int i=0; i<4; i++) if (pos==levelpos[29+i]) return "village"+(i+1)+".zelda";
    if (pos>=100 && pos<150) return "house"+(pos-100+1)+".zelda";
    if (pos>=150 && pos<250) return "cave"+(pos-150+1)+".zelda";
    if (pos==64) return "house15.zelda";
    if (pos==65) return "cave1.zelda";
    if (pos==66) return "cave2.zelda";
    return null;
  }

  public void takeOptions() {
    zelda.nickname = nick.getText();
    zelda.showownname = checkname.getState();
    zelda.showcolors = checkcolors.getState();
    if (zelda.client!=null) {
      zelda.client.sendNickName();
      zelda.client.sendSpriteColors();
    }
  }

  public synchronized boolean handleEvent (Event e) {
    switch  (e.id) {
      case Event.ACTION_EVENT: {
//        if (e.arg.equals("Update")) repaint();
        if (e.target.equals(choicewear)) { 
          int index = choicewear.getSelectedIndex();
          if (index>=0) colindex = zelda.curcolor[index];
          repaint();
          return true;
        }
        break;
      }
      case Event.KEY_PRESS: {
        if (e.target==passfield && e.key==Event.ENTER) {
          if (!setCode()) passfield.setText("- illegal -");
          return true;
        }
      }
    }
    return super.handleEvent(e);
  }
  
  public void paint(Graphics g) {
//    updatePositions();
    paintBorder(g, 10, 10, 330, 400, "Map");
    paintBorder(g, 350, 10, 260, 90, "Name");
    paintBorder(g, 350, 115, 260, 130, "Colors");
    paintBorder(g, 350, 260, 260, 115, "Level Password");

    g.setColor(Color.white);
    g.fillRect(x+10,y+h+10,5,5);
//    g.setColor(Color.red);
//    g.fillRect(x+70,y+h+10,5,5);
    g.setColor(Color.black);
    g.drawString("You",x+20,y+h+20);
//    g.drawString("Other Players ("+poso.length+")",x+80,y+h+20);

    Graphics g2 = g.create(x,y,w,h);
    drawMap(g2);

    drawPlayer(g, 370, 135);
    drawColors(g, colx, coly);
  }

  public void update(Graphics g) {
    if (dbuf==null) dbuf = createImage(winw,winh);
    Graphics og = dbuf.getGraphics();
    og.setColor(Color.lightGray);
    og.fillRect(0,0,winw,winh);
    paint(og);
    g.drawImage(dbuf,0,0,this);
  }

  private void drawMap(Graphics g) {
    if (img != null) g.drawImage(img,0,0,w,h,this);

    int pos = getPos(zelda.curlevel);
    if (pos>=0 && pos<64) {
      int px = (int)(((pos%8)*50+(zelda.playerx*50/64))*w/350);
      int py = (int)(((pos/8)*50+(zelda.playery*50/64))*h/400);
      g.setColor(Color.white);
      g.fillRect(px-2,py-2,5,5);
    }

/*    for (int i=0; i<poso.length; i++) if (poso[i]!=null){
      g.setColor(Color.red);
      g.fillRect(poso[i].x-2,poso[i].y-2,5,5);
    }*/

/*    if (oppname!=null) {
      FontMetrics fm = getFontMetrics(g.getFont());
      int strw = fm.stringWidth(oppname)+10;
      int strh = fm.getHeight();
      int desc = fm.getMaxDescent();

      int hintx = mx+5, hinty = my+5;
      if (hintx+strw>w) hintx = w-strw;
      if (hintx<0) hintx = 0;
      if (hinty+strh+10>h) hinty = h-strh-10;
      if (hinty<0) hinty = 0;

      g.setColor(new Color(255,255,192));
      g.fill3DRect(hintx,hinty,strw,strh+10,true); 
      g.setColor(Color.black);
      g.drawString(oppname,hintx+5,hinty+5+strh-desc);
    }*/
  }

  private void drawPlayer(Graphics g, int x, int y) {
    zelda.setPlayerColors(zelda.curcolor);

    int[] s = zelda.spritevalues[2];
    for (int i=0; i<s.length; i++) {
      int snum = s[i]>>16&0xFFFF;
      zelda.spritemis[snum].newPixels(zelda.spritearrays[snum],zelda.icm,0,zelda.spritew[snum]);
      g.drawImage(zelda.sprite[snum], x+(byte)(s[i]>>8&0xFF)*zelda.scale, 
        y+(byte)(s[i]&0xFF)*zelda.scale, this);
    }
  }

  private void drawColors(Graphics g, int x, int y) {
    for (int i=0; i<18; i++) {
      int cx = x+(i%9)*colcellw, cy = y+i/9*colcellw;
      g.setColor(Color.lightGray);
      g.draw3DRect(cx,cy,colcellw-1,colcellw-1,false);
      g.setColor(new Color(zelda.allcolors[i]));
      g.fillRect(cx+1,cy+1,colcellw-2,colcellw-2);
      if (i==colindex) {
        g.setColor(Color.white);
        g.drawLine(cx+2,cy+2,cx+colcellw-4,cy+colcellw-3);
        g.drawLine(cx+3,cy+2,cx+colcellw-3,cy+colcellw-3);
        g.drawLine(cx+2,cy+colcellw-3,cx+colcellw-4,cy+2);
        g.drawLine(cx+3,cy+colcellw-3,cx+colcellw-3,cy+2);
      }
    }
  }

/*  private void updatePositions() {
    if (zelda.client==null) {
      poso = new Point[0];
      return;
    }
    poso = new Point[zelda.client.players.size()];
    for (int i=0; i<poso.length; i++) {
      Vector vec = (Vector)zelda.client.players.elementAt(i);
      String clevel = (String)vec.elementAt(1);
      double cx = ((Double)vec.elementAt(2)).doubleValue();
      double cy = ((Double)vec.elementAt(3)).doubleValue();

      int pos = getPos(clevel);
      if (pos>=0 && pos<64) {
        int px = (int)(((pos%8)*50+(cx*50/64))*w/350);
        int py = (int)(((pos/8)*50+(cy*50/64))*h/400);
        poso[i] = new Point(px,py);
      }      
    } 
  }*/

/*  public boolean mouseMove(Event e, int x, int y) {
    mx = x-this.x; my = y-this.y;
    String oldname = oppname;
    oppname = null;
    if (mx>=0 && mx<w && my>=0 && my<h) {
      int dist = 1000, nameindex = -1;
      for (int i=0; i<poso.length; i++) if (poso[i]!=null){
        int w = Math.abs(poso[i].x-mx)+Math.abs(poso[i].y-my);
        if (w<dist) { dist = w; nameindex = i; }
      }
      if (nameindex>=0 && dist<20 && zelda.client!=null) {
        Vector vec = (Vector)zelda.client.players.elementAt(nameindex);
        oppname = (String)vec.elementAt(6);
      }
    }
    if (oppname!=null || oldname!=null) repaint();
    return true;
  }*/

  public boolean mouseDown(Event e, int x, int y) {
    int cx = x-colx, cy = y-coly;
    if (cx>=0 && cx<9*colcellw && cy>=0 && cy<2*colcellw) {
      colindex = cx/colcellw+cy/colcellw*9;
      int index = choicewear.getSelectedIndex();
      if (index>=0) zelda.curcolor[index] = colindex;
      repaint();
    }
    return true;
  }

}
