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

import com.ac.games.data.BGGGame;
import com.ac.games.data.parser.BGGGameParser;
import com.ac.games.db.GamesDatabase;
import com.ac.games.db.MongoDBFactory;
import com.ac.games.db.exception.ConfigurationException;
import com.ac.games.db.exception.DatabaseOperationException;
import com.ac.games.exception.GameNotFoundException;
import com.ac.games.rest.message.SimpleErrorData;
import com.ac.games.rest.message.SimpleMessageData;

/**
 * This class should be the intercepter for REST service access to the BoardGameGeek game
 * information.
 * <p>
 * It should handle all request that come in under the /external/bggdata entry.
 * <p>
 * Refer to the individual methods to determine the parameter lists.
 * 
 * @author ac010168
 */
@RestController
@RequestMapping("/external/bggdata")
public class BGGDataController {

  /** The standard URI template by which games can be accessed by bggid */
  public final static String URL_TEMPLATE = "http://www.boardgamegeek.com/xmlapi/boardgame/<bggid>?stats=1";
  /** The replacement marker in the URL_TEMPLATE */
  public final static String BGGID_MARKER = "<bggid>";
  
  /**
   * GET method designed to handle retrieving the BoardGameGeek content from the
   * BGG XML API and return the formatted {@link BGGGame} object.
   * <p>
   * This method supports the following parameters:
   * <ul>
   * <li><code>bggid=&lt;gameid&gt;</code> - The gameID.  This is required.</li>
   * <li><code>source=&lt;bgg|db&gt;</code> - This indicated whether to request the game from BoardGameGeek (bgg)
   * or from our cached database (db).  Default is bgg.</li></ul>
   * 
   * @param bggID
   * 
   * @return A {@link BGGGame} object or {@link SimpleErrorData} message reporting the failure
   */
  @RequestMapping(method = RequestMethod.GET, produces="application/json")
  public Object getBGGData(@RequestParam(value="bggid") long bggID, @RequestParam(value="source", defaultValue="bgg") String source) {
    if ((!source.equalsIgnoreCase("bgg")) && (!source.equalsIgnoreCase("db")))
      return new SimpleErrorData("Invalid Parameters", "The source parameter value of " + source + " is not a valid source value.");
    
    if (source.equalsIgnoreCase("bgg")) {
      //Create the RestTemplate to access the external XML API
      RestTemplate restTemplate = new RestTemplate();
      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(Arrays.asList(MediaType.TEXT_XML));
      HttpEntity<String> entity = new HttpEntity<String>("parameters", headers);
      
      //Run the GET command to retrieve the XML Body
      ResponseEntity<String> gameResponse = restTemplate.exchange(URL_TEMPLATE.replace(BGGID_MARKER, "" + bggID), 
          HttpMethod.GET, entity, String.class);
      
      String xmlText = gameResponse.getBody();
      BGGGame game = null;
      try {
        game = BGGGameParser.parseBGGXML(xmlText);
      } catch (GameNotFoundException gnfe) {
        System.out.println ("I could not find this game.");
        return new SimpleErrorData("Game Not Found", "The requested bggid of " + bggID + " could not be found.");
      } catch (Throwable t) {
        System.out.println ("Something terribly wrong happened here...");
        t.printStackTrace();
        return new SimpleErrorData("Operation Error", "An error has occurred: " + t.getMessage());
      }

      return game;
    } else {
      GamesDatabase database = MongoDBFactory.getMongoGamesDatabase();
      BGGGame game = null;
      try {
        game = database.readBGGGameData(bggID);
        if (game == null)
          return new SimpleErrorData("Game Not Found", "The requested item could not be found in the database.");
      } catch (DatabaseOperationException doe) {
        doe.printStackTrace();
        return new SimpleErrorData("Database Operation Error", "An error occurred running the request: " + doe.getMessage());
      } catch (ConfigurationException ce) {
        ce.printStackTrace();
        return new SimpleErrorData("Database Configuration Error", "An error occurred accessing the database: " + ce.getMessage());
      }
      
      return game;
    }
  }  

  /**
   * PUT Method, which should update (or potentially upsert) the provided game object.
   * 
   * @param game
   * 
   * @return A {@link SimpleMessageData} or {@link SimpleErrorData} message indicating the operation status
   */
  @RequestMapping(method = RequestMethod.PUT)
  public Object putBGGData(@RequestBody BGGGame game) {
    if (game == null)
      return new SimpleErrorData("Game Data Error", "There was no valid BGGGame data provided");
    
    if (game.getBggID() < 0)
      return new SimpleErrorData("Game Data Invalid", "The provided game has no Game ID");
    
    GamesDatabase database = MongoDBFactory.getMongoGamesDatabase();
    try {
      database.updateBGGGameData(game);
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
   * @param game
   * 
   * @return A {@link SimpleMessageData} or {@link SimpleErrorData} message indicating the operation status
   */
  @RequestMapping(method = RequestMethod.POST)
  public Object postBGGData(@RequestBody BGGGame game) {
    if (game == null)
      return new SimpleErrorData("Game Data Error", "There was no valid BGGGame data provided");
    
    if (game.getBggID() < 0)
      return new SimpleErrorData("Game Data Invalid", "The provided game has no Game ID");
    
    GamesDatabase database = MongoDBFactory.getMongoGamesDatabase();
    try {
      database.insertBGGGameData(game);
    } catch (DatabaseOperationException doe) {
      doe.printStackTrace();
      return new SimpleErrorData("Database Operation Error", "An error occurred running the request: " + doe.getMessage());
    } catch (ConfigurationException ce) {
      ce.printStackTrace();
      return new SimpleErrorData("Database Configuration Error", "An error occurred accessing the database: " + ce.getMessage());
    }
    
    return new SimpleMessageData("Operation Successful", "The Post Request Completed Successfully");
  }

  /**
   * DELETE Method, which should delete the provided game reference, if it exists
   * 
   * @param bggID
   * 
   * @return A {@link SimpleMessageData} or {@link SimpleErrorData} message indicating the operation status
   */
  @RequestMapping(method = RequestMethod.DELETE)
  public Object deleteBGGData(@RequestBody long bggID) {
    if (bggID <= 0)
      return new SimpleErrorData("Game Data Invalid", "The provided game has no Game ID");
    
    GamesDatabase database = MongoDBFactory.getMongoGamesDatabase();
    try {
      database.deleteBGGGameData(bggID);
    } catch (DatabaseOperationException doe) {
      doe.printStackTrace();
      return new SimpleErrorData("Database Operation Error", "An error occurred running the request: " + doe.getMessage());
    } catch (ConfigurationException ce) {
      ce.printStackTrace();
      return new SimpleErrorData("Database Configuration Error", "An error occurred accessing the database: " + ce.getMessage());
    }
    
    return new SimpleMessageData("Operation Successful", "The Delete Request Completed Successfully");
  }
}