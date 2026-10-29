package org.slf4j;
public class LoggerFactory {
    public static Logger getLogger(String name)    { return NOPLog.INSTANCE; }
    public static Logger getLogger(Class<?> clazz) { return NOPLog.INSTANCE; }
    static class NOPLog implements Logger {
        static final NOPLog INSTANCE = new NOPLog();
        public String getName(){return"NOP";}
        public boolean isTraceEnabled(){return false;} public boolean isDebugEnabled(){return false;}
        public boolean isInfoEnabled(){return false;}  public boolean isWarnEnabled(){return false;}
        public boolean isErrorEnabled(){return false;}
        public void trace(String m){} public void trace(String f,Object a){} public void trace(String f,Object a,Object b){} public void trace(String f,Object...a){} public void trace(String m,Throwable t){}
        public void debug(String m){} public void debug(String f,Object a){} public void debug(String f,Object a,Object b){} public void debug(String f,Object...a){} public void debug(String m,Throwable t){}
        public void info(String m){}  public void info(String f,Object a){}  public void info(String f,Object a,Object b){}  public void info(String f,Object...a){}  public void info(String m,Throwable t){}
        public void warn(String m){}  public void warn(String f,Object a){}  public void warn(String f,Object a,Object b){}  public void warn(String f,Object...a){}  public void warn(String m,Throwable t){}
        public void error(String m){} public void error(String f,Object a){} public void error(String f,Object a,Object b){} public void error(String f,Object...a){} public void error(String m,Throwable t){}
    }
}