const express = require('express'),
        async = require('async'),
      randgen = require('randgen'),
        debug = require('debug')('pokestack:battle')
const util = require('../seed/util'),
      genPokemon = require('../seed/gen-pokemon').genPokemon

const router = express.Router()

function findPokemonsTask(db, tableName, id, {fieldName='pokemonIds', baseQuery = {}} = {}) {
  return function (done) {
    async.waterfall([
        function(next) {
          db.get(tableName).findOne({_id: id}, next)
        },
        function(doc, next) {
          if (!doc) return next(new Error(tableName + " not found"))
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

function fillPokemons(pokemons, count) {
  if (count < pokemons.length) return pokemons.slice() // return a copy
  return pokemons.concat(util.genArray(count - pokemons.length, genPokemon))
}

// Returns whether the offensive pokemons won the battle
function battlePokemons(offensivePokemons, defensivePokemons) {
  offensivePokemons = fillPokemons(offensivePokemons, 3)
  defensivePokemons = fillPokemons(defensivePokemons, 3)

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

function setTeamTask(db, type, id, newTeam) {
  return function(done) {
    db.get(type).update({_id: id}, {$set: {team: newTeam}}, done)
  }
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
      let pokemonsWon = trainerPoke.map(p => p._id),
          pointsWon = trainerPoke.map(p => p.level).reduce((a,b) => a + b)
      async.parallel([
          setTeamTask(req.db, 'trainer', trainerId, results.user.doc.team),
          done => req.db.get('user').update({_id: userId},
                          {$inc: {points: pointsWon}}, done)
      ], function (err) {
        res.data = {victory: 1, pointsWon: pointsWon}
        next(err)
      })
    } else {
      res.data = {victory: 0}
      return next()
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
          loserIds = stadiumPoke.map(p => p._id),
          pointsWon = stadium.points + 3 * userPoke.map(p => p.level).reduce((a,b) => a + b)
      let tasks = []
      if (stadium.ownerId) {
        let pointsLost = stadium.points + 3 * stadiumPoke.map(p => p.level).reduce((a,b) => a + b)
        tasks.push(done => req.db.get('user').update({_id: stadium.ownerId},
                                {$pull: {stadiumIds: stadiumId}, $inc: {points: -pointsLost}}, done))
      }
      async.parallel(tasks.concat([
          done => req.db.get('stadium').update({_id: stadiumId},
                          {$set: {ownerId: userId, defendingPokemonIds: winnerIds}}, done),
          done => req.db.get('pokemon').update({_id: {$in: winnerIds}},
                          {$set: {stadiumId: stadiumId}}, done),
          done => req.db.get('pokemon').update({_id: {$in: loserIds}},
                          {$set: {stadiumId: null}}, done),
          done => req.db.get('user').update({_id: userId},
                          {$addToSet: {stadiumIds: stadiumId},
                           $inc: {points: pointsWon}}, done)
      ]), function (err) {
        res.data = {victory: 1, pointsWon: pointsWon}
        next(err)
      })
    } else {
      if (!stadium.ownerId) {
        res.data = {victory: 0}
        return next()
      }
      // Stadium owner will get the points in this case
      let pointsGiven = userPoke.map(p => p.level).reduce((a, b) => a + b)
      req.db.get('user').update({_id: stadium.ownerId}, {$inc: {points: pointsGiven}},
        function (err) {
          res.data = {victory: 0, pointsGiven: pointsGiven}
          next(err)
        })
    }
  })
})


router.post('/pokemon/:pokemonId', function(req, res, next) {
  [userId, pokemonId] = [req.body.userId, req.params.pokemonId]
  async.parallel({
    user: findPokemonsTask(req.db, 'user', userId, {baseQuery: {stadiumId: null}}),
    pokemon: done => req.db.get('pokemon').findOne({_id: pokemonId}, done)
  }, function (err, results) {
    if (err) return next(err)
    // battle with only the 3 strongest pokemons
    let userPoke = results.user.pokemons.slice(0, 4),
        pokemon = results.pokemon
    if (!pokemon) {
      return next(new Error("Pokemon not found"))
    } else if (pokemon.ownerId || pokemon.stadiumId) {
      return next(new Error("Pokemon must be free to be battled directly"))
    }

    let userWon = battlePokemons(userPoke, [pokemon])
    if (userWon) {
      let pointsWon = 5 * pokemon.level
      async.parallel([
          done => req.db.get('user').update({_id: userId},
                          {$inc: {points: pointsWon}}, done),
          done => req.db.get('pokemon').remove({_id: pokemonId}, done)
      ], function (err) {
        res.data = {victory: 1, pointsWon: pointsWon}
        next(err)
      })
    } else {
      res.data = {victory: 0}
      next()
    }
  })
})

module.exports = router
