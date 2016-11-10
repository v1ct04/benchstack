const express = require('express'),
         util = require('../util')

const router = express.Router()

router.param('autoStadiumId', util.autoParamMiddleware('stadium'))

router.get('/:autoStadiumId', function(req, res, next) {
  res.data = {stadium: req.stadium}
  next()
})

router.get('/:autoStadiumId/pokemons', function(req, res, next) {
  req.db.get('pokemon').find({_id: { $in: req.stadium.defendingPokemonIds }}, '-_id')
      .then(function(pokemons) {
        res.data = {pokemons: pokemons}
        next()
      }, next)
})

router.post('/:autoStadiumId/collect', function(req, res, next) {
  var updateDoc = {}
  let items = req.stadium.items;
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
