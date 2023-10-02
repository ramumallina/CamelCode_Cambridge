package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.ANSWER_UUID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_MARKS_SELECT_QUERY;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ELIT_TASK_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.OVERALL;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ca.ceil.marking.svc.camelprocessor.FetchMarkMappingData;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import com.ca.ceil.marking.svc.utility.RandomIdentifier;

@Component
public class SqsEliteResponseConsumerRoute extends RouteBuilder {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SqsEliteResponseConsumerRoute.class);

	@Value("${xslt.elitMarkEndpoint}")
	private String elitMarkXsltEndpoint;
	
	@Value("${inspera_mark_queue_url}")
	private String insperaMarkQueueEndpoint;
	
	@Value("${elit_response_queue_url}")
	private String elitResponseQueueEndpoint;
	
	@SuppressWarnings("unchecked")
	@Override
	public void configure() throws Exception {
		
		onException(NullPointerException.class,Exception.class)
		    .handled(true)
		    .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE)
		    .end(); 
		
		from(elitResponseQueueEndpoint)
		 	.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START+" for answer UUID ${header.answer_uuid} ")
	     	.routeId(getClass().getName())
	     	.log(LoggingLevel.INFO, LOGGER,"Body After consuming data from SQS Queue is :::${body}")
	     	.setHeader(ANSWER_UUID,jsonpath("$.id"))
	     	.setHeader(OVERALL,jsonpath("$.grader-results[0].results.overall"))
	     	.to(ELIT_MARKS_SELECT_QUERY)
	     	.bean(FetchMarkMappingData.class,"fetchElitData")
	     	.bean(RandomIdentifier.class,"digit6")
			 .setBody(simple("<root></root>"))
	     	.to(elitMarkXsltEndpoint) 
	     	.log(LoggingLevel.INFO, LOGGER,"Elit Mark payload :::${body}")
	     	.to(insperaMarkQueueEndpoint)
	     	.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE+ " for answer UUID ${header.answerUuid}")
	     	.end();
		
	}


}
