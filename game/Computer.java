import java.awt.*;
import java.util.*;

class Computer {
  ZeldaMain z;
  static public final int WALK = 0, LOOK = 1, HUNT = 2, HURTED = 3, BUMPED = 4, DIE = 5,
    SWAMPSHOT = 6, HAREJUMP = 7, OCTOSHOT = 8, DEAD = 9;
  static public final int SOLDIERGREEN = 0, SOLDIERBLUE = 1, SOLDIERRED = 2,
    SHOTSOLDIER = 3, SWAMPSOLDIER = 4, JUMPINGHARE = 5, OCTOPUS = 6;

  int mode = WALK;
  int dir, headdir, spriteset=0, type, power, destplayer=-1;
  double x, y, hurtdx, hurtdy, orgx, orgy, destdist;
  int anicount, seecount, huntcount;
  boolean flicker = false, shooting = false, modelocked = false;
  int id;

  Computer(ZeldaMain zelda, double x, double y, int type, int id) {
    z = zelda;
    this.x = x;
    this.y = y;
    this.type = type;
    this.id = id;

    switch (type) {
      case OCTOPUS: power = 1; break;
      case SOLDIERGREEN: case SWAMPSOLDIER: power = 2; break;
      case SOLDIERBLUE: case SHOTSOLDIER: power = 3; break;
      case SOLDIERRED: power = 4; break;
    }
    if (Math.abs(x-z.playerx)+Math.abs(y-z.playery)<=30 || type == SOLDIERBLUE) {
      dir = headdir = (z.playerdir+2)%4;
    } else dir = headdir = (y<z.playery? ZeldaMain.DOWN : ZeldaMain.UP);
    if (type==SWAMPSOLDIER) {
      mode = SWAMPSHOT;
      anicount = 0;
      spriteset = 2;
    } else if (type==JUMPINGHARE) {
      mode = HAREJUMP;
      anicount = seecount = 0;
      orgx = x;
      orgy = y;
      int newdir = (int)(Math.random()*4); 
      this.x = orgx + (newdir==ZeldaMain.LEFT? -4 : (newdir==ZeldaMain.RIGHT? 4 : 0));
      this.y = orgy + (newdir==ZeldaMain.UP? -4 : (newdir==ZeldaMain.DOWN? 4 : 0));
    } else if (type==OCTOPUS) {
      dir = headdir = ZeldaMain.RIGHT;
      spriteset = 4;
      anicount = 20+(int)(Math.random()*10);
    } else {
      anicount = 40+(int)(Math.random()*20);
    }
  }

  public int getColorBlock() {
    if (flicker) { return anicount%3;
    } else {
      switch (type) {
        case SOLDIERGREEN: case SWAMPSOLDIER: return 1;
        case SOLDIERBLUE: case SHOTSOLDIER: return 0;
        case SOLDIERRED: return 2;
      } 
    }
    return 0;
  }

  public void testForHits() {
    if (mode==HURTED || mode==BUMPED || mode==DIE
      || (mode==SWAMPSHOT && (seecount>0 || anicount<17))
      || (mode==HAREJUMP && (anicount<17 || anicount>37))) return;

    double plx = z.playerx+1.5, ply = z.playery+2, cx = x+1, cy = y+1;
    if (mode==HAREJUMP && anicount>=17 && anicount<=37)
      cy = cy-ZeldaMain.jumphare[10-Math.abs(anicount-27)];
    double dx = plx-cx, dy = ply-cy;
    double ax = Math.abs(dx), ay = Math.abs(dy);
    boolean bumpingcompu = false, hitscompu = false;
    if ((z.playerdir+2)%4 == dir && type>=SOLDIERGREEN && type<=SOLDIERRED)
        switch (z.playerdir) {
      case ZeldaMain.UP: bumpingcompu = (ply<=cy+5 && ply>cy+2 && ax<=2 && plx<cx+0.5); break;
      case ZeldaMain.LEFT: bumpingcompu = (plx<=cx+5 && plx>cx+2 && ay<=2 && ply>cy-0.5); break;
      case ZeldaMain.DOWN: bumpingcompu = (ply>=cy-5 && ply<cy-2 && ax<=2 && plx>cx-0.5); break;
      case ZeldaMain.RIGHT: bumpingcompu = (plx>=cx-5 && plx<cx-2 && ay<=2 && ply>cy-0.5); break;
    }
    if (!bumpingcompu) switch (z.playerdir) {
      case ZeldaMain.UP: hitscompu = (ply<=cy+4 && ply>=cy && ax<=2); break;
      case ZeldaMain.LEFT: hitscompu = (plx<=cx+4 && plx>=cx && ay<=2); break;
      case ZeldaMain.DOWN: hitscompu = (ply>=cy-4 && ply<=cy && ax<=2); break;
      case ZeldaMain.RIGHT: hitscompu = (plx>=cx-4 && plx<=cx && ay<=2); break;
    }
    if (hitscompu || bumpingcompu) {
      hurt(hitscompu,bumpingcompu,z.playerx,z.playery,cy,dx,dy,1);
      if (hitscompu && z.client!=null) 
        z.client.sendCompuHurt(id,dx,dx,1); 
    }
  }

