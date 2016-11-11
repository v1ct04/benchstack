const express = require('express'),
        async = require('async'),
      randgen = require('randgen'),
        debug = require('debug')('pokestack:battle')
const util = require('../seed/util')

const router = express.Router()

function findPokemonsTask(db, tableName, id, {fieldName='pokemonIds', baseQuery = {}} = {}) {
  return function (done) {
    async.waterfall([
        function(next) {
          db.get(tableName).findOne({_id: id}, next)
        },
        function(doc, next) {
          query = Object.create(baseQuery)
          query._id = {$in: doc[fieldName]}
          db.get('pokemon').find(query, {sort: { level: -1 }}).then(
              pokemons => next(null, {doc: doc, pokemons: pokemons}),
              next)
        }
      ],
      done)
  }
}

function attackPokemon(attacker, defender) {
  let specialProb = 0.51 - 8 / (15 + attacker.level)
  let isSpecial = randgen.rbernoulli(specialProb)

  let dodge = Math.min(defender.stats.Spd / 255, 1) * 0.7
  if (isSpecial) dodge /= 2
  if (randgen.rbernoulli(dodge)) return;

  var baseDamage, damageMultiplier
  if (!isSpecial) {
    let baseAttack = attacker.stats.Atk / 10
    baseDamage = baseAttack * Math.min(randgen.rchisq(10) / 10, 2)
    damageMultiplier = 255 / (255 + defender.stats.Def)
  } else {
    let spAttack = attacker.stats.SpAtk / 8
    baseDamage = spAttack * Math.min(randgen.rchisq(50) / 50, 3)
    damageMultiplier = 510 / (510 + 2 * defender.stats.SpDef + defender.stats.Def)
  }
  let attackDamage = baseDamage * damageMultiplier
  defender.stats.HP = Math.max(defender.stats.HP - attackDamage, 0)
  debug(`${attacker.name} has attacked ${defender.name} dealing ${attackDamage} damage. Resulting HP: ${defender.stats.HP}`)
}

function battlePokemons(offensivePokemons, defensivePokemons) {
  // not a good practice to modify fn args, so take a copy of the array
  offensivePokemons = offensivePokemons.slice()
  defensivePokemons = defensivePokemons.slice()

  var off = offensivePokemons.shift()
  var def = defensivePokemons.shift()
  let offTurn = true
  while (off && def) {
    if (offTurn) {
      attackPokemon(off, def)
      if (def.stats.HP <= 0) {
        def = defensivePokemons.shift()
      }
    } else {
      attackPokemon(def, off)
      if (off.stats.HP <= 0) {
        off = offensivePokemons.shift()
      }
    }
    offTurn = !offTurn
  }
  return !!off
}

function losePokemons(db, userId, pokemons, next) {
  let lostIds = pokemons.map(p => p._id)
  async.parallel([
      done => db.get('user').update({_id: userId},
                  {$pullAll: {pokemonIds: lostIds}}, done),
      done => db.get('pokemon').update({_id: {$in: lostIds}},
                  {$set: {ownerId: null, loc: util.rloc()}}, done)
  ], next)
}

router.post('/trainer/:trainerId', function(req, res, next) {
  [userId, trainerId] = [req.body.userId, req.params.trainerId]
  async.parallel({
    user: findPokemonsTask(req.db, 'user', userId, {baseQuery: {stadiumId: null}}),
    trainer: findPokemonsTask(req.db, 'trainer', trainerId)
  }, function (err, results) {
    if (err) return next(err)
    // battle only the 3 strongest pokemons between trainers
    let userPoke = results.user.pokemons.slice(0, 4),
        trainerPoke = results.trainer.pokemons.slice(0, 4)
    let userWon = battlePokemons(userPoke, trainerPoke)
    if (userWon) {
      let pokemonsWon = trainerPoke.map(p => p._id)
      async.parallel([
          done => req.db.get('trainer').update({_id: trainerId},
                          {$pullAll: {pokemonIds: pokemonsWon}}, done),
          done => req.db.get('user').update({_id: userId},
                          {$addToSet: {pokemonIds: {$each: pokemonsWon}}}, done),
          done => req.db.get('pokemon').update({_id: {$in: pokemonsWon}},
                          {$set: {ownerId: req.body.userId}}, done)
      ], function (err) {
        res.data = {victory: 1, pokemonsWon: trainerPoke}
        next(err)
      })
    } else {
      losePokemons(req.db, userId, userPoke, function (err) {
        res.data = {victory: 0, pokemonsLost: userPoke}
        next(err)
      })
    }
  })
})


router.post('/stadium/:stadiumId', function(req, res, next) {
  [userId, stadiumId] = [req.body.userId, req.params.stadiumId]
  async.parallel({
    user: findPokemonsTask(req.db, 'user', userId, {baseQuery: {stadiumId: null}}),
    stadium: findPokemonsTask(req.db, 'stadium', stadiumId, {fieldName: 'defendingPokemonIds'})
  }, function (err, results) {
    if (err) return next(err)
    // battle with only the 3 strongest pokemons
    let userPoke = results.user.pokemons.slice(0, 4),
        {doc: stadium, pokemons: stadiumPoke} = results.stadium
    let userWon = battlePokemons(userPoke, stadiumPoke)
    if (userWon) {
      let winnerIds = userPoke.map(p => p._id),
          loserIds = stadiumPoke.map(p => p._id)
      let tasks = []
      if (stadium.ownerId) {
        tasks.push(done => req.db.get('user').update({_id: stadium.ownerId},
                                {$pullAll: {stadiumIds: [stadiumId]}}, done))
      }
      async.parallel(tasks.concat([
          done => req.db.get('stadium').update({_id: stadiumId},
                          {$set: {ownerId: userId, defendingPokemonIds: winnerIds}}, done),
          done => req.db.get('user').update({_id: userId},
                          {$addToSet: {stadiumIds: stadiumId, pokemonIds: {$each: loserIds}},
                           $inc: {points: stadium.points}}, done),
          done => req.db.get('pokemon').update({_id: {$in: winnerIds}},
                          {$set: {stadiumId: stadiumId}}, done),
          done => req.db.get('pokemon').update({_id: {$in: loserIds}},
                          {$set: {stadiumId: null, ownerId: userId}}, done),
      ]), function (err) {
        res.data = {victory: 1, pokemonsWon: stadiumPoke}
        next(err)
      })
    } else {
      losePokemons(req.db, userId, userPoke, function (err) {
        res.data = {victory: 0, pokemonsLost: userPoke}
        next(err)
      })
    }
  })
})

module.exports = router
