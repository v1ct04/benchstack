const express = require('express'),
      randgen = require('randgen'),
        async = require('async'),
   genPokemon = require('../seed/gen-pokemon').genPokemon,
         util = require('../util'),
      genUtil = require('../seed/util')

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
  if (!captured || !pokemon._id) {
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

router.param('autoPokemonId', util.autoParamMiddleware('pokemon', genPokemon))

router.get('/:autoPokemonId', function(req, res, next) {
  res.data = {pokemon: req.pokemon}
  next()
})

router.post('/:autoPokemonId/capture', function(req, res, next) {
  if (req.pokemon.ownerId || req.pokemon.stadiumId) {
      // Instead of failing the request, simulate capturing a fake pokemon
    req.pokemon = genPokemon()
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

function samplePokemons(db, count, loc, query, done) {
  query.loc = util.nearDoc(loc)
  db.get('pokemon').find(query, {limit: count}, done)
}

router.post('/nuke', function(req, res, next) {
  let count = parseInt(req.body.count) || 10
  let loc = req.body.loc || genUtil.rloc()

  samplePokemons(req.db, count, loc, {ownerId: null, stadiumId: null},
    function (err, pokemons) {
      if (err) return next(err)

      req.db.get('pokemon').remove({_id: {$in: pokemons.map(p => p._id)}},
        function (err) {
          res.data = {removed: pokemons}
          next(err)
        })
    })
})

router.post('/levelUp', function(req, res, next) {
  let count = parseInt(req.body.count) || 10
  let loc = req.body.loc || genUtil.rloc()

  samplePokemons(req.db, count, loc, {level: {$lt: 100}},
    function (err, pokemons) {
      if (err) return next(err)

      req.db.get('pokemon').update({_id: {$in: pokemons.map(p => p._id)}},
          {$inc: {level: 1}}, {multi: true},
          function (err) {
            res.data = {leveledUp: pokemons}
            next(err)
          })
    })
})

router.get('/stats/count', function (req, res, next) {
  req.db.get('pokemon').count({}, function(err, count) {
    res.data = {count: count}
    next(err)
  })
})

module.exports = router
