package com.ac.games.rest;

import javax.annotation.PreDestroy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import com.ac.games.db.GamesDatabase;
import com.ac.games.db.MongoDBFactory;
import com.ac.games.db.exception.ConfigurationException;

/**
 * @author ac010168
 *
 * Primary Launcher for the REST Service
 */
@ComponentScan
@EnableAutoConfiguration
public class Application {
  
  /** Main method, which is starting point for service using Spring launcher */
  public static void main(String[] args) {
    
    //TODO - Eventually decide on how to dynamically define the database parameters
    try {
      MongoDBFactory.createMongoGamesDatabase("localhost", 27017, "livedb").initializeDBConnection();
    } catch (ConfigurationException e) {
      e.printStackTrace();
      System.out.println ("Shutting down system!");
    }
    
    SpringApplication.run(Application.class, args);
  }
  
  @PreDestroy
  public static void shutdownHook() {
    System.out.println (">>>  I'm inside the shutdownHook  <<");
    GamesDatabase database = MongoDBFactory.getMongoGamesDatabase();
    try {
      database.closeDBConnection();
    } catch (ConfigurationException e) {
      e.printStackTrace();
    }
  }
}