  public void hurt(boolean hit, boolean bump, double px, double py, double cy,
      double dx, double dy, int decpower) {
    if (z.client!=null && !z.client.leader) {
      if (hit) {
        power-=decpower;
        mode = HURTED;
        modelocked = true;
      }
      return;
    }
    double movef;
    if (bump) {
      mode = BUMPED;
      movef = 0.8;
      z.bumpcounter = 4;
      z.bumppx = px;
      z.bumppy = py;
      flicker = false;
    } else {
      flicker = true;
      power-=decpower;
      if (type==SWAMPSOLDIER) {
        seecount = 8;
        return;
      } else if (type==JUMPINGHARE) {
        mode = DIE;
        anicount = 9; 
        y = cy-1;
        putRandomExtras();
        return;
      }
      mode = HURTED;
      movef = 2;
    } 
    anicount = 8;
    hurtdx = -dx*2;
    hurtdy = -dy*2;
    double abs = Math.sqrt(hurtdx*hurtdx+hurtdy*hurtdy);
    if (abs>0) {
      hurtdx *= movef/abs;
      hurtdy *= movef/abs;
    } else hurtdx = -movef;
  }

  public boolean testObjectHits(double ox, double oy, int cdir, int decpower) {
    if (mode==HURTED || mode==BUMPED || mode==DIE
      || (mode==SWAMPSHOT && seecount>0) || (mode==SWAMPSHOT && (anicount<22 || anicount>36))
      || (mode==HAREJUMP && (anicount<17 || anicount>37))) return false;

    double cx = x+1, cy = y+1;
    if (mode==HAREJUMP && anicount>=17 && anicount<=37)
      cy = cy-ZeldaMain.jumphare[10-Math.abs(anicount-27)];
    boolean hitscompu = (ox>=cx-1 && ox<=cx+1 && oy>=cy-2 && oy<=cy+1);

    if (hitscompu) {
      double dx = (cdir==ZeldaMain.LEFT? -1 : cdir==ZeldaMain.RIGHT? 1 : 0);
      double dy = (cdir==ZeldaMain.UP? -1 : cdir==ZeldaMain.DOWN? 1 : 0);
      hurt(true,false,z.playerx,z.playery,cy,dx,dy,decpower);
      if (z.client!=null) 
        z.client.sendCompuHurt(id,dx,dx,decpower); 
      return true;
    }
    return false;
  }

  static final double[][][] test1 = {{{0.1,0.1},{1,0.1},{1.9,0.1}},{{0.1,0.1},{0.1,1},{0.1,1.9}},
    {{0.1,1.9},{1,1.9},{1.9,1.9}},{{1.9,0.1},{1.9,1},{1.9,1.9}}};
  static final double[][][] test2 = {{{0.1,-ZeldaMain.runw},{1,-ZeldaMain.runw},{1.9,-ZeldaMain.runw}},
    {{-ZeldaMain.runw,0.1},{-ZeldaMain.runw,1},{-ZeldaMain.runw,1.9}},
    {{0.1,2+ZeldaMain.runw},{1,2+ZeldaMain.runw},{1.9,2+ZeldaMain.runw}},
    {{2+ZeldaMain.runw,0.1},{2+ZeldaMain.runw,1},{2+ZeldaMain.runw,1.9}}};
  static final double[][][] test3 = {{{0.1,-0.1},{1,-0.1},{1.9,-0.1}},
    {{-0.1,0.1},{-0.1,1},{-0.1,1.9}},{{0.1,2.1},{1,2.1},{1.9,2.1}},{{2.1,0.1},{2.1,1},{2.1,1.9}}};
  static final double[][] walk = {{0,-ZeldaMain.runw/2},{-ZeldaMain.runw/2,0},{0,ZeldaMain.runw/2},
    {ZeldaMain.runw/2,0}};
  static final int[] movehead = {1,1,0,-1,-1,0,1,1,0,-1,-1};


