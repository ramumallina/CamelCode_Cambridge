package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class UploadingLoftFileToS3Route extends RouteBuilder{
  
  private Logger LOGGER = LoggerFactory.getLogger(UploadingLoftFileToS3Route.class);

  @Value("${route.enable.UploadingloftToS3Route}")
  private boolean autoStartRoute;

  @Override
  public void configure() throws Exception {
    
	onException(Exception.class)
        .handled(true)
        .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE)
        .end(); 
    
    from("file:{{inspera.loft.folder}}?delete=true")
    	.autoStartup(autoStartRoute)
    	.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START )
    	.routeId(getClass().getName())
    	.setHeader("CamelAwsS3ContentLength", simple("${in.header.CamelFileLength}"))
    	.setHeader("CamelKey",simple("loftFiles/${header.CamelFileName}"))
    	.setHeader("CamelAwsS3Key", simple("${in.header.CamelKey}"))
        .toD("{{aws2.component}}://{{s3Bucket_loft}}?deleteAfterWrite=false&region={{aws.s3.region}}&useDefaultCredentialsProvider=true")
        .log(LoggingLevel.INFO,LOGGER,"Uploaded file [${header.CamelFileName}] to S3 Successfully")
		.log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE)
    .end();
  }
}
