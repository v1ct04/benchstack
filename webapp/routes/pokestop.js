const express = require('express'),
         util = require('../util')

const router = express.Router()

router.param('autoPokestopId', util.autoParamMiddleware('pokestop'))

router.get('/:autoPokestopId', function(req, res, next) {
  res.data = {pokestop: req.pokestop}
  next()
})

router.post('/:autoPokestopId/collect', function(req, res, next) {
  var updateDoc = {}
  let items = req.pokestop.items;
  ['pokeball', 'greatball', 'revive', 'lure']
      .filter(k => k in items && items[k] > 0)
      .forEach(k => updateDoc["bag." + k] = items[k])

  updateDoc = {$inc: updateDoc}
  req.db.get('user').findOneAndUpdate({_id: req.body.userId}, updateDoc, 'bag',
      function(err, user) {
        if (!user) {
          res.status(404)
          return next(new Error("User not found"))
        }
        res.data = {bag: user.bag}
        next(err)
      })
})

module.exports = router
