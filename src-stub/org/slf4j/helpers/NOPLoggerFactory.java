package org.slf4j.helpers;
import org.slf4j.*;
public class NOPLoggerFactory implements ILoggerFactory {
    public Logger getLogger(String name){return LoggerFactory.getLogger(name);}
}