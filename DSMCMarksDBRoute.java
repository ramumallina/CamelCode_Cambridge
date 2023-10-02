package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ca.ceil.marking.svc.camelprocessor.GetDsmcAggregatedMark;
import com.ca.ceil.marking.svc.model.RmaMarksProcessor;
import com.ca.ceil.marking.svc.utility.CEILConstants;
import static com.ca.ceil.marking.svc.utility.CEILConstants.EXAM_TYPE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DELIVAERY_METHOD;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ASSESSOR_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.KEY_ASSESSMENT_DATE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.SITTING;
import static com.ca.ceil.marking.svc.utility.CEILConstants.RECORDED_MARK_DATE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.INTERLOCUTOR_ID;

@Component
public class DSMCMarksDBRoute extends RouteBuilder {

	public static final Logger LOGGER = LoggerFactory.getLogger(DSMCMarksDBRoute.class);
	
	
	@Autowired
	RmaMarksProcessor rmaMarksProcessor;

	@Override
	public void configure() throws Exception {
				
		onException(Exception.class)
			.log(LoggingLevel.ERROR, LOGGER," Error occured For Product Id::: ${header.productId} "+ CEILConstants.LOG_ERROR_MESSAGE)
			.handled(true)
			.end();
		
		from(CEILConstants.DSMC_DB_ROUTE)
		 	.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " For Product Id ${header.productId}")
			.routeId(getClass().getName())
		    .setHeader(EXAM_TYPE,  jsonpath("$.examType"))
		    .setHeader(RECORDED_MARK_DATE, jsonpath("$.recordedMarkDate"))
		    .setHeader(SITTING, jsonpath("$.sitting"))
		    .setHeader(KEY_ASSESSMENT_DATE, jsonpath("$.keyAssessmentDate"))
		    .setHeader(DELIVAERY_METHOD, jsonpath("$.deliveryMethod"))
		    .setHeader(ASSESSOR_ID, jsonpath("$.assessorId"))
		    .setHeader(INTERLOCUTOR_ID, jsonpath("$.interlocutorId"))
		    .log(LoggingLevel.INFO,LOGGER,"Inserting data to DB")
			.to(CEILConstants.DSMC_INSERTION_MARKS)
			.split(simple("${exchangeProperty.dsmcMarkList}"))
			    .to(CEILConstants.DSMC_INSERTION_ITEM_MARKS) 
			.end()
			.log(LoggingLevel.INFO,LOGGER,"Data Inserted Successfully")
		    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE + " For Product Id ${header.productId}");
		
	}

}
