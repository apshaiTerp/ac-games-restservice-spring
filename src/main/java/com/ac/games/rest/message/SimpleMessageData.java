package com.ac.games.rest.message;

/**
 * @author ac010168
 *
 */
public class SimpleMessageData {
  
  /** A placeholder for the error type.  This may get converted to an enum at some point. */
  private final String errorType;
  /** The text we want returned for this message. */
  private final String errorMessage;
  
  public SimpleMessageData(String errorType, String errorMessage) {
    this.errorType    = errorType;
    this.errorMessage = errorMessage;
  }

  /**
   * @return the errorType
   */
  public String getErrorType() {
    return errorType;
  }

  /**
   * @return the errorMessage
   */
  public String getErrorMessage() {
    return errorMessage;
  }
}