const logger = require('./logger')
const client = require('./solid-client')

const policyDefaults = {
	'@context': 'https://dev.yodata.io/public/real_estate/context.jsonld',
	type: 'DataPolicy',
	processor: 'Yodata',
	effect: 'Transform'
}

/**
 * Saves policy to pod:public/data-policy/{name}
 * @async
 * @param {object} event
 * @param {string} event.name - http uri of the policy
 * @param {object} event.value - the policy value
 * @returns {Promise<string>} the policy iri
 */
module.exports = async event => {
	const DOMAIN = String(process.env.DOMAIN || process.env.SOLID_HOST)
	const policyName = event.name
	const policy = Object.assign(policyDefaults, { value: event.value })
	const body = JSON.stringify(policy)
	const headers = { 'Content-Type': 'application/ld+json' }
	const iri = `https://${DOMAIN}/public/data-policy/${policyName}`
	const result = await client.put(iri, { headers, body }).then(() => {
		return iri
	}).catch(error => {
		logger.error(error.message, error)
		return null
	})
	return result
}
