package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.PresignedURLGeneratorProcessor;
import com.ca.ceil.marking.svc.exception.FileNotFoundException;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class PresignedUrlBriefingCommentsRoute extends RouteBuilder{

  public static final Logger LOGGER = LoggerFactory.getLogger(PresignedUrlBriefingCommentsRoute.class);
  
  @Autowired
  PresignedURLGeneratorProcessor presignedURLGeneratorProcessor;
  
  /*
   *PresignedUrlBriefingCommentsRoute will receive unique Id for Briefing Comments. Based on that Id, return the presigned url.
   * 
   *@exception Exception throws generic exception
   *@exception FileNotFoundException throws when file is not present in s3 bucket
   *@exception NumberFormatException throws when unique Id contains any alphabet
   */
  
  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {
    
        onException(Exception.class)
            .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
            .handled(true)
            .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE + " Unique Id: ${exchangeProperty.fileName}")
            .end();

        onException(FileNotFoundException.class, NumberFormatException.class)
            .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
            .handled(true)
            .log(LoggingLevel.ERROR,LOGGER,CEILConstants.LOG_ERROR_MESSAGE + " Unique Id: ${exchangeProperty.fileName}")
            .end(); 
    
        from(CEILConstants.HTML_BRIEFING_COMMENTS_PRESIGNEDURL_GENERATION_ROUTE)
            .setProperty(CEILConstants.FILE_NAME,simple("${body}"))
            .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " Unique Id: ${exchangeProperty.fileName}")
            .routeId(getClass().getName())
            .bean(PresignedURLGeneratorProcessor.class,"briefingCommentsPreSignedUrl")
            .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
            .log(LoggingLevel.INFO,LOGGER,"The Presigned url for the file with Unique Id ${exchangeProperty.fileName} is ${body}")
            .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE + " Unique Id: ${exchangeProperty.fileName}")
        .end();
  }
}