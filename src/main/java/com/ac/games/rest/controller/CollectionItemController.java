package com.ac.games.rest.controller;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ac.games.data.CollectionItem;
import com.ac.games.db.GamesDatabase;
import com.ac.games.db.MongoDBFactory;
import com.ac.games.db.exception.ConfigurationException;
import com.ac.games.db.exception.DatabaseOperationException;
import com.ac.games.rest.Application;
import com.ac.games.rest.message.SimpleErrorData;
import com.ac.games.rest.message.SimpleMessageData;

/**
 * @author ac010168
 *
 */
@RestController
@RequestMapping("/collectionitem")
public class CollectionItemController {
  /**
   * GET method designed to handle retrieving {@link CollectionItem} data from the database.
   * This method supports the following parameters:
   * 
   * <ul>
   * <li><code>itemid=&lt;itemID&gt;</code></li>
   * </ul>
   * 
   * @param itemID
   * 
   * @return A {@link CollectionItem} object or {@link SimpleErrorData} message reporting what failed.
   */
  @RequestMapping(method = RequestMethod.GET, produces="application/json;charset=UTF-8")
  public Object getCollectionItem(@RequestParam(value="itemid") long itemID) {
    if (itemID <= 0)
      return new SimpleErrorData("CollectionItem Data Error", "There was no valid collection request data provided");
      
    GamesDatabase database = null; 
    CollectionItem collection  = null;
    try {
      database = MongoDBFactory.createMongoGamesDatabase(Application.databaseHost, Application.databasePort, Application.databaseName);
      database.initializeDBConnection();
      
      collection = database.readCollectionItem(itemID);
    } catch (DatabaseOperationException doe) {
      doe.printStackTrace();
      try { if (database != null) database.closeDBConnection(); } catch (Throwable t2) { /** Ignore Errors */ }
      return new SimpleErrorData("Database Operation Error", "An error occurred running the request: " + doe.getMessage());
    } catch (ConfigurationException ce) {
      ce.printStackTrace();
      try { if (database != null) database.closeDBConnection(); } catch (Throwable t2) { /** Ignore Errors */ }
      return new SimpleErrorData("Database Configuration Error", "An error occurred accessing the database: " + ce.getMessage());
    } finally {
      try { if (database != null) database.closeDBConnection(); } catch (Throwable t2) { /** Ignore Errors */ }
    }
    
    if (collection == null)
      return new SimpleErrorData("Item Not Found", "The requested collection could not be found in the database.");
    return collection;
  }
  
  /**
   * A simple PUT command to update the user.  This will update all fields for a collection, but
   * the itemID cannot be changed.  This value must match the param itemID.
   * 
   * @param itemID The itemID we intend to update.  This is required
   * @param collection   The collection data we want to update.  This is required.
   * 
   * @return a {@link SimpleMessageData} indicating the operation succeeded, or a {@link SimpleErrorData} 
   * indicating it failed.
   */
  @RequestMapping(method = RequestMethod.PUT, consumes = "application/json;charset=UTF-8", produces="application/json;charset=UTF-8")
  public Object putCollectionItem(@RequestParam(value="itemid", defaultValue="-1") long itemID,
                                  @RequestBody CollectionItem item) {
    if (itemID <= 0)
      return new SimpleErrorData("CollectionItem Data Error", "There was no valid CollectionItem request data provided");
    if (item == null)
      return new SimpleErrorData("CollectionItem Data Error", "There was no valid CollectionItem request data provided");
    if (itemID != item.getItemID())
      return new SimpleErrorData("CollectionItem Data Error", "The itemID parameter and itemID value of the RequestBody do not match");

    GamesDatabase database = null; 
    try {
      database = MongoDBFactory.createMongoGamesDatabase(Application.databaseHost, Application.databasePort, Application.databaseName);
      database.initializeDBConnection();
      
      database.updateCollectionItem(item);
    } catch (DatabaseOperationException doe) {
      doe.printStackTrace();
      try { if (database != null) database.closeDBConnection(); } catch (Throwable t2) { /** Ignore Errors */ }
      return new SimpleErrorData("Database Operation Error", "An error occurred running the request: " + doe.getMessage());
    } catch (ConfigurationException ce) {
      ce.printStackTrace();
      try { if (database != null) database.closeDBConnection(); } catch (Throwable t2) { /** Ignore Errors */ }
      return new SimpleErrorData("Database Configuration Error", "An error occurred accessing the database: " + ce.getMessage());
    } finally {
      try { if (database != null) database.closeDBConnection(); } catch (Throwable t2) { /** Ignore Errors */ }
    }
    
    return new SimpleMessageData("Operation Successful", "The Put Request Completed Successfully");
  }
  
