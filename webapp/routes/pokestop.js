const express = require('express'),
         util = require('../util'),
   genPokemon = require('../seed/gen-pokemon').genPokemon,
      genUtil = require('../seed/util')

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

router.post('/:autoPokestopId/lure', function(req, res, next) {
  let query = {_id: req.body.userId}
  req.db.get('user').findOneAndUpdate(query, {$inc: {"bag.lure": -1}}, 'bag',
      function(err, user) {
        if (err) return next(err)
        if (!user) return next(new Error("User not found"))
        if (user.bag.lure <= 0) {
          req.db.get('user').update(query, {$max: {"bag.lure": 0}},
              err => next(new Error("No lures available")))
          return;
        }
        let pokeGen = () => genPokemon({loc: genUtil.rloc(req.pokestop.loc, 100000)})
        let pokemons = genUtil.genArray(20, pokeGen)
        req.db.get('pokemon').insert(pokemons,
            function(err) {
              res.data = {pokemons: pokemons, bag: user.bag}
              next(err)
            })
      })
})

module.exports = router
