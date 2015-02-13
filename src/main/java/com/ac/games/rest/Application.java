package com.ac.games.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

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
    SpringApplication.run(Application.class, args);
  }
}
