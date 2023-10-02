package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_API_DATABASE_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_CREATION_DATE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_INSERT_QUERY_URL;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.utility.CEILConstants;
@Component
public class ElitResponseDBRoute extends RouteBuilder{
  
  public static final Logger LOGGER = LoggerFactory.getLogger(ElitResponseDBRoute.class);

  @Override
  public void configure() throws Exception {
    
    onException(Exception.class)
    .log(LoggingLevel.ERROR, LOGGER,"for answerUuid: ${header.answer_uuid} "+ CEILConstants.LOG_ERROR_MESSAGE)
    .handled(true);
    
    from(ELIT_RESPONSE_API_DATABASE_ROUTE)
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " for answerUuid: ${header.answer_uuid}")
      .routeId(getClass().getName())
      .setHeader(ELIT_RESPONSE_CREATION_DATE, simple("${date:now:yyyy-MM-dd HH:mm:ss.SSS}"))
      .to(ELIT_RESPONSE_INSERT_QUERY_URL)
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE  + " for answerUuid: ${header.answer_uuid}")
      .end();
  }
}
