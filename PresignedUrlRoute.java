package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.PresignedURLGeneratorProcessor;
import com.ca.ceil.marking.svc.exception.FileNotFoundException;
import com.ca.ceil.marking.svc.model.ResponseMessageDto;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class PresignedUrlRoute extends RouteBuilder{

  public static final Logger LOGGER = LoggerFactory.getLogger(PresignedUrlRoute.class);
  
  @Autowired
  PresignedURLGeneratorProcessor presignedURLGeneratorProcessor;
  
  /*
   *HtmlPresignedUrlGenerationRoute will receive unique Id. Based on that Id, return the presigned url.
   * 
   *@exception Exception throws generic exception
   *@exception FileNotFoundException throws when file is not present in s3 bucket
   *@exception NumberFormatException throws when unique Id contains any alphabet
   */
  
  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {
    
		onException(Exception.class)
			.bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
			.handled(true)
			.log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE)
			.end();

		onException(FileNotFoundException.class, NumberFormatException.class)
			.bean(ResponseMessageDto.class, "setResponseMessage(${exchange}, ${exception})")
			.handled(true)
			.log(LoggingLevel.ERROR,LOGGER,"Error::: "+CEILConstants.LOG_ERROR_MESSAGE)
			.end(); 
    
		from(CEILConstants.DIRECT_HTML_PRESIGNED_URL_GENERATION_ROUTE)
		    .setProperty(CEILConstants.FILE_NAME,simple("${body}"))
        	.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " Unique Id: ${exchangeProperty.fileName}")
        	.routeId(getClass().getName())
    	    .bean(presignedURLGeneratorProcessor.getClass(),"fileUploadToBucket")
    	    .delay(3000)
    	    .bean(presignedURLGeneratorProcessor.getClass(),"preSignedUrl")
    	    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpStatus.OK.value()))
    	    .log(LoggingLevel.INFO,LOGGER,"The Presigned url for the file with Unique Id ${exchangeProperty.fileName} is ${body}")
    	    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE + " Unique Id: ${exchangeProperty.fileName}")
	    .end();
	    
  }

}
