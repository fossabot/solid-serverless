const logger = require('@yodata/solid-serverless-logger').defaultLogger

/**
 * what does your function do?
 * @param {object} event
 * @param {string} event.param - comment
 * 
 * Context doc: https://docs.aws.amazon.com/lambda/latest/dg/nodejs-prog-model-context.html 
 * @param {Object}   context
 * @param {string}   context.logGroupName - Cloudwatch Log Group name
 * @param {string}   context.logStreamName - Cloudwatch Log stream name.
 * @param {string}   context.functionName - Lambda function name.
 * @param {string}   context.memoryLimitInMB - Function memory.
 * @param {string}   context.functionVersion - Function version identifier.
 * @param {function} context.getRemainingTimeInMillis - Time in milliseconds before function times out.
 * @param {string}   context.awsRequestId - Lambda request ID.
 * @param {string}   context.invokedFunctionArn - Function ARN.
 *
 * @returns {Object} response
 * @returns {string} response.param - comment
 */
exports.handler = async (event, context) => {
    try {
        logger.debug('validate-schema received event', {event,context})
    } catch (error) {
        logger.error('validate-schema failed', {error, context})
    }
    logger.debug('validate-schema response', event)
    return event
}
