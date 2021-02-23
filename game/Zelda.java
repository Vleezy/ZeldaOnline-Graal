import java.awt.*;

public class Zelda {

  public static void main(String[] args) {
    Frame f = new ZeldaWindow("Zelda Start",568,380);
    f.add("Center",new StartPanel(f));
    f.show();
  }

}

class ZeldaWindow extends java.awt.Frame {
  MapPanel mappanel;
  
  ZeldaWindow(String title, int winw, int winh) {
    super(title);
    setBackground(Color.lightGray);
    setLayout(new BorderLayout());
    setResizable(false);
    pack();
    reshape(50,50,winw+insets().left+insets().right,winh+insets().top+insets().bottom);
  }

  private void hideWindow() {
    if (mappanel!=null) {
      mappanel.takeOptions();
      hide();
    } else dispose();
    if (getTitle().equals("Zelda Online")) 
      System.exit(0);
  }

  public synchronized boolean handleEvent (Event e) {
    switch  (e.id) {
      case Event.WINDOW_DESTROY: { hideWindow(); return true; }
      case Event.ACTION_EVENT: {
        if (!(e.target instanceof Button)) break;
        if (e.arg.equals("Cancel") || e.arg.equals("Close")) hideWindow();
      }
    }
    return super.handleEvent(e);
  }
}


class StartPanel extends Panel {
  Frame frame;
  Image img;
  Font bigfont = new Font("Dialog",Font.BOLD,36);
  Font normfont = new Font("Dialog",Font.PLAIN,13);
  Checkbox check1, check2;
  TextField text  = new TextField("127.0.0.1");
  boolean loading = false;

  StartPanel(Frame frame){
    this.frame = frame;

    setLayout(null);
    setFont(normfont);
    Button but1 = new Button("Play"), but2 = new Button("Cancel");
    add(but1);
    add(but2);
    but1.reshape(341,341,100,28);
    but2.reshape(455,341,100,28);

    CheckboxGroup check = new CheckboxGroup();
    check1 = new Checkbox("One-Player mode (124k)",check,true);
    check2 = new Checkbox("Multiplayer mode (133k)",check,false);
    add(check1);
    add(check2);
    check1.reshape(205,180,320,30);
    check2.reshape(205,210,320,30);

    Label label = new Label("Server IP address:");
    add(label);
    add(text);
    label.reshape(205,245,140,30);
    text.reshape(350,245,200,30);

    img = getToolkit().getImage("startsprite.gif");
  }

  public void paint(Graphics g) {
    if (loading) {
      g.setColor(Color.lightGray);
      g.fillRect(0,0,size().width,size().height);
      g.setFont(bigfont);
      String str = "loading game...";
      int w = getFontMetrics(g.getFont()).stringWidth(str);
      g.setColor(Color.white);
      g.drawString(str,(size().width-w)/2,150);
      return;
    }
    g.setColor(new Color(0,0x7F,0x7F));
    g.fillRect(13,13,160,275);
    g.setColor(Color.white);
    g.draw3DRect(13,13,159,274,false);
    g.draw3DRect(13,323,541,1,false);
    g.drawImage(img,60,105,56,118,this);

    g.setFont(bigfont);
    g.setColor(Color.gray);
    g.drawString("Zelda online",230,60);
    g.setFont(normfont);
    g.setColor(Color.black);
    g.drawString("Select a mode. Both modes contain the same",205,100);
    g.drawString("levels and opponents. The multiplayer mode",205,120);
    g.drawString("additionally lets you play against other players,",205,140);
    g.drawString("but that mode may be slower on your machine.",205,160);
  }

  public synchronized boolean handleEvent (Event e) {
    switch  (e.id) {
      case Event.ACTION_EVENT: {
        if (e.arg.equals("Play")) { 
          loading = true;
          removeAll();
          frame.setTitle("Zelda Online");
          paint(getGraphics());
          ZeldaMain z = new ZeldaMain(check2.getState());
          z.serverhost = text.getText();
          frame.resize(512+frame.insets().left+frame.insets().right,
            482+frame.insets().top+frame.insets().bottom);
          frame.remove(this);
          frame.add("Center",z);
          z.init();
          frame.validate();
        }
      }
    }
    return super.handleEvent(e);
  }
}

