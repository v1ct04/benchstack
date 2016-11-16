const express = require('express'),
         util = require('../util')
const router = express.Router()

function nearbyElementsMiddleware(tableName, maxDist) {
  return function(req, res, next) {
    req.db.get(tableName).find({loc: util.nearDoc(req.user.loc, maxDist)})
        .then(function(elements) {
          res.data = {}
          res.data[tableName] = elements
          next()
        }, next)
    }
}

function closestElementMiddleware(tableName, extraQueryArgs = () => ({})) {
  return function(req, res, next) {
    let query = extraQueryArgs(req.user)
    query.loc = util.nearDoc(req.user.loc)
    req.db.get(tableName).find(query, {limit: parseInt(req.query.count) || 1})
        .then(function(element) {
          res.data = {}
          res.data[tableName] = element
          next()
        }, next)
    }
}

router.param('autoUserId', util.autoParamMiddleware('user'))

router.get('/:autoUserId/pokemon', nearbyElementsMiddleware('pokemon', 50000))
router.get('/:autoUserId/pokestop', nearbyElementsMiddleware('pokestop', 50000))
router.get('/:autoUserId/stadium', nearbyElementsMiddleware('stadium', 50000))
router.get('/:autoUserId/trainer', nearbyElementsMiddleware('trainer', 50000))

router.get('/:autoUserId/pokestop/closest', closestElementMiddleware('pokestop'))
router.get('/:autoUserId/pokemon/closest', closestElementMiddleware('pokemon', () => ({ownerId: null, stadiumId: null})))
router.get('/:autoUserId/trainer/closest', closestElementMiddleware('trainer', user => ({team: {$ne: user.team}})))

router.get('/:autoUserId/stadium/closest', closestElementMiddleware('stadium', user => ({ownerId: {$ne: user._id}})))

module.exports = router
