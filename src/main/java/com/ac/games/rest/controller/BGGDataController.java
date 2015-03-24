package com.ac.games.rest.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.ac.games.data.BGGGame;
import com.ac.games.data.parser.BGGGameParser;
import com.ac.games.db.GamesDatabase;
import com.ac.games.db.MongoDBFactory;
import com.ac.games.db.exception.ConfigurationException;
import com.ac.games.db.exception.DatabaseOperationException;
import com.ac.games.exception.GameNotFoundException;
import com.ac.games.rest.Application;
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
   * <li><code>source=&lt;bgg|db|hybrid&gt;</code> - This indicated whether to request the game from BoardGameGeek (bgg)
   * or from our cached database (db).  Default is bgg.</li>
   * <li><code>batch=n</code> - This indicates whether to generate a batch on game requests including bggID and up
   * to 'n' additional sequential elements.  Default is 1.</li></ul>
   * 
   * @param bggID The bggID that we are using to base this request on.
   * @param source The source, either bgg or db we are requesting data from
   * @param batch The number of rows to retrieve in batch from the server
   * @param sync  A flag indicating whether we should automatically post an update if we determine that newly read content
   * has changed.  This simplifies front-end workflow.
   * 
   * @return A {@link BGGGame} object or {@link SimpleErrorData} message reporting the failure
   */
  @SuppressWarnings("unchecked")
  @RequestMapping(method = RequestMethod.GET, produces="application/json;charset=UTF-8")
  public Object getBGGData(@RequestParam(value="bggid") long bggID, 
                           @RequestParam(value="source", defaultValue="bgg") String source,
                           @RequestParam(value="batch", defaultValue="1") int batch,
                           @RequestParam(value="sync", defaultValue="n") String sync) {
    
    if ((!source.equalsIgnoreCase("bgg")) && (!source.equalsIgnoreCase("db")) && (!source.equalsIgnoreCase("hybrid")))
      return new SimpleErrorData("Invalid Parameters", "The source parameter value of " + source + " is not a valid source value.");
    if ((!sync.equalsIgnoreCase("n")) && (!sync.equalsIgnoreCase("y")))
      return new SimpleErrorData("Invalid Parameters", "The sync parameter value of " + sync + " is not a valid sync value");
    
    List<BGGGame> bggSources = new LinkedList<BGGGame>();
    List<BGGGame> dbSources  = new LinkedList<BGGGame>();
    
    if (source.equalsIgnoreCase("bgg") || source.equalsIgnoreCase("hybrid")) {
      //Create the RestTemplate to access the external XML API
      RestTemplate restTemplate = new RestTemplate();
      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(Arrays.asList(MediaType.TEXT_XML));
      HttpEntity<String> entity = new HttpEntity<String>("parameters", headers);
      
      Object gameResult = null;
      try {
        //Build the ID string (or list of bggIDs) that need to be submitted to BGG.
        String bggIDString = "" + bggID;
        for (int offset = 1; offset < batch; offset++)
          bggIDString += "," + (bggID + offset);
        
        System.out.println ("Beginning to parse BGG ID(s): " + bggIDString + "...");
        
        //Run the GET command to retrieve the XML Body
        ResponseEntity<String> gameResponse = restTemplate.exchange(URL_TEMPLATE.replace(BGGID_MARKER, bggIDString), 
            HttpMethod.GET, entity, String.class);

        String xmlText = gameResponse.getBody();
        if (batch > 1) gameResult = BGGGameParser.parseMultiBGGXML(xmlText);
        else           gameResult = BGGGameParser.parseBGGXML(xmlText);
        
      } catch (GameNotFoundException gnfe) {
        System.out.println ("I could not find this game.");
        return new SimpleErrorData("Game Not Found", "The requested bggid of " + bggID + " could not be found.");
      } catch (HttpServerErrorException hsee) {
        if (hsee.getMessage().contains("503 Service Unavailable")) {
          System.out.println ("The BGG server is icing me out again...");
          return new SimpleErrorData("Server Timeout 503", "The BGG server has stopped answering my requests");
        } else {
          System.out.println ("Something probably wrong happened here...");
          hsee.printStackTrace();
          return new SimpleErrorData("Operation Error", "An error has occurred: " + hsee.getMessage());
        }
      } catch (HttpClientErrorException hcee) {
        if (hcee.getMessage().contains("404 Not Found")) {
          System.out.println ("I could not find this game.");
          return new SimpleErrorData("Game Not Found", "The requested bggid of " + bggID + " could not be found.");
        } else {
          System.out.println ("Something probably wrong happened here...");
          hcee.printStackTrace();
          return new SimpleErrorData("Operation Error", "An error has occurred: " + hcee.getMessage());
        }
      } catch (Throwable t) {
        System.out.println ("Something terribly wrong happened here...");
        t.printStackTrace();
        return new SimpleErrorData("Operation Error", "An error has occurred: " + t.getMessage());
      }

      if (source.equalsIgnoreCase("bgg"))
        return gameResult;
      else {
        if (batch == 1) bggSources.add((BGGGame)gameResult);
        else            bggSources = (List<BGGGame>)gameResult;
      }
    } 
    if (source.equalsIgnoreCase("db") || source.equalsIgnoreCase("hybrid")) {
      GamesDatabase database = null; 
      BGGGame singleGame     = null;
      List<BGGGame> allGames = new LinkedList<BGGGame>();
      
      try {
        database = MongoDBFactory.createMongoGamesDatabase(Application.databaseHost, Application.databasePort, Application.databaseName);
        database.initializeDBConnection();
        
        if (batch == 1)
          singleGame = database.readBGGGameData(bggID);
        else {
          for (long i = bggID; i < (bggID + batch); i++) {
            singleGame = database.readBGGGameData(i);
            if (singleGame != null) 
              allGames.add(singleGame);
          }
        }
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
      
      if (batch == 1) {
        if (singleGame == null)
          return new SimpleErrorData("Game Not Found", "The requested item could not be found in the database.");
  
        if (source.equalsIgnoreCase("db"))
          return singleGame;
        else dbSources.add(singleGame);
      } else {
        if (allGames.size() == 0)
          return new SimpleErrorData("Game Not Found", "The requested item could not be found in the database.");
        
        if (source.equalsIgnoreCase("db"))
          return allGames;
        else dbSources = allGames;
      }
    }
    
    //If we made it this far, we're in hybrid mode, so we need to do our comparisons.
    //We need to keep all the Review States, and other fields that we made decisions about
    //otherwise prioritize new fields from BGG
    //Note, we need to check for nulls on any field that may have nulls.
    List<BGGGame> finalList = new ArrayList<BGGGame>(dbSources.size());
    for (BGGGame dbSource : dbSources) {
      BGGGame bggSource = null;
      for (BGGGame searchSource : bggSources) {
        if (searchSource.getBggID() == dbSource.getBggID()) {
          bggSource = searchSource;
          break;
        }
      }
      //If we didn't find a matching BGG item (for whatever reason, BGG can be flaky sometimes)
      if (bggSource == null)
        finalList.add(bggSource);
      else {
        if (!dbSource.getName().equalsIgnoreCase(bggSource.getName()))     dbSource.setName(bggSource.getName());
        if (dbSource.getYearPublished() != bggSource.getYearPublished())   dbSource.setYearPublished(bggSource.getYearPublished());
        if (dbSource.getMinPlayers() != bggSource.getMinPlayers())         dbSource.setMinPlayers(bggSource.getMinPlayers());
        if (dbSource.getMaxPlayers() != bggSource.getMaxPlayers())         dbSource.setMaxPlayers(bggSource.getMaxPlayers());
        if (dbSource.getMinPlayingTime() != bggSource.getMinPlayingTime()) dbSource.setMinPlayingTime(bggSource.getMinPlayingTime());
        if (dbSource.getMaxPlayingTime() != bggSource.getMaxPlayingTime()) dbSource.setMaxPlayingTime(bggSource.getMaxPlayingTime());
        
        if (dbSource.getBggRating() != bggSource.getBggRating())           dbSource.setBggRating(bggSource.getBggRating());
        if (dbSource.getBggRatingUsers() != bggSource.getBggRatingUsers()) dbSource.setBggRatingUsers(bggSource.getBggRatingUsers());
        if (dbSource.getBggRank() != bggSource.getBggRank())               dbSource.setBggRank(bggSource.getBggRank());

        if (dbSource.getParentGameID() != bggSource.getParentGameID())     dbSource.setParentGameID(bggSource.getParentGameID());
        if (dbSource.getGameType() != bggSource.getGameType())             dbSource.setGameType(bggSource.getGameType());

        if (dbSource.getImageURL() == null) dbSource.setImageURL(bggSource.getImageURL());
        else if (bggSource.getImageURL() != null) {
          if (dbSource.getImageURL().equalsIgnoreCase(bggSource.getImageURL()))                 
            dbSource.setImageURL(bggSource.getImageURL());
        }
        if (dbSource.getImageThumbnailURL() == null) dbSource.setImageThumbnailURL(bggSource.getImageThumbnailURL());
        else if (bggSource.getImageThumbnailURL() != null) {
          if (dbSource.getImageThumbnailURL().equalsIgnoreCase(bggSource.getImageThumbnailURL()))                 
            dbSource.setImageThumbnailURL(bggSource.getImageThumbnailURL());
        }
        if (dbSource.getDescription() == null) dbSource.setDescription(bggSource.getDescription());
        else if (bggSource.getDescription() != null) {
          if (dbSource.getDescription().equalsIgnoreCase(bggSource.getDescription()))                 
            dbSource.setDescription(bggSource.getDescription());
        }

        if (dbSource.getPublishers() == null) dbSource.setPublishers(bggSource.getPublishers());
        else if (bggSource.getPublishers() != null) {
          if (dbSource.getPublishers().size() != bggSource.getPublishers().size())                 
            dbSource.setPublishers(bggSource.getPublishers());
        }
        if (dbSource.getDesigners() == null) dbSource.setDesigners(bggSource.getDesigners());
        else if (bggSource.getDesigners() != null) {
          if (dbSource.getDesigners().size() != bggSource.getDesigners().size())                 
            dbSource.setDesigners(bggSource.getDesigners());
        }
        if (dbSource.getCategories() == null) dbSource.setCategories(bggSource.getCategories());
        else if (bggSource.getCategories() != null) {
          if (dbSource.getCategories().size() != bggSource.getCategories().size())                 
            dbSource.setCategories(bggSource.getCategories());
        }
        if (dbSource.getMechanisms() == null) dbSource.setMechanisms(bggSource.getMechanisms());
        else if (bggSource.getMechanisms() != null) {
          if (dbSource.getMechanisms().size() != bggSource.getMechanisms().size())                 
            dbSource.setMechanisms(bggSource.getMechanisms());
        }
        if (dbSource.getExpansionIDs() == null) dbSource.setExpansionIDs(bggSource.getExpansionIDs());
        else if (bggSource.getExpansionIDs() != null) {
          if (dbSource.getExpansionIDs().size() != bggSource.getExpansionIDs().size())                 
            dbSource.setExpansionIDs(bggSource.getExpansionIDs());
        }
        
        finalList.add(dbSource);
      }
    }
    
    if (sync.equalsIgnoreCase("y")) {
      GamesDatabase database = null; 
      try {
        database = MongoDBFactory.createMongoGamesDatabase(Application.databaseHost, Application.databasePort, Application.databaseName);
        database.initializeDBConnection();
        
        for (BGGGame curGame : finalList)
          database.updateBGGGameData(curGame);
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
    }
    
    if (batch == 1) return finalList.get(0);
    else            return finalList;
  }  

  /**
   * PUT Method, which should update (or potentially upsert) the provided game object.
   * 
   * @param game
   * 
   * @return A {@link SimpleMessageData} or {@link SimpleErrorData} message indicating the operation status
   */
  @RequestMapping(method = RequestMethod.PUT, consumes = "application/json;charset=UTF-8", produces="application/json;charset=UTF-8")
  public Object putBGGData(@RequestParam(value="bggid") long bggID,
                           @RequestBody BGGGame game) {
    if (bggID <= 0)
      return new SimpleErrorData("Game Data Error", "There was no valid BGGGame data provided");
    if (game == null)
      return new SimpleErrorData("Game Data Error", "There was no valid BGGGame data provided");
    if (game.getBggID() < 0)
      return new SimpleErrorData("Game Data Invalid", "The provided game has no Game ID");
    if (game.getBggID() != bggID)
      return new SimpleErrorData("Game Data Invalid", "The provided game content does not match the bggID parameter");
    
    GamesDatabase database = null; 
    try {
      database = MongoDBFactory.createMongoGamesDatabase(Application.databaseHost, Application.databasePort, Application.databaseName);
      database.initializeDBConnection();
      
      database.updateBGGGameData(game);
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
   * POST Method, which should insert (or potentially upsert) the provided game object.
   * 
   * @param game
   * 
   * @return A {@link SimpleMessageData} or {@link SimpleErrorData} message indicating the operation status
   */
  @RequestMapping(method = RequestMethod.POST, consumes = "application/json;charset=UTF-8", produces="application/json;charset=UTF-8")
  public Object postBGGData(@RequestBody BGGGame game) {
    if (game == null)
      return new SimpleErrorData("Game Data Error", "There was no valid BGGGame data provided");
    
    if (game.getBggID() < 0)
      return new SimpleErrorData("Game Data Invalid", "The provided game has no Game ID");
    
    GamesDatabase database = null; 
    try {
      database = MongoDBFactory.createMongoGamesDatabase(Application.databaseHost, Application.databasePort, Application.databaseName);
      database.initializeDBConnection();
      
      database.insertBGGGameData(game);
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
   * DELETE Method, which should delete the provided game reference, if it exists
   * 
   * @param data
   * 
   * @return A {@link SimpleMessageData} or {@link SimpleErrorData} message indicating the operation status
   */
  @RequestMapping(method = RequestMethod.DELETE, produces="application/json;charset=UTF-8")
  public Object deleteBGGData(@RequestParam(value="bggid") long bggID) {
    if (bggID <= 0)
      return new SimpleErrorData("Game Data Invalid", "The request has no Game ID");
    
    GamesDatabase database = null; 
    try {
      database = MongoDBFactory.createMongoGamesDatabase(Application.databaseHost, Application.databasePort, Application.databaseName);
      database.initializeDBConnection();
      
      database.deleteBGGGameData(bggID);
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
