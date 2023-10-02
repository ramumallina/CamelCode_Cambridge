package com.ca.ceil.marking.svc.camelroutes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ca.ceil.marking.svc.camelprocessor.FetchMarkingMode;
import com.ca.ceil.marking.svc.utility.CEILConstants;

@Component
public class ProductLookupRoute extends RouteBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProductLookupRoute.class);
  
  @Autowired
  FetchMarkingMode fetchMarkingMode;
  
  /*
   * ProductLookupRoute fetching the ceil marking mode based on the candidate inspera id and setting
   * header and property for insert query.
   * 
   * @exception Exception throws generic exception
   *
   * Control comes from: ExportFinishedRoute Control goes to: ExportFinishedRoute
   */
  
  @SuppressWarnings("unchecked")
  @Override
  public void configure() throws Exception {

    onException(NullPointerException.class, Exception.class)
      .handled(true)
      .log(LoggingLevel.ERROR, LOGGER,CEILConstants.LOG_ERROR_MESSAGE+ " For Candidate Id: ${exchangeProperty.candidateId}")
    .end();
    
    from(CEILConstants.DIRECT_PRODUCT_LOOKUP_TABLE)
      .routeId(getClass().getName())
      .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_START + " For Candidate Id: ${exchangeProperty.candidateId}")
      .to(CEILConstants.SQL_QUERY_PRODUCT_VALIDATION)
      .process(fetchMarkingMode) 
      .choice()
        .when(exchangeProperty(CEILConstants.CEIL_MARKING_MODE).contains(CEILConstants.HYBRID_MARKING_MODE))
             .setHeader(CEILConstants.FOR_ELIT, constant("yes"))
             .setHeader(CEILConstants.FOR_RMA, constant("no"))
        .when(exchangeProperty(CEILConstants.CEIL_MARKING_MODE).contains(CEILConstants.EXAMINER_MARKING_MODE))
            .setHeader(CEILConstants.FOR_ELIT, constant("no"))
            .setHeader(CEILConstants.FOR_RMA, constant("yes"))
        .when(PredicateBuilder.or(exchangeProperty(CEILConstants.CEIL_MARKING_MODE).contains(CEILConstants.TRAINING_MARKING_MODE)))
            .setHeader(CEILConstants.FOR_ELIT, constant("yes"))
            .setHeader(CEILConstants.FOR_RMA, constant("yes"))
        .when(exchangeProperty(CEILConstants.CEIL_MARKING_MODE).contains(CEILConstants.PILOT_MARKING_MODE))
            .setHeader(CEILConstants.FOR_ELIT, constant("yes"))
            .setHeader(CEILConstants.FOR_RMA, constant("yes"))
            .setProperty("ifPilot",constant(true))
        .otherwise()
           .log(LoggingLevel.INFO, LOGGER, "Wrong product name")
           .end()
    .log(LoggingLevel.INFO,LOGGER,CEILConstants.LOG_ROUTE_MESSAGE_COMPLETE+ " For Candidate Id: ${exchangeProperty.candidateId}")
    .end();
  }

}
