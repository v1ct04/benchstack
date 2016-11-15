const express = require('express'),
         util = require('../util')

const router = express.Router()

router.param('autoTrainerId', util.autoParamMiddleware('trainer'))

router.get('/:autoTrainerId', function(req, res, next) {
  res.data = {trainer: req.trainer}
  next()
})

router.get('/:autoTrainerId/pokemons', function(req, res, next) {
  req.db.get('pokemon').find({_id: { $in: req.trainer.pokemonIds }}, '-_id')
      .then(function(pokemons) {
        res.data = {pokemons: pokemons}
        next()
      }, next)
})

module.exports = router
