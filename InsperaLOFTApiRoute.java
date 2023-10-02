package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import static com.ca.ceil.marking.svc.utility.CEILConstants.VERIFICATION;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ATC_CONTINGENCY_COMPLETED;
import static com.ca.ceil.marking.svc.utility.CEILConstants.AUTOMATED_TEST_CONSTRUCTION_COMPLETED;
import static com.ca.ceil.marking.svc.utility.CEILConstants.AUTOMATED_TEST_CONSTRUCTION_COMPLETED_FOR_CANDIDATE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.AUTOMATED_TEST_CONSTRUCTION_FAILED;

@Component
public class InsperaLOFTApiRoute extends RouteBuilder {
  
  public static final Logger LOGGER = LoggerFactory.getLogger(InsperaLOFTApiRoute.class);

	@Override
	public void configure() throws Exception {
	  
	  onException(Exception.class)
        .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
        .handled(true)
        .log(LoggingLevel.ERROR,LOGGER,"for contextObjectId: ${exchangeProperty.contextObjectId} " + CEILConstants.LOG_ERROR_MESSAGE)
        .end();

	from(CEILConstants.LOFT_EVENT_NOTIFICATION_API_ROUTE)
		.routeId(getClass().getName())
		.log(LoggingLevel.INFO,LOGGER,"Received Inspera LOFT event Notification:::${body}")
		.setHeader(CEILConstants.EVENT,jsonpath("$.event"))
        .setProperty(CEILConstants.CONTEXT_OBJECT_ID, jsonpath("$.contextObjectId"))
        .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " for contextObjectId: ${exchangeProperty.contextObjectId}")
        .choice()
		   .when(PredicateBuilder.or(header(CEILConstants.EVENT).isEqualTo(AUTOMATED_TEST_CONSTRUCTION_COMPLETED),header(CEILConstants.EVENT).isEqualTo(AUTOMATED_TEST_CONSTRUCTION_COMPLETED_FOR_CANDIDATE)))
		   		.wireTap(CEILConstants.LOFT_EVENT_NOTIFICATION_ROUTE)
 		   		.bean(ResponseMessageDto.class,"setResponseMessage(${exchange}, ${exception})")
 		   .endChoice()
		   .when(PredicateBuilder.or(header(CEILConstants.EVENT).isEqualTo(VERIFICATION),header(CEILConstants.EVENT).isEqualTo(AUTOMATED_TEST_CONSTRUCTION_FAILED),header(CEILConstants.EVENT).isEqualTo(ATC_CONTINGENCY_COMPLETED)))
		         .bean(ResponseMessageDto.class,"setResponseMessage(${exchange}, ${exception})")
		   .endChoice()
		   .otherwise()
	         .log("Invalid Request Body")
		     .setHeader("flag", constant(true))
		     .bean(ResponseMessageDto.class,"setResponseMessage(${exchange}, ${exception})")
	    .end()
		.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE  + " for contextObjectId: ${exchangeProperty.contextObjectId}")
	    .end();
	}
}
