package org.slf4j;
public interface Logger {
    String getName();
    boolean isTraceEnabled(); void trace(String m); void trace(String f,Object a); void trace(String f,Object a,Object b); void trace(String f,Object...a); void trace(String m,Throwable t);
    boolean isDebugEnabled(); void debug(String m); void debug(String f,Object a); void debug(String f,Object a,Object b); void debug(String f,Object...a); void debug(String m,Throwable t);
    boolean isInfoEnabled();  void info(String m);  void info(String f,Object a);  void info(String f,Object a,Object b);  void info(String f,Object...a);  void info(String m,Throwable t);
    boolean isWarnEnabled();  void warn(String m);  void warn(String f,Object a);  void warn(String f,Object a,Object b);  void warn(String f,Object...a);  void warn(String m,Throwable t);
    boolean isErrorEnabled(); void error(String m); void error(String f,Object a); void error(String f,Object a,Object b); void error(String f,Object...a); void error(String m,Throwable t);
}