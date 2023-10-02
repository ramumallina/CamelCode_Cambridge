package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.INSPERA_MARK_SUSPENSION_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.SUSPEND_ROUTE_PATH;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class SendMarkToInsperaSuspensionRoute extends RouteBuilder {

	private static final Logger LOGGER = LoggerFactory.getLogger(SendMarkToInsperaSuspensionRoute.class);

	@Override
	public void configure() throws Exception {
	
		onException(Exception.class)
		    .handled(true)
		    .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE)
		    .end(); 
		 
		 from(INSPERA_MARK_SUSPENSION_ROUTE)
		 	.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START)
	     	.routeId(getClass().getName())
	     	.toD(SUSPEND_ROUTE_PATH)
	     	.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE)
	     	.end();
	
	}


}
