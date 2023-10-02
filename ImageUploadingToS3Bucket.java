package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.utility.CEILConstants;
@Component
public class ImageUploadingToS3Bucket extends RouteBuilder{
  
  private static final Logger LOGGER = LoggerFactory.getLogger(SubmissionDeliveredRoute.class);

	@Override
	public void configure() throws Exception {
	  
	  onException(Exception.class)
	  .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE)
	    .handled(true);
	  
		from("file:{{image.resource.folder}}")
    		.routeId(getClass().getName())
    	    .setHeader("CamelAwsS3ContentLength", simple("${in.header.CamelFileLength}"))
    	    .setHeader("CamelAwsS3Key", simple("${in.header.CamelFileNameOnly}"))
    	    .setProperty("imageFileName", simple("${in.header.CamelFileNameOnly}"))
    	    //.toD("{{s3_bucket_endpoint_url}}")
    	    .to("{{aws2.component}}://{{s3Bucket_question_answer}}?deleteAfterWrite=false&region={{aws.s3.region}}&useDefaultCredentialsProvider=true")
    	    .end();
	}
}
