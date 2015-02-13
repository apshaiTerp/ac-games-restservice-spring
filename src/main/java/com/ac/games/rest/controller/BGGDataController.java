package com.ac.games.rest.controller;

import java.util.Arrays;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.ac.games.data.BGGGame;
import com.ac.games.data.parser.BGGGameParser;
import com.ac.games.exception.GameNotFoundException;
import com.ac.games.rest.error.SimpleErrorData;

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
   * @return
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
      return new SimpleErrorData("Unsupported Operation", "Database operations are unsupported at this time");
    }
  }  

  @RequestMapping(method = RequestMethod.PUT)
  public void putBGGData() {
    throw new RuntimeException("PUT is not supported for this service");
  }
  
  @RequestMapping(method = RequestMethod.POST)
  public void postBGGData() {
    throw new RuntimeException("POST is not supported for this service");
  }

  @RequestMapping(method = RequestMethod.DELETE)
  public void deleteBGGData() {
    throw new RuntimeException("DELETE is not supported for this service");
  }
}
