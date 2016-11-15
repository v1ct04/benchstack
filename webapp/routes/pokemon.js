const express = require('express'),
      randgen = require('randgen'),
        async = require('async'),
         util = require('../util')

const router = express.Router()

function tryCapture(db, user, pokemon, done) {
  var captured = false
  let [normalTry, greatTry] = [10, 2]
  while (normalTry-- > 0) {
    let success = randgen.rbernoulli(1 - pokemon.level / 120)
    if (user.bag.pokeball > 0 && !captured) {
      user.bag.pokeball--
      captured = success
    }
  }
  while (greatTry-- > 0) {
    let success = randgen.rbernoulli(1 - pokemon.level / 220)
    if (user.bag.greatball > 0 && !captured) {
      user.bag.greatball--
      captured = success
    }
  }
  if (!captured) {
    db.get('user').update({_id: user._id}, {$set: {bag: user.bag}},
      (err) => {done(err, captured)})
  } else {
    async.parallel([
        callback => db.get('user').update({_id: user._id},
          {$set: {bag: user.bag}, $addToSet: {pokemonIds: pokemon._id}},
          done),
        callback => db.get('pokemon').update({_id: pokemon._id},
          {$set: {ownerId: user._id, loc: user.loc}}, done)
      ],
      (err) => {done(err, captured)})
  }
}

router.param('autoPokemonId', util.autoParamMiddleware('pokemon'))

router.get('/:autoPokemonId', function(req, res, next) {
  res.data = {pokemon: req.pokemon}
  next()
})

router.post('/:autoPokemonId/capture', function(req, res, next) {
  if (req.pokemon.ownerId || req.pokemon.stadiumId) {
    return next(new Error("Can't capture Pokemon already owned"))
  }
  req.db.get('user').findOne({_id: req.body.userId}).then(
      function (user) {
        if (!user) {
          res.status(404)
          return next(new Error("User not found"))
        }
        tryCapture(req.db, user, req.pokemon, function (err, captured) {
          res.data = {captured: captured ? 1 : 0, bag: user.bag}
          next(err)
        })
      }, next)
})

function samplePokemons(db, count, done) {
  db.get('pokemon').aggregate({$sample: {size: count}}, done)
}

router.post('/genocide', function(req, res, next) {
  samplePokemons(req.db, parseInt(req.body.count) || 10,
    function (err, pokemons) {
      if (err) return next(err)
      pokemons = pokemons.filter(p => p.ownerId == null && p.stadiumId == null)

      req.db.get('pokemon').remove({_id: {$in: pokemons.map(p => p._id)}},
        function (err) {
          res.data = {removed: pokemons}
          next(err)
        })
    })
})

module.exports = router
