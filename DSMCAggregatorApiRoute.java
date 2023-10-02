package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_AGGREGATOR_API_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_JSON_VALIDATOR_ENDPOINT;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_MARK_AGGREGATOR_ROUTE;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jsonvalidator.JsonValidationException;
import org.apache.camel.support.processor.validation.SchemaValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.fasterxml.jackson.core.JsonParseException;
import com.jayway.jsonpath.InvalidJsonException;

@Component
public class DSMCAggregatorApiRoute extends RouteBuilder{

	public static final Logger LOGGER = LoggerFactory.getLogger(DSMCAggregatorApiRoute.class);
	  
	@SuppressWarnings("unchecked")
	@Override
	public void configure() throws Exception {
		 onException(Exception.class)
	      .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
	      .handled(true)
	      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE)
	    .end();
	    
	    onException(SchemaValidationException.class,JsonParseException.class,InvalidJsonException.class, JsonValidationException.class, ValidationException.class)
	      .log(LoggingLevel.ERROR,LOGGER,"Validation Error:::"+CEILConstants.LOG_ERROR_MESSAGE)
	      .bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
	      .handled(true)
	    .end();
	    
	    from(DSMC_AGGREGATOR_API_ROUTE)
	      .routeId(getClass().getName())
	      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START)
	      .to(DSMC_JSON_VALIDATOR_ENDPOINT)
	      .log(LoggingLevel.INFO, LOGGER, "Json Schema Validation is successful")
	      .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
	      .wireTap(DSMC_MARK_AGGREGATOR_ROUTE)
	      .bean(ResponseMessageDto.class,"setResponseMessage(${exchange}, ${exception})")
	      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE)
	      .end();
	  }
	}
		
	

