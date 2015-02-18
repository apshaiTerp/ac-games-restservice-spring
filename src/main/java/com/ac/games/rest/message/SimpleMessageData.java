package com.ac.games.rest.message;

/**
 * @author ac010168
 *
 */
public class SimpleMessageData {
  
  /** A placeholder for the error type.  This may get converted to an enum at some point. */
  private final String messageType;
  /** The text we want returned for this message. */
  private final String message;
  
  public SimpleMessageData(String messageType, String message) {
    this.messageType = messageType;
    this.message     = message;
  }

  /**
   * @return the errorType
   */
  public String getMessageType() {
    return messageType;
  }

  /**
   * @return the errorMessage
   */
  public String getMessage() {
    return message;
  }
}
