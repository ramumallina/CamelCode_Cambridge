package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.CONTENT_SUBMISSION_DELIVERED;
import static com.ca.ceil.marking.svc.utility.CEILConstants.CONTENT_SUBMISSION_EXPORT_FINISHED;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DIRECT_SUBMISSION_DELIVERED_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DIRECT_SUBMISSION_EXPORT_FINISHED;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DIRECT_WEBHOOK_NOTIFICATIONS;
import static com.ca.ceil.marking.svc.utility.CEILConstants.SUBMISSION_ASSOCIATED_OBJECT_ID;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class MessageRoute extends RouteBuilder {
  
  public static final Logger LOGGER = LoggerFactory.getLogger(MessageRoute.class);

	@Override
	public void configure() throws Exception {
	  
	  onException(Exception.class)
        .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
        .handled(true)
        .log(LoggingLevel.ERROR,LOGGER,"for associatedObjectId: ${header.associatedObjectId} " + CEILConstants.LOG_ERROR_MESSAGE);

	from(DIRECT_WEBHOOK_NOTIFICATIONS)
		.routeId(getClass().getName())
		.log("Received Inspera Submission Notification:::${body}")
		.setHeader("event",jsonpath("$.event"))
        .setHeader(SUBMISSION_ASSOCIATED_OBJECT_ID, jsonpath("$.associatedObjectId"))
        .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " for associatedObjectId: ${header.associatedObjectId}")
		.choice()
		   .when(header("event").isEqualTo("resource_export_completed"))
    		   .wireTap(DIRECT_SUBMISSION_EXPORT_FINISHED)
    		   .bean(ResponseMessageDto.class,"setResponseMessage(${exchange}, ${exception})")
    	   .endChoice()
		   .when(header("event").isEqualTo(CONTENT_SUBMISSION_DELIVERED))
		       .wireTap(DIRECT_SUBMISSION_DELIVERED_ROUTE)
		       .bean(ResponseMessageDto.class,"setResponseMessage(${exchange}, ${exception})")
		   .endChoice()
		   .when(header("event").isEqualTo("verification"))
		         .bean(ResponseMessageDto.class,"setResponseMessage(${exchange}, ${exception})")
		   .endChoice()
		   .otherwise()
		         .log("Invalid Request Body")
    		     .setHeader("flag", constant(true))
    		     .bean(ResponseMessageDto.class,"setResponseMessage(${exchange}, ${exception})")
		    .end()
		    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE  + " for associatedObjectId: ${header.associatedObjectId}")
	    .end();
	}
}
