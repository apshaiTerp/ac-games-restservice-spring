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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.ac.games.data.CSIIDOnlyData;
import com.ac.games.data.CoolStuffIncPriceData;
import com.ac.games.data.parser.CoolStuffIncParser;
import com.ac.games.db.GamesDatabase;
import com.ac.games.db.MongoDBFactory;
import com.ac.games.db.exception.ConfigurationException;
import com.ac.games.db.exception.DatabaseOperationException;
import com.ac.games.exception.GameNotFoundException;
import com.ac.games.rest.message.SimpleErrorData;
import com.ac.games.rest.message.SimpleMessageData;

/**
 * This class should be the intercepter for REST service access to the CoolStuffInc game
 * information.
 * <p>
 * It should handle all request that come in under the /external/csidata entry.
 * <p>
 * Refer to the individual methods to determine the parameter lists.
 * 
 * @author ac010168
 */
@RestController
@RequestMapping("/external/csidata")
public class CSIDataController {

  /** The standard URI template by which games can be accessed by csiid */
  public final static String URL_TEMPLATE = "http://www.coolstuffinc.com/p/<csiid>";
  /** The replacement marker in the URL_TEMPLATE */
  public final static String CSIID_MARKER = "<csiid>";
  
  /**
   * GET method designed to handle retrieving the CoolStuffInc content from the
   * coolstuffinc website and return the formatted {@link CoolStuffIncPriceData} object.
   * <p>
   * This method supports the following parameters:
   * <ul>
   * <li><code>csiid=&lt;gameid&gt;</code> - The gameID.  This is required.</li>
   * <li><code>source=&lt;csi|db&gt;</code> - This indicated whether to request the game from BoardGameGeek (bgg)
   * or from our cached database (db).  Default is csi.</li></ul>
   * 
   * @param csiID
   * @return A {@link CoolStuffIncPriceData} object or {@link SimpleErrorData} message reporting the failure
   */
  @RequestMapping(method = RequestMethod.GET, produces="application/json;charset=UTF-8")
  public Object getCSIData(@RequestParam(value="csiid") long csiID, @RequestParam(value="source", defaultValue="csi") String source) {
    if ((!source.equalsIgnoreCase("csi")) && (!source.equalsIgnoreCase("db")))
      return new SimpleErrorData("Invalid Parameters", "The source parameter value of " + source + " is not a valid source value.");
    
    //DEBUG
    System.out.println ("Processing csi request for csiid " + csiID + "...");
    
    if (source.equalsIgnoreCase("csi")) {
      //Create the RestTemplate to access the external HTML page
      RestTemplate restTemplate = new RestTemplate();
      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(Arrays.asList(MediaType.TEXT_HTML));
      HttpEntity<String> entity = new HttpEntity<String>("parameters", headers);
      
      CoolStuffIncPriceData data = null;
      try {
        //Run the GET command to retrieve the HTML Body
        ResponseEntity<String> gameResponse = restTemplate.exchange(URL_TEMPLATE.replace(CSIID_MARKER, "" + csiID), 
            HttpMethod.GET, entity, String.class);

        String htmlText = gameResponse.getBody();
        data = CoolStuffIncParser.parseCSIHTML(htmlText, csiID);
      } catch (HttpServerErrorException hsee) {
        if (hsee.getMessage().contains("503 Service Unavailable")) {
          System.out.println ("The CSI server is icing me out again...");
          return new SimpleErrorData("Server Timeout 503", "The CSI server has stopped answering my requests");
        } else {
          System.out.println ("Something probably wrong happened here...");
          hsee.printStackTrace();
          return new SimpleErrorData("Operation Error", "An error has occurred: " + hsee.getMessage());
        }
      } catch (HttpClientErrorException hcee) {
        if (hcee.getMessage().contains("404 Not Found")) {
          System.out.println ("I could not find this game.");
          return new SimpleErrorData("Game Not Found", "The requested csiid of " + csiID + " could not be found.");
        } else {
          System.out.println ("Something probably wrong happened here...");
          hcee.printStackTrace();
          return new SimpleErrorData("Operation Error", "An error has occurred: " + hcee.getMessage());
        }
      } catch (GameNotFoundException gnfe) {
        System.out.println ("I could not find this game.");
        return new SimpleErrorData("Game Not Found", "The requested csiid of " + csiID + " could not be found.");
      } catch (Throwable t) {
        System.out.println ("Something terribly wrong happened here...");
        t.printStackTrace();
        return new SimpleErrorData("Operation Error", "An error has occurred: " + t.getMessage());
      }

      return data;
    } else {
      GamesDatabase database = MongoDBFactory.getMongoGamesDatabase();
      CoolStuffIncPriceData data = null;
      try {
        data = database.readCSIPriceData(csiID);
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
  public Object putCSIData(@RequestBody CoolStuffIncPriceData data) {
    if (data == null)
      return new SimpleErrorData("Game Data Error", "There was no valid CSI data provided");
    
    if (data.getCsiID() < 0)
      return new SimpleErrorData("Game Data Invalid", "The provided game has no CSI ID");
    
    GamesDatabase database = MongoDBFactory.getMongoGamesDatabase();
    try {
      database.updateCSIPriceData(data);
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
  public Object postCSIData(@RequestBody CoolStuffIncPriceData data) {
    if (data == null)
      return new SimpleErrorData("Game Data Error", "There was no valid CSI data provided");
    
    if (data.getCsiID() < 0)
      return new SimpleErrorData("Game Data Invalid", "The provided game has no CSI ID");
    
    GamesDatabase database = MongoDBFactory.getMongoGamesDatabase();
    try {
      database.insertCSIPriceData(data);
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
   * @param data
   * 
   * @return A {@link SimpleMessageData} or {@link SimpleErrorData} message indicating the operation status
   */
  @RequestMapping(method = RequestMethod.DELETE)
  public Object deleteCSIData(@RequestBody CSIIDOnlyData data) {
    if (data == null)
      return new SimpleErrorData("Game Data Error", "There was no valid CSI data provided");
    
    if (data.getCsiID() <= 0)
      return new SimpleErrorData("Game Data Invalid", "The provided game has no CSI ID");
    
    GamesDatabase database = MongoDBFactory.getMongoGamesDatabase();
    try {
      database.deleteCSIPriceData(data.getCsiID());
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
