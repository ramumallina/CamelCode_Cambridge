package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.FileNameS3Processor;

@Component
public class UploadingHtmlToS3Route extends RouteBuilder{
  
  private Logger logger = LoggerFactory.getLogger(UploadingHtmlToS3Route.class);
  
  @Autowired
  FileNameS3Processor fileNameS3Processor;

  @Override
  public void configure() throws Exception {
    
    onException(Exception.class)
    .log(LoggingLevel.ERROR, logger, "General Error Has Occurred ,Exception Message:: ${exception.message}")
    .handled(true);
    
    from("file:{{html.upload.folder}}")
    .routeId(getClass().getName())
    .setHeader("CamelAwsS3ContentLength", simple("${in.header.CamelFileLength}"))
//    .setHeader("CamelAwsS3Key", simple("${in.header.CamelFileNameOnly}"))
    .process(fileNameS3Processor)
    .setHeader("CamelAwsS3Key", simple("${header.CamelKey}"))
    //.toD("{{s3_bucket_endpoint_url}}")
    .to("{{aws2.component}}://{{s3Bucket_question_answer}}?deleteAfterWrite=false&region={{aws.s3.region}}&useDefaultCredentialsProvider=true")
    .end();
  }
}