  public String move() {
    if (z.client==null || z.client.leader) move1();
    if (z.gamestate!=ZeldaMain.PAUSED) move2();
    if (z.client!=null && z.client.leader) {
      return z.client.getCompMoveString(id,x,y,anicount,dir,headdir,mode);
    } else return "";
  }

  public void move1() {
    if (mode==WALK) {
      if (headdir != dir) {
        if ((anicount&1) == 0) {
          dir = headdir;
        } else anicount--; 
      } else {
        anicount--; 
        if (anicount<0) {
          if (type==OCTOPUS) {
            mode = OCTOSHOT;
            anicount = 13;
          } else {
            mode = LOOK;
            anicount = 44;
          }
        } else spriteset = (anicount%4)/2 + (type==OCTOPUS? 4 :0);

        if (mode==WALK) {
          boolean wall = false;
          for (int i=0; i<3 && !wall; i++)
            wall = wall || z.isOnWall(x+test2[dir][i][0], y+test2[dir][i][1]);
          if (wall) { 
            headdir = (dir+2)%4;
            dir = (type==OCTOPUS? headdir : (dir+3)%4);
            if ((anicount&1)==0) anicount--;
          } else {
            double movef = (type==OCTOPUS? 2 : 1);
            x += walk[dir][0]*movef;
            y += walk[dir][1]*movef;
          }
        }
      }
      if (type!=OCTOPUS) lookForPlayer();
    } else if (mode==LOOK) {
      anicount--;
      if (anicount>=0) {
        headdir = (dir+movehead[anicount/4]+4)%4;
      } else {
        dir = headdir = (int)(Math.random()*4);
        mode = WALK; 
        anicount = 40 + (int)(Math.random()*20);
      }  
      lookForPlayer();
    } else if (mode==HUNT) {
      if (anicount>=6) { 
        anicount--;
        if (!flicker) spriteset = anicount%2;
      } else {
        flicker = false;
        if (type==SHOTSOLDIER) { 
          moveShootingCompu();
        } else {
          moveHuntingSwordCompu();
        }
      }
    } else if (mode==HURTED || mode==BUMPED) {
      hurtFly();
    } else if (mode==DIE) {
      if (anicount>=0) anicount--;
    } else if (mode==SWAMPSHOT) {
      anicount = (anicount>=42? 0 : anicount+1);
      if (anicount==11) dir = headdir = ZeldaMain.DOWN;
      if (anicount==17) z.putLeaps(0,(int)x,(int)y,0);
      if (anicount==22) dir = headdir = calcDir();
      if (anicount==25) shootDart();
      if (anicount==33) { dir = headdir = ZeldaMain.DOWN; shooting=false; }
      if (seecount>0) {
        seecount--;
        if (seecount<=0) {
          flicker = false;
          if (power<=0) {
            mode = DIE;
            anicount = 14;
            putRandomExtras();
          }   
        }
      }
    } else if (mode==HAREJUMP) {
      anicount = (anicount>=45? 0 : anicount+1);
      if (anicount==0) {
        seecount++;
        if (seecount%2==0) {
          int newdir = (int)(Math.random()*4); 
          x = orgx + (newdir==ZeldaMain.LEFT? -4 : (newdir==ZeldaMain.RIGHT? 4 : 0));
          y = orgy + (newdir==ZeldaMain.UP? -4 : (newdir==ZeldaMain.DOWN? 4 : 0));
        }
      }
    } if (mode==OCTOSHOT) {
      anicount--;
      if (anicount==10) spriteset = 6;
      if (anicount==6) { 
        spriteset = 7;
        double[][] diff = {{4,-3},{-8,5},{4,12},{17,5}};
        z.putFlyingObject(131,x+diff[dir][0]/8,y+diff[dir][1]/8,dir,1,0,true);
      }
      if (anicount==2) spriteset = 4;
      if (anicount==0) {
        dir = headdir = (int)(Math.random()*4);
        mode = WALK; 
        anicount = 20 + (int)(Math.random()*10);
      }
    }
  }

