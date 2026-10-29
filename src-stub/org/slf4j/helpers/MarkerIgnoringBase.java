package org.slf4j.helpers;
import org.slf4j.Logger;
import java.io.Serializable;
public abstract class MarkerIgnoringBase implements Logger, Serializable { protected String name; public String getName(){return name;} }