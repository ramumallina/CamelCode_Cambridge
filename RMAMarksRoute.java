package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.processor.validation.SchemaValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXParseException;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.model.XmlResponseDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class RMAMarksRoute extends RouteBuilder {

	public static final Logger LOGGER = LoggerFactory.getLogger(RMAMarksRoute.class);
	
	@Override
	public void configure() throws Exception {

		onException(SchemaValidationException.class, SAXParseException.class, ValidationException.class)
				.log(LoggingLevel.ERROR,LOGGER,"Validation Error For Candidate Id ${header.candidateId}::: " +CEILConstants.LOG_ERROR_MESSAGE)
				.bean(XmlResponseDto.class, "printXmlErrorResponseBody(${exchange}, ${exception})")
				.handled(true);

		onException(Exception.class)
		        .bean(XmlResponseDto.class, "printXmlErrorResponseBody(${exchange}, ${exception})")
				.log(LoggingLevel.ERROR, LOGGER," Error occured For Candidate Id::: ${header.candidateId} "+ CEILConstants.LOG_ERROR_MESSAGE)
				.handled(true);

		from(CEILConstants.DIRECT_RMA_MARKS)
		 	.setHeader("candidateId", xpath("*[name()='GetItemMarksReturnResponse']/*[name()='GetItemMarksReturnResult']/*[name()='itemArray']/*[name()='ItemMarksReturnData']/*[name()='ArrayOfItemMarksReturnData']/*[name()='Candidate']/*[name()='UCI']/*[name()='Id']/text()",String.class))
		    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " For Candidate Id ${header.candidateId}")
			.routeId(getClass().getName())
			.log("Validating xml data:::${body}")
			.to("validator:RMAResponseForMarks.xsd")
			.log("Validation Success-Sending data to SQS Queue")
			.wireTap(CEILConstants.DIRECT_RMA_MARKS_DB_ROUTE)
			//.toD("{{queue_url_rma}}")		
			.bean(XmlResponseDto.class,"printXmlResponseBody")
		    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE + " For Candidate Id ${header.candidateId}");

	}

}
