const express = require('express'),
         util = require('../util')
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

function closestElementMiddleware(tableName, extraQueryArgs = () => ({})) {
  return function(req, res, next) {
    let query = extraQueryArgs(req.user)
    query.loc = nearDoc(req.user.loc)
    req.db.get(tableName).findOne(query)
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

router.get('/:autoUserId/stadium/closest', function(req, res, next) {
  var closed = false
  req.db.get('stadium').find({loc: nearDoc(req.user.loc)})
      .each(function(stadium, stream) {
        let closeNext = function(err) {
          closed = true
          stream.close()
          next(err)
        }
        if (!stadium.ownerId) {
          res.data = {stadium: stadium}
          return closeNext()
        }
        stream.pause()
        req.db.get('user').findOne({_id: stadium.ownerId}, 'team')
            .then(function (user) {
              if (user.team === req.user.team) {
                return stream.resume()
              }
              res.data = {stadium: stadium}
              closeNext()
            }, closeNext)
      }).then(() => {
        if (!closed) next(new Error("Not found"))
      }, next)
})

module.exports = router