  public void move2() {
    if (!(mode==HURTED || mode==BUMPED || mode==DIE
        || (mode==SWAMPSHOT && seecount>0) || (mode==SWAMPSHOT && (anicount<22 || anicount>36))
        || (mode==HAREJUMP && (anicount<17 || anicount>37))) && z.hurtcounter==0) {
      double px = z.playerx+1.5, py = z.playery+2, cx = x+1, cy = y+1;
      if (mode==HAREJUMP && anicount>=17 && anicount<=37)
        cy = cy-ZeldaMain.jumphare[10-Math.abs(anicount-27)];
      double dx = px-cx, dy = py-cy;
      double ax = Math.abs(dx), ay = Math.abs(dy);
      if (ax<=1 && ay<=1) {
        if (z.carrying) z.throwObject();
        z.hurtcounter = 40;
        z.changePower(-0.5);
        z.hurtdx = dx;
        z.hurtdy = dy;
      }
    }

    if (type==SHOTSOLDIER) spriteset = spriteset%2 + 2;
    if (type==OCTOPUS && spriteset<2) spriteset = spriteset%2 + 4;
    if (mode==DIE && anicount<0) {
      die();
      mode = DEAD;
    } 
  }
  
  public void remoteMove(double newx, double newy, int anicount, int dir, int headdir, int mode) {
    x = newx;
    y = newy;
    this.anicount = anicount;
    this.dir = dir;
    this.headdir = headdir;
    if (mode==HURTED) {
      modelocked = false;
      flicker = true;
    } else flicker = false;
    if (!modelocked) this.mode = mode;
    if (mode == DEAD) die();
  }

  public void die() {
    z.compus.removeElement(this);
    z.deadcompus.addElement(new Integer(id));
    if (z.curlevel.equals("cave2.zelda") && z.compus.size()==0) {
      z.gamestate = z.GAMEEND;
      z.setSpecialObject(149,31,23,false);
      z.boardx = z.boardy = 16;
      z.playerx= z.boardx+32/2-1.5;
      z.playery= z.boardy+28/2-2;
      z.playerdir = ZeldaMain.UP;

      z.signy = (z.playery-16<=16? 18 : 9);
      z.signnum = 0;
      z.signline = 1;
      z.showsign = true;
      z.timercount = 0;
      for (int i=0; i<2; i++) for (int j=0; j<12; j++) z.board[27+i+(22+j)*64] = z.freestone;
      z.paintOffscreen(27,22,2,12);
    }
    try{ finalize(); } catch (Throwable t) { }
  }

  private void lookForPlayer() {
    int destdir = calcDir();
    double px = z.playerx+1.5, py = z.playery+2, cx = x+1, cy = y+1;
    if (destplayer>=0) {
      Vector vec = (Vector)z.client.players.elementAt(destplayer);
      px = ((Double)vec.elementAt(2)).doubleValue()+1.5;
      py = ((Double)vec.elementAt(3)).doubleValue()+2;
    } else if (z.gamestate==ZeldaMain.PAUSED) return;
    boolean cansee = z.canSee(px,py,cx,cy,dir);
    if (cansee && destdist<20) {
      mode = HUNT;
      anicount = 16;
      dir = headdir = destdir;
      seecount = huntcount = 0;
    }
  }
  

