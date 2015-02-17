package com.ac.games.rest.controller;

import java.util.Arrays;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.ac.games.data.MiniatureMarketPriceData;
import com.ac.games.data.parser.MiniatureMarketParser;
import com.ac.games.db.GamesDatabase;
import com.ac.games.db.MongoDBFactory;
import com.ac.games.db.exception.ConfigurationException;
import com.ac.games.db.exception.DatabaseOperationException;
import com.ac.games.exception.GameNotFoundException;
import com.ac.games.rest.message.SimpleErrorData;
import com.ac.games.rest.message.SimpleMessageData;

/**
 * This class should be the intercepter for REST service access to the MiniatureMarket game
 * information.
 * <p>
 * It should handle all request that come in under the /external/mmdata entry.
 * <p>
 * Refer to the individual methods to determine the parameter lists.
 * 
 * @author ac010168
 */
@RestController
@RequestMapping("/external/mmdata")
public class MMDataController {

  /** The standard URI template by which games can be accessed by csiid */
  public final static String URL_TEMPLATE = "http://www.miniaturemarket.com/catalog/product/view/id/<mmid>";
  /** The replacement marker in the URL_TEMPLATE */
  public final static String MMID_MARKER  = "<mmid>";
  
  /**
   * GET method designed to handle retrieving the Miniature Market content from the
   * miniaturemarket website and return the formatted {@link MiniatureMarketPriceData} object.
   * <p>
   * This method supports the following parameters:
   * <ul>
   * <li><code>mmid=&lt;gameid&gt;</code> - The gameID.  This is required.</li>
   * <li><code>source=&lt;mm|db&gt;</code> - This indicated whether to request the game from BoardGameGeek (bgg)
   * or from our cached database (db).  Default is mm.</li></ul>
   * 
   * @param mmID
   * @return A {@link MiniatureMarketPriceData} object or {@link SimpleErrorData} message reporting the failure
   */
  @RequestMapping(method = RequestMethod.GET, produces="application/json")
  public Object getCSIData(@RequestParam(value="mmid") long mmID, @RequestParam(value="source", defaultValue="mm") String source) {
    if ((!source.equalsIgnoreCase("mm")) && (!source.equalsIgnoreCase("db")))
      return new SimpleErrorData("Invalid Parameters", "The source parameter value of " + source + " is not a valid source value.");
  
    if (source.equalsIgnoreCase("mm")) {
      //Create the RestTemplate to access the external HTML page
      RestTemplate restTemplate = new RestTemplate();
      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(Arrays.asList(MediaType.TEXT_HTML));
      HttpEntity<String> entity = new HttpEntity<String>("parameters", headers);
      
      //Run the GET command to retrieve the HTML Body
      ResponseEntity<String> gameResponse = restTemplate.exchange(URL_TEMPLATE.replace(MMID_MARKER, "" + mmID), 
          HttpMethod.GET, entity, String.class);

      String htmlText = gameResponse.getBody();
      MiniatureMarketPriceData data = null;
      try {
        data = MiniatureMarketParser.parseMMHTML(htmlText);
      } catch (GameNotFoundException gnfe) {
        System.out.println ("I could not find this game.");
        return new SimpleErrorData("Game Not Found", "The requested csiid of " + mmID + " could not be found.");
      } catch (Throwable t) {
        System.out.println ("Something terribly wrong happened here...");
        t.printStackTrace();
        return new SimpleErrorData("Operation Error", "An error has occurred: " + t.getMessage());
      }

      return data;
    } else {
      GamesDatabase database = MongoDBFactory.getMongoGamesDatabase();
      MiniatureMarketPriceData data = null;
      try {
        data = database.readMMPriceData(mmID);
        if (data == null)
          return new SimpleErrorData("Game Not Found", "The requested item could not be found in the database.");
      } catch (DatabaseOperationException doe) {
        doe.printStackTrace();
        return new SimpleErrorData("Database Operation Error", "An error occurred running the request: " + doe.getMessage());
      } catch (ConfigurationException ce) {
        ce.printStackTrace();
        return new SimpleErrorData("Database Configuration Error", "An error occurred accessing the database: " + ce.getMessage());
      }
      
      return data;
    }
  }  

  /**
   * PUT Method, which should update (or potentially upsert) the provided data object.
   * 
   * @param data
   * 
   * @return A {@link SimpleMessageData} or {@link SimpleErrorData} message indicating the operation status
   */
  @RequestMapping(method = RequestMethod.PUT)
  public Object putMMData(@RequestBody MiniatureMarketPriceData data) {
    if (data == null)
      return new SimpleErrorData("Game Data Error", "There was no valid MM data provided");
    
    if (data.getMmID() < 0)
      return new SimpleErrorData("Game Data Invalid", "The provided game has no MM ID");
    
    GamesDatabase database = MongoDBFactory.getMongoGamesDatabase();
    try {
      database.updateMMPriceData(data);
    } catch (DatabaseOperationException doe) {
      doe.printStackTrace();
      return new SimpleErrorData("Database Operation Error", "An error occurred running the request: " + doe.getMessage());
    } catch (ConfigurationException ce) {
      ce.printStackTrace();
      return new SimpleErrorData("Database Configuration Error", "An error occurred accessing the database: " + ce.getMessage());
    }
    
    return new SimpleMessageData("Operation Successful", "The Put Request Completed Successfully");
  }
  
  /**
   * POST Method, which should insert (or potentially upsert) the provided game object.
   * 
   * @param data
   * 
   * @return A {@link SimpleMessageData} or {@link SimpleErrorData} message indicating the operation status
   */
  @RequestMapping(method = RequestMethod.POST)
  public Object postMMData(@RequestBody MiniatureMarketPriceData data) {
    if (data == null)
      return new SimpleErrorData("Game Data Error", "There was no valid MM data provided");
    
    if (data.getMmID() < 0)
      return new SimpleErrorData("Game Data Invalid", "The provided game has no MM ID");
    
    GamesDatabase database = MongoDBFactory.getMongoGamesDatabase();
    try {
      database.insertMMPriceData(data);
    } catch (DatabaseOperationException doe) {
      doe.printStackTrace();
      return new SimpleErrorData("Database Operation Error", "An error occurred running the request: " + doe.getMessage());
    } catch (ConfigurationException ce) {
      ce.printStackTrace();
      return new SimpleErrorData("Database Configuration Error", "An error occurred accessing the database: " + ce.getMessage());
    }
    
    return new SimpleMessageData("Operation Successful", "The Put Request Completed Successfully");
  }

  /**
   * DELETE Method, which should delete the provided game reference, if it exists
   * 
   * @param mmID
   * 
   * @return A {@link SimpleMessageData} or {@link SimpleErrorData} message indicating the operation status
   */
  @RequestMapping(method = RequestMethod.DELETE)
  public Object deleteMMData(@RequestBody long mmID) {
    if (mmID < 0)
      return new SimpleErrorData("Game Data Invalid", "The provided game has no MM ID");
    
    GamesDatabase database = MongoDBFactory.getMongoGamesDatabase();
    try {
      database.deleteMMPriceData(mmID);
    } catch (DatabaseOperationException doe) {
      doe.printStackTrace();
      return new SimpleErrorData("Database Operation Error", "An error occurred running the request: " + doe.getMessage());
    } catch (ConfigurationException ce) {
      ce.printStackTrace();
      return new SimpleErrorData("Database Configuration Error", "An error occurred accessing the database: " + ce.getMessage());
    }
    
    return new SimpleMessageData("Operation Successful", "The Put Request Completed Successfully");
  }
}