package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class ReceiveBriefingCommentsRoute extends RouteBuilder{
	
	public static final Logger LOGGER = LoggerFactory.getLogger(ReceiveBriefingCommentsRoute.class);

	@Override
	public void configure() throws Exception {
		
		onException(Exception.class)
	      .handled(true)
	      .log(LoggingLevel.ERROR,LOGGER, CEILConstants.LOG_ERROR_MESSAGE+ " for questionId: ${exchangeProperty.questionId}")
	    .end();
	  
		from(CEILConstants.RECEIVE_BRIEFING_COMMENTS_FOR_RMA)
			.routeId(getClass().getName())
			.setProperty("questionId", simple("${body}"))
            .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " for questionId: ${exchangeProperty.questionId}")
            .setProperty("fileName", simple("${body}.pdf"))
            .setHeader("questionId", simple("${exchangeProperty.questionId}", Integer.class))
            .setHeader("fileName", simple("${exchangeProperty.fileName}"))
            .setHeader("briefingComments",constant("true"))
            .setHeader("dateCreated", simple("${date:now:yyyy-MM-dd HH:mm:ss.SSS}"))
            .to(CEILConstants.RMA_BRIEFING_COMMENTS_INSERT_QUERY_URL)
            .log(LoggingLevel.INFO, LOGGER, "Briefing Comments table updated with questionId and status")
            .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE + " for questionId: ${exchangeProperty.questionId}")
         .end();
	}
}