  private void moveHuntingSwordCompu() {
    anicount = (anicount+3)%4;
    spriteset = anicount/2;

    double px = z.playerx+1.5, py = z.playery+2, cx = x+1, cy = y+1;
    calcDir();
    if (destplayer>=0) {
      Vector vec = (Vector)z.client.players.elementAt(destplayer);
      px = ((Double)vec.elementAt(2)).doubleValue()+1.5;
      py = ((Double)vec.elementAt(3)).doubleValue()+2;
    } else if (z.gamestate==ZeldaMain.PAUSED) return;
    double dx = px-cx, dy = py-cy, ax = Math.abs(dx), ay = Math.abs(dy);
    boolean cansee = z.canSee(px,py,cx,cy,dir);
    if (cansee && ax<=1 && ay<=1 && z.hurtcounter==0 && destplayer==-1) {
      if (z.carrying) z.throwObject();
      z.hurtcounter = 40;
      z.hurtdx = dx;
      z.hurtdy = dy;
    }
    if (!cansee) {
      seecount++;
      if (seecount>40) {
        mode = LOOK;
        anicount = 44;
      }
    } else seecount = 0;
    if (mode==HUNT) {
      int lookdir, sidedir;
      double sidef;
      if (ay>ax) { lookdir=(dy>0? 2:0); sidedir=(dx>0? 3:1); sidef = ax/ay; 
      } else { lookdir=(dx>0? 3:1); sidedir=(dy>0? 2:0); sidef = (ax>0? ay/ax : 0); }
      int normlookdir = lookdir;
      if (!(ay>=2 || ax>=2 || cansee)) lookdir = dir;
      boolean lookwall=false, sidewall=false;
      for (int i=0; i<3; i++) {
        lookwall = lookwall || z.isOnWall(x+test2[lookdir][i][0], y+test2[lookdir][i][1]);
        sidewall = sidewall || z.isOnWall(x+test3[sidedir][i][0], y+test3[sidedir][i][1]);
      }        
      if (normlookdir!=lookdir && lookwall) {
        lookdir = normlookdir;
        lookwall=sidewall=false;
        for (int i=0; i<3; i++) {
          lookwall = lookwall || z.isOnWall(x+test2[lookdir][i][0], y+test2[lookdir][i][1]);
          sidewall = sidewall || z.isOnWall(x+test3[sidedir][i][0], y+test3[sidedir][i][1]);
        }        
      }
      dir = headdir = lookdir;
      if (!lookwall && sidewall) {
        x += 2*walk[lookdir][0];
        y += 2*walk[lookdir][1];
      } else if (!lookwall && !sidewall) {
        x += 2*walk[lookdir][0] + 2*walk[sidedir][0]*sidef;
        y += 2*walk[lookdir][1] + 2*walk[sidedir][1]*sidef;
      } else if (lookwall && !sidewall) {
        x += 2*walk[sidedir][0];
        y += 2*walk[sidedir][1];
      }
      if (lookwall) {
        x = Math.floor(x*2)/2;
        y = Math.floor(y*2)/2;
        lookwall = false;
        boolean[] dirwalls = {false,false,false,false};
        for (int dir=0; dir<4; dir++) for (int i=0; i<3; i++) 
          dirwalls[dir] = dirwalls[dir] || z.isOnWall(x+test1[dir][i][0],y+test1[dir][i][1]);
        if (dirwalls[ZeldaMain.UP]) y+=0.5;
        if (dirwalls[ZeldaMain.LEFT]) x+=0.5;
        if (dirwalls[ZeldaMain.DOWN]) y-=0.5;
        if (dirwalls[ZeldaMain.RIGHT]) x-=0.5;
      }
    }
  }

  private void moveShootingCompu() {
    spriteset = 2;
    if (seecount>0) { 
      seecount--; 
      if (seecount==0) anicount = 12;
      return;
    }

    double px = z.playerx+1.5, py = z.playery+2, cx = x+1, cy = y+1;
    dir = headdir = calcDir();
    if (destplayer>=0) {
      Vector vec = (Vector)z.client.players.elementAt(destplayer);
      px = ((Double)vec.elementAt(2)).doubleValue()+1.5;
      py = ((Double)vec.elementAt(3)).doubleValue()+2;
    } else if (z.gamestate==ZeldaMain.PAUSED) return;
    double aimx = px + (dir==ZeldaMain.LEFT? 11 : (dir==ZeldaMain.RIGHT? -11 : dir==ZeldaMain.UP? -1 : 0));
    double aimy = py + (dir==ZeldaMain.UP? 11 : (dir==ZeldaMain.DOWN? -11 : 1));

    if (shooting) {
      shooting = false;
      if (!z.canSee(px,py,cx,cy,dir)) {
        mode = LOOK;
        anicount = 44;
        return;
      }
    }
    double dx = aimx-cx, dy = aimy-cy, ax = Math.abs(dx), ay = Math.abs(dy);
    if ((ax<1 && ay<1) || huntcount>40) { shootDart(); 
    } else {
      huntcount++;
      int movedir, sidedir;
      if (ay>ax) { movedir=(dy>0? 2:0); sidedir=(dx>0? 3:1); 
      } else { movedir=(dx>0? 3:1); sidedir=(dy>0? 2:0); }
      boolean movewall=false, sidewall=false;
      for (int i=0; i<3; i++) {
        movewall = movewall || z.isOnWall(x+test2[movedir][i][0], y+test2[movedir][i][1]);
        sidewall = sidewall || z.isOnWall(x+test3[sidedir][i][0], y+test3[sidedir][i][1]);
      }        
      double oldx=x, oldy=y;
      if (!movewall) {
        if (ax>=1) x += 2*walk[movedir][0];
        if (ay>=1) y += 2*walk[movedir][1];
      }
      if (!sidewall) {
        if (ax>=1) x += 2*walk[sidedir][0];
        if (ay>=1) y += 2*walk[sidedir][1];
      }
      if (movewall) {
        x = Math.floor(x*2)/2;
        y = Math.floor(y*2)/2;
        boolean[] dirwalls = {false,false,false,false};
        for (int dir=0; dir<4; dir++) for (int i=0; i<3; i++) 
          dirwalls[dir] = dirwalls[dir] || z.isOnWall(x+test1[dir][i][0],y+test1[dir][i][1]);
        if (dirwalls[ZeldaMain.UP]) y+=0.5;
        if (dirwalls[ZeldaMain.LEFT]) x+=0.5;
        if (dirwalls[ZeldaMain.DOWN]) y-=0.5;
        if (dirwalls[ZeldaMain.RIGHT]) x-=0.5;
      }
      if ((x==oldx && y==oldy) || ((dir==ZeldaMain.LEFT || dir==ZeldaMain.RIGHT) && ay<1 && ax>=11)
        || ((dir==ZeldaMain.UP || dir==ZeldaMain.DOWN) && ax<1 && ay>=11)) shootDart();
    }
  }

