const express = require('express'),
      randgen = require('randgen'),
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
  Object.keys(items).forEach(k => updateDoc["bag." + k] = items[k])

  updateDoc = {$inc: updateDoc}
  req.db.get('user').findOneAndUpdate({_id: req.body.userId}, updateDoc, 'bag')
    .then(function (user) {
        if (!user) {
          res.status(404)
          return next(new Error("User not found"))
        }
        res.data = {bag: user.bag}
        next()
    }, next)
})

router.post('/:autoPokestopId/lure', function(req, res, next) {
  let query = {_id: req.body.userId}
  req.db.get('user').findOneAndUpdate(query, {$inc: {"bag.lure": -1}}, 'bag',
      function(err, user) {
        if (err) return next(err)
        if (!user) return next(new Error("User not found"))
        if (user.bag.lure <= 0) {
          req.db.get('user').update(query, {$max: {"bag.lure": 0}})
          user.bag.lure = 0
          // do not fail as we need a predictable performance for the request
        }
        let pokeGen = () => genPokemon({loc: genUtil.rloc(req.pokestop.loc, 100000)})
        let pokemonCount = req.body.count || 20
        let pokemons = genUtil.genArray(pokemonCount, pokeGen)
        req.db.get('pokemon').insert(pokemons,
            function(err) {
              res.data = {pokemons: pokemons, bag: user.bag}
              next(err)
            })
      })
})

router.post('/improve', function (req, res, next) {
  let count = parseInt(req.body.count) || 10
  req.db.get('pokestop').aggregate({$sample: {size: count}},
    function (err, pokestops) {
      if (err) return next(err)

      let updateDoc = {$inc: {}}
      let itemToAdd = randgen.rlist(['pokeball', 'greatball', 'revive', 'lure'])
      updateDoc.$inc["items." + itemToAdd] = 1

      req.db.get('pokestop').update({_id: {$in: pokestops.map(p => p._id)}},
          updateDoc, {multi: true},
          function (err) {
            res.data = {improved: pokestops, item: itemToAdd}
            next(err)
          })
    })
})

module.exports = router