  /**
   * A simple POST command to update the user.  This will insert a new collection.
   * 
   * @param collection   The collection data we want to insert.  This is required.
   * 
   * @return a {@link SimpleMessageData} indicating the operation succeeded, or a {@link SimpleErrorData} 
   * indicating it failed.
   */
  @RequestMapping(method = RequestMethod.POST, consumes = "application/json;charset=UTF-8", produces="application/json;charset=UTF-8")
  public Object postCollectionItem(@RequestBody CollectionItem item) {
    if (item == null)
      return new SimpleErrorData("CollectionItem Data Error", "There was no valid CollectionItem request data provided");

    GamesDatabase database = null; 
    try {
      database = MongoDBFactory.createMongoGamesDatabase(Application.databaseHost, Application.databasePort, Application.databaseName);
      database.initializeDBConnection();
      
      if (item.getItemID() == -1)
        item.setItemID(database.getMaxCollectionItemID() + 1);
      
      database.insertCollectionItem(item);
    } catch (DatabaseOperationException doe) {
      doe.printStackTrace();
      try { if (database != null) database.closeDBConnection(); } catch (Throwable t2) { /** Ignore Errors */ }
      return new SimpleErrorData("Database Operation Error", "An error occurred running the request: " + doe.getMessage());
    } catch (ConfigurationException ce) {
      ce.printStackTrace();
      try { if (database != null) database.closeDBConnection(); } catch (Throwable t2) { /** Ignore Errors */ }
      return new SimpleErrorData("Database Configuration Error", "An error occurred accessing the database: " + ce.getMessage());
    } finally {
      try { if (database != null) database.closeDBConnection(); } catch (Throwable t2) { /** Ignore Errors */ }
    }
    
    return new SimpleMessageData("Operation Successful", "The Post Request Completed Successfully");
  }
  
  /**
   * DELETE Method, which should delete the provided collection reference, if it exists
   * 
   * @param itemID
   * 
   * @return A {@link SimpleMessageData} or {@link SimpleErrorData} message indicating the operation status
   */
  @RequestMapping(method = RequestMethod.DELETE, produces="application/json;charset=UTF-8")
  public Object deleteCollectionItem(@RequestParam(value="itemid", defaultValue="-1") long itemID) {
    if (itemID <= 0)
      return new SimpleErrorData("CollectionItem Data Error", "There was no valid CollectionItem request data provided");

    GamesDatabase database = null; 
    try {
      database = MongoDBFactory.createMongoGamesDatabase(Application.databaseHost, Application.databasePort, Application.databaseName);
      database.initializeDBConnection();
      
      //First we need to see if this user exists.  If it does, reject the request.
      CollectionItem existCollectionItem = database.readCollectionItem(itemID);
      if (existCollectionItem == null) {
        try { if (database != null) database.closeDBConnection(); } catch (Throwable t2) { /** Ignore Errors */ }
        return new SimpleErrorData("No Such CollectionItem", "No CollectionItem with this ID Exists");
      }
      
      database.deleteCollectionItem(itemID);
      
    } catch (DatabaseOperationException doe) {
      doe.printStackTrace();
      try { if (database != null) database.closeDBConnection(); } catch (Throwable t2) { /** Ignore Errors */ }
      return new SimpleErrorData("Database Operation Error", "An error occurred running the request: " + doe.getMessage());
    } catch (ConfigurationException ce) {
      ce.printStackTrace();
      try { if (database != null) database.closeDBConnection(); } catch (Throwable t2) { /** Ignore Errors */ }
      return new SimpleErrorData("Database Configuration Error", "An error occurred accessing the database: " + ce.getMessage());
    } finally {
      try { if (database != null) database.closeDBConnection(); } catch (Throwable t2) { /** Ignore Errors */ }
    }
    
    return new SimpleMessageData("Operation Successful", "The Delete Request Completed Successfully");
  }
}
