package com.ac.games.rest.controller;

import java.util.ArrayList;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ac.games.data.CompactSearchData;
import com.ac.games.db.GamesDatabase;
import com.ac.games.db.MongoDBFactory;
import com.ac.games.db.exception.ConfigurationException;
import com.ac.games.db.exception.DatabaseOperationException;
import com.ac.games.rest.Application;
import com.ac.games.rest.data.WrapList;
import com.ac.games.rest.message.SimpleErrorData;

/**
 * This class should be the intercepter for REST service access for any Autocomplete
 * fields.
 * <p>
 * It should handle anything under the /auto entry
 * 
 * @author ac010168
 *
 */
@RestController
@RequestMapping("/auto")
public class AutocompleteController {

  @RequestMapping(method = RequestMethod.GET, produces="application/json;charset=UTF-8")
  public Object getAutoComplete(@RequestParam(value="source") String source,
                                @RequestParam(value="value", defaultValue="full") String value) {
    
    if (source == null)
      return new SimpleErrorData("Invalid Parameters", "The source parameter was not provided");
    if (value == null)
      return new SimpleErrorData("Invalid Parameters", "The value parameter was not provided");

    if ((!source.equalsIgnoreCase("game")))
      return new SimpleErrorData("Invalid Parameters", "The source parameter value of " + source + " is not a valid source value.");
    
    GamesDatabase database = null; 
    Object results = null;
    
    if (value.trim().length() < 3)
      return new ArrayList<CompactSearchData>();
    
    try {
      database = MongoDBFactory.createMongoGamesDatabase(Application.databaseHost, Application.databasePort, Application.databaseName);
      database.initializeDBConnection();
      
      if (value.equalsIgnoreCase("full"))
        results = new WrapList(database.readGameNamesForAutoComplete());
      else {
        System.out.println ("The value I'm going to deconstruct is: " + value);
        String gameName   = null;
        String primaryPub = null;
        int yearPublished = -1;
        
        int openParen = value.lastIndexOf("(");
        if (openParen == -1)
          gameName = value.trim();
        else {
          gameName = value.substring(0, openParen - 1).trim();
          String filterSubString = value.substring(openParen + 1, value.length() - 1);
          int splitPos = filterSubString.indexOf(" - ");
          if (splitPos != -1) {
            //It has a publisher and a date
            primaryPub = filterSubString.substring(0, splitPos).trim();
            yearPublished = Integer.parseInt(filterSubString.substring(splitPos+3).trim());
          } else {
            //It is a publisher OR a date
            try {
              yearPublished = Integer.parseInt(filterSubString);
            } catch (NumberFormatException nfe) {
              primaryPub = filterSubString;
            }
          }
        }
        
        System.out.println ("Game Name:     " + gameName);
        System.out.println ("Primary Pub:   " + primaryPub);
        System.out.println ("yearPublished: " + yearPublished);
        
        CompactSearchData data = database.readGameFromAutoName(gameName, primaryPub, yearPublished);
        
        if (data == null)
          results =  new SimpleErrorData("No Game Found", "I could not find the requested game.");
        else 
          results = data;
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
    
    return results;
  }
}

