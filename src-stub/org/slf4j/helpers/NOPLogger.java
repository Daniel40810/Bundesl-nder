package org.slf4j.helpers;
public class NOPLogger extends MarkerIgnoringBase {
    public static final NOPLogger NOP_LOGGER=new NOPLogger();
    private NOPLogger(){this.name="NOP";}
    public boolean isTraceEnabled(){return false;} public boolean isDebugEnabled(){return false;}
    public boolean isInfoEnabled(){return false;}  public boolean isWarnEnabled(){return false;}
    public boolean isErrorEnabled(){return false;}
    public void trace(String m){} public void trace(String f,Object a){} public void trace(String f,Object a,Object b){} public void trace(String f,Object...a){} public void trace(String m,Throwable t){}
    public void debug(String m){} public void debug(String f,Object a){} public void debug(String f,Object a,Object b){} public void debug(String f,Object...a){} public void debug(String m,Throwable t){}
    public void info(String m){}  public void info(String f,Object a){}  public void info(String f,Object a,Object b){}  public void info(String f,Object...a){}  public void info(String m,Throwable t){}
    public void warn(String m){}  public void warn(String f,Object a){}  public void warn(String f,Object a,Object b){}  public void warn(String f,Object...a){}  public void warn(String m,Throwable t){}
    public void error(String m){} public void error(String f,Object a){} public void error(String f,Object a,Object b){} public void error(String f,Object...a){} public void error(String m,Throwable t){}
}