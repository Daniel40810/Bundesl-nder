package org.apache.commons.logging;

public class LogFactory {

    public static Log getLog(Class<?> clazz) {
        return new SimpleLog(clazz != null ? clazz.getName() : "");
    }

    public static Log getLog(String name) {
        return new SimpleLog(name != null ? name : "");
    }

    private static final class SimpleLog implements Log {
        private final String name;

        SimpleLog(String name) {
            this.name = name;
        }

        @Override public boolean isDebugEnabled() { return false; }
        @Override public boolean isErrorEnabled() { return true; }
        @Override public boolean isFatalEnabled() { return true; }
        @Override public boolean isInfoEnabled()  { return false; }
        @Override public boolean isTraceEnabled() { return false; }
        @Override public boolean isWarnEnabled()  { return true; }

        @Override public void trace(Object message) { }
        @Override public void trace(Object message, Throwable t) { }
        @Override public void debug(Object message) { }
        @Override public void debug(Object message, Throwable t) { }
        @Override public void info(Object message) { }
        @Override public void info(Object message, Throwable t) { }
        @Override public void warn(Object message) { print("WARN", message, null); }
        @Override public void warn(Object message, Throwable t) { print("WARN", message, t); }
        @Override public void error(Object message) { print("ERROR", message, null); }
        @Override public void error(Object message, Throwable t) { print("ERROR", message, t); }
        @Override public void fatal(Object message) { print("FATAL", message, null); }
        @Override public void fatal(Object message, Throwable t) { print("FATAL", message, t); }

        private void print(String level, Object message, Throwable t) {
            System.err.println("[JXMapViewer2 " + level + "] " + name + ": " + message);
            if (t != null) {
                t.printStackTrace(System.err);
            }
        }
    }
}
