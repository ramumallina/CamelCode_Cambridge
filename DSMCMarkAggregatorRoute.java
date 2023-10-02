package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.ASSESSOR_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.CANDIDATE_CENTRE_NUMBER;
import static com.ca.ceil.marking.svc.utility.CEILConstants.CENTRE_IDENTIFIER;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_CANDIDATE_ID_SELECT_QUERY;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_DB_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_INSPERA_UPDATE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_MARK_AGGREGATOR_ROUTE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_MARK_PAYLOAD;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_MARK_SELECT_QUERY;
import static com.ca.ceil.marking.svc.utility.CEILConstants.KAD_Date;
import static com.ca.ceil.marking.svc.utility.CEILConstants.LEGACY_NUMBER;
import static com.ca.ceil.marking.svc.utility.CEILConstants.PRODUCT_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.ROOT_TAG;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ca.ceil.marking.svc.camelprocessor.FetchMarkMappingData;
import com.ca.ceil.marking.svc.camelprocessor.GetDsmcAggregatedMark;
import com.ca.ceil.marking.svc.camelprocessor.KeyAssessmentDateProcessor;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class DSMCMarkAggregatorRoute extends RouteBuilder{

	public static final Logger LOGGER = LoggerFactory.getLogger(DSMCMarkAggregatorRoute.class);
	  
	@Value("${xslt.dsmcAggregateMarkPayloadEndpoint}")
	private String dsmcAggregateMarkPayloadEndpoint;
	
	@Value("${inspera_mark_queue_url}")
	private String insperaMarkQueueEndpoint;
	
	@Autowired
	KeyAssessmentDateProcessor keyAssessmentDateProcessor;
	
	
	@SuppressWarnings("unchecked")
	@Override
	public void configure() throws Exception { 
		
		onException(NullPointerException.class,Exception.class)
	      .handled(true)
	      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE)
	    .end(); 
	    
	    from(DSMC_MARK_AGGREGATOR_ROUTE)
	      .routeId(getClass().getName())
	      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START)
	      .split().jsonpath("$").streaming()
	        .marshal().json(JsonLibrary.Jackson)
	        .convertBodyTo(String.class)
		    .setProperty(DSMC_MARK_PAYLOAD, simple("${body}"))
		    .setHeader(PRODUCT_ID, jsonpath("$.programOfStudy"))
		  //  .setHeader(CENTRE_IDENTIFIER, jsonpath("$.legacyCentreNumber").convertTo(Integer.class))
		    
		    .setProperty(ASSESSOR_ID, jsonpath("$.assessorId"))
		    .setHeader(CANDIDATE_CENTRE_NUMBER, jsonpath("$.candidateNumber"))
		    .setHeader(LEGACY_NUMBER, jsonpath("$.legacyCentreNumber").convertTo(String.class))
		    
		    .setHeader(KAD_Date, jsonpath("$.keyAssessmentDate"))
		    .log(LoggingLevel.INFO, LOGGER,"Fetching weightage value from DB for the product ${header.productId}") 
		    .to(DSMC_MARK_SELECT_QUERY)
		    .choice() 
	        	.when(PredicateBuilder.or(body().isEqualTo(""),body().isNull()))
	        		.log(LoggingLevel.ERROR,LOGGER,"No Data fetched from database for the product ${header.productId}")
	        		.throwException(new NullPointerException("No Data fetched from database"))
	        	.otherwise()
			      .bean(GetDsmcAggregatedMark.class,"calculateAggregatedMarks")
			      .setBody(simple("${exchangeProperty.dsmcPayload}"))
			      .wireTap((DSMC_DB_ROUTE))
			      .toD(DSMC_CANDIDATE_ID_SELECT_QUERY)
				  .bean(FetchMarkMappingData.class,"fetchDsmcData") 
			      .setBody(simple(ROOT_TAG))
			      .to(dsmcAggregateMarkPayloadEndpoint) 
			      .log(LoggingLevel.INFO, LOGGER,"DSMC Aggregate Mark payload :::${body}")
			      .process(keyAssessmentDateProcessor)
			      .choice()
			      .when(simple("${exchangeProperty.sentToQueue} == true"))
			      .toD(insperaMarkQueueEndpoint)
			      .toD(DSMC_INSPERA_UPDATE)
			    .end()
	      .end()
	      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE)
	    .end();
	  }
	}
		
	