  private void shootDart() {
    double[][] diff = {{12,-12},{-10,0},{4,3},{11,0}};
    seecount=15;
    shooting = true;
    z.putFlyingObject(107+dir,x+diff[dir][0]/8,y+diff[dir][1]/8,dir,1,0,true);
    huntcount = 0;
  }


  private int calcDir() {
    double px = z.playerx+1.5, py = z.playery+2, cx = x+1, cy = y+1;
    int destdir = dir;
    destplayer = -1;
    destdist = 1000;

    if (z.client!=null) for (int i=0; i<z.client.players.size(); i++) {
      Vector vec = (Vector)z.client.players.elementAt(i);
      double x = ((Double)vec.elementAt(2)).doubleValue()+1.5;
      double y = ((Double)vec.elementAt(3)).doubleValue()+2;
      double dx = x-cx, dy = y-cy, ax = Math.abs(dx), ay = Math.abs(dy);
      double dist = Math.max(ax,ay);
      int cdir = (ay>ax? (dy>0? 2:0):(dx>0? 3:1));
      if (dist<destdist && z.canSee(x,y,cx,cy,cdir)) { 
        destplayer = i;
        destdist = dist;
        destdir = cdir;
      } 
    }
    double dx = px-cx, dy = py-cy, ax = Math.abs(dx), ay = Math.abs(dy);
    double dist = Math.max(ax,ay);
    int cdir = (ay>ax? (dy>0? 2:0):(dx>0? 3:1));
    if (dist<destdist && (destdist==1000 || z.canSee(px,py,cx,cy,cdir)) &&
        z.gamestate!=ZeldaMain.PAUSED) {
      destplayer = -1;
      destdist = dist;
      destdir = cdir;
    } 
    return destdir;
  }

  private void hurtFly() {
    if (anicount>=3) {
      x += hurtdx;
      y += hurtdy;
      boolean onwall = false;
      for (int dir=0; dir<4; dir++) for (int i=0; i<3; i++)
        onwall = onwall || z.isOnWall(x+test1[dir][i][0],y+test1[dir][i][1]);
      if (onwall) {
        x -= hurtdx;
        y -= hurtdy;
      }
    }
 
    anicount--;
    if (anicount<=0) {
      if (power>0) { 
        mode = HUNT;
        anicount = 12;
        dir = headdir = calcDir();
        seecount = 0;
      } else {
        mode = DIE;
        anicount = 14;
        putRandomExtras();
      }
    } else if (anicount<=3 && type==OCTOPUS) {
      mode = DIE;
      anicount = 9;
      putRandomExtras();
    }
  }

  private void putRandomExtras() {
    int p = (int)(Math.random()*100);
    if (p<10) { z.putExtra(x,y,136,true); return; }
    if (p<25) { z.putExtra(x,y,139,true); return; }
    if (p<30) { z.putExtra(x,y,137,true); return; }
    if (type==SHOTSOLDIER || type==SWAMPSOLDIER)
      if (p<75) { z.putExtra(x,y,143,true); return; }
  }

}