const express = require('express')
const router = express.Router()

function nearDoc(loc, maxDist = null) {
  let geoJson = {type: "Point", coordinates: [loc.lng, loc.lat]}
  let nearDoc = {$nearSphere: {$geometry: geoJson}}
  if (maxDist) nearDoc.$nearSphere.$maxDistance = maxDist
  return nearDoc
}

function nearbyElementsMiddleware(tableName, maxDist) {
  return function(req, res, next) {
    req.db.get(tableName).find({loc: nearDoc(req.user.loc, maxDist)})
        .then(function(elements) {
          res.data = {}
          res.data[tableName] = elements
          next()
        }, next)
    }
}

function closestElementMiddleware(tableName) {
  return function(req, res, next) {
    req.db.get(tableName).findOne({loc: nearDoc(req.user.loc)})
        .then(function(element) {
          res.data = {}
          res.data[tableName] = element
          next()
        }, next)
    }
}

router.param('autoUserId', function(req, res, next, userId) {
  req.db.get('user').findOne({_id: userId})
      .then(function(user) {
        if (!user) {
          res.status(404)
          return next(new Error("User not found"))
        }
        req.user = user
        next()
      }, next)
})

router.get('/:autoUserId/pokemon', nearbyElementsMiddleware('pokemon', 50000))
router.get('/:autoUserId/pokemon/closest', closestElementMiddleware('pokemon'))
router.get('/:autoUserId/pokestop', nearbyElementsMiddleware('pokestop', 50000))
router.get('/:autoUserId/pokestop/closest', closestElementMiddleware('pokestop'))
router.get('/:autoUserId/stadium', nearbyElementsMiddleware('stadium', 50000))
router.get('/:autoUserId/stadium/closest', closestElementMiddleware('stadium'))
router.get('/:autoUserId/trainer', nearbyElementsMiddleware('trainer', 50000))
router.get('/:autoUserId/trainer/closest', closestElementMiddleware('trainer'))


module.exports = router
