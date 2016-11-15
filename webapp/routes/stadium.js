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

module.exports = router
