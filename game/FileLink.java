import java.awt.*;

class FileLink {

  String filename="";
  double x, y, w, h, newx, newy;
  boolean constantx=true, constanty=true;

  FileLink(String str) {
    int index = str.indexOf(" ");
    filename = str.substring(0,index);
    str = str.substring(index+1);
    index = str.indexOf(" ");
    x = new Double(str.substring(0,index)).doubleValue();
    str = str.substring(index+1);
    index = str.indexOf(" ");
    y = new Double(str.substring(0,index)).doubleValue();
    str = str.substring(index+1);
    index = str.indexOf(" ");
    w = new Double(str.substring(0,index)).doubleValue();
    str = str.substring(index+1);
    index = str.indexOf(" ");
    h = new Double(str.substring(0,index)).doubleValue();
    str = str.substring(index+1);
    index = str.indexOf(" ");
    newx = new Double(str.substring(0,index)).doubleValue();
    if (newx>=0) constantx = false;
    str = str.substring(index+1);
    newy = new Double(str).doubleValue();
    if (newy>=0) constanty = false;
  }

  public boolean inside(double xf, double yf) {
    return (xf>=x && xf<=x+w && yf>=y && yf<y+h);
  }


}
