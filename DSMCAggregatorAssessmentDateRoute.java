package com.ca.ceil.marking.svc.camelroutes;

import static com.ca.ceil.marking.svc.utility.CEILConstants.ASSESSOR_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.CANDIDATE_CENTRE_NUMBER;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_AGGREGATED_MARKS;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_INSPERA_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_INSPERA_UPDATE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_KAD;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_KAD_UPDATE;
import static com.ca.ceil.marking.svc.utility.CEILConstants.DSMC_QUESTION_ID;
import static com.ca.ceil.marking.svc.utility.CEILConstants.LEGACY_NUMBER;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.FetchMarkMappingData;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class DSMCAggregatorAssessmentDateRoute extends RouteBuilder {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(DSMCAggregatorAssessmentDateRoute.class);
	
	@Value("${xslt.dsmcAggregateMarkPayloadEndpoint}")
	private String dsmcAggregateMarkPayloadEndpoint;
	
	@Value("${inspera.mark.endpoint}")
	private String insperMarkEndpoint;
	  	
	@Value("${inspera_mark_queue_url}")
	private String insperaMarkQueueEndpoint;
	
	@Override
	public void configure() throws Exception {
		
		 onException(Exception.class)
	      .handled(true)
	      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE + " For Legacy Centre Number: ${body.get('legacy_centre_number')}")
	    .end();
		
		from(DSMC_KAD + DSMC_KAD_UPDATE)
			.log(LoggingLevel.INFO, LOGGER, CEILConstants.LOG_ROUTE_MESSAGE_START + " For Legacy Centre Number: ${body.get('legacy_centre_number')}")
			.routeId(getClass().getName())
			.log("Data after fetching KAD date from DB::${body}")
			.setProperty(ASSESSOR_ID, simple("${body.get('assessor_id')}"))
			.setHeader(DSMC_INSPERA_ID,simple("${body.get('candidateinsperaid')}"))
			.setHeader(DSMC_AGGREGATED_MARKS,simple("${body.get('aggregate_mark')}"))
			.setHeader(DSMC_QUESTION_ID,simple("${body.get('question_id')}"))
			.setHeader(LEGACY_NUMBER,simple("${body.get('legacy_centre_number')}",String.class))
			.setHeader(CANDIDATE_CENTRE_NUMBER,simple("${body.get('candidate_center_id')}",String.class))
			.choice()
	        .when(body().isNotNull())
			.bean(FetchMarkMappingData.class,"fetchDsmcData") 
			.setBody(simple("<root></root>"))
			.to(dsmcAggregateMarkPayloadEndpoint)
			.log("Sending Inspera Payload to Queue:::${body}")
			.toD(insperaMarkQueueEndpoint)
			.toD(DSMC_INSPERA_UPDATE)
			.log(LoggingLevel.INFO, LOGGER, CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE + " For Legacy Centre Number: ${header.legacyNumber}");
		

	}

}
