package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import static com.ca.ceil.marking.svc.utility.CEILConstants.BRIEFING_COMMENTS_QUESTIONID;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import static com.ca.ceil.marking.svc.utility.CEILConstants.BRIEFING_COMMENTS_INSERTION;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_ANSWER_UUID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_RESPONSE_INSERT_QUERY_URL;
import static com.ca.ceil.marking.svc.utility.CEILConstants.BRIEFING_COMMENTS;
import static com.ca.ceil.marking.svc.utility.CEILConstants.RMA_BRIEFING_COMMENTS_INSERT_QUERY_URL;


@Component
public class BriefingCommentsDBRoute extends RouteBuilder{
	
	public static final Logger LOGGER = LoggerFactory.getLogger(BriefingCommentsDBRoute.class);

	@Override
	public void configure() throws Exception {
		
		onException(Exception.class)
	    .log(LoggingLevel.ERROR, LOGGER,"for questionId: ${header.questionId} "+ CEILConstants.LOG_ERROR_MESSAGE)
	    .handled(true);
		
		from(BRIEFING_COMMENTS_INSERTION)
			.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START)
			.routeId(getClass().getName())
			.split().jsonpath("$")
		        .marshal().json(JsonLibrary.Jackson)
		        .log(LoggingLevel.INFO, LOGGER, "Body After Split is:::${body}")
		        .setHeader(BRIEFING_COMMENTS_QUESTIONID, jsonpath("$.questionId"))
				.setHeader(BRIEFING_COMMENTS, jsonpath("$.briefingComments"))
				.toD(RMA_BRIEFING_COMMENTS_INSERT_QUERY_URL)
			.end()
		.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE  + " for questionId: ${header.questionId}");
	}
}
