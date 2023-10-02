package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.ElitPrimingMetaDataReading;
import com.ca.ceil.marking.svc.camelprocessor.FileNameReadingProcessor;
import com.ca.ceil.marking.svc.camelprocessor.ReadingFolderName;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class ReadingQuestionRoute extends RouteBuilder{

  private static final Logger LOGGER = LoggerFactory.getLogger(ReadingQuestionRoute.class);
  
  @Autowired
  ElitPrimingMetaDataReading elitPrimingMetaDataReading;
  
  @Autowired
  ReadingFolderName readingFolderName;
  
  @Override
  public void configure() throws Exception {

    onException(Exception.class)
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE + " for questionContentItemId: ${exchangeProperty.contextObjectId}")
    .end();
    
    from("file:{{elit.priming.question.folder}}/extractedFiles?recursive=true")
      .routeId(getClass().getName())
      .process(readingFolderName)
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " for questionContentItemId: ${exchangeProperty.contextObjectId}")
      .choice()
      .when(exchangeProperty("isItem").isEqualTo(true))
      .convertBodyTo(String.class)
      .choice()
        .when((body().contains("<extendedTextInteraction")))
          .log("After PollEnrich item body::${header.CamelFileNameConsumed}")
          .setProperty("camelFileNameConsumed", simple("${header.CamelFileNameConsumed}"))
          .bean(FileNameReadingProcessor.class,"xmlFileName")
          .to("direct:readingQuestionItemFileRoute")
        .endChoice()
      .end()
      .end()
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE  + " for questionContentItemId: ${exchangeProperty.contextObjectId}")
    .end();
  }

}